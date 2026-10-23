import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSlideToggleChange, MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { EMPTY, Observable, Subject, catchError, debounceTime, filter, switchMap } from 'rxjs';
import { mensajeDeError } from '../../../core/http/error.util';
import { NotificacionesStore } from '../../../core/notificaciones/notificaciones.store';
import {
  formatearFechaLarga,
  formatearPrecio,
  formatearRangoHoras,
} from '../../../core/util/fecha';
import {
  ConfirmDialog,
  ConfirmDialogData,
} from '../../../shared/components/confirm-dialog/confirm-dialog';
import { TipoNotificacion } from '../../../shared/models/notificacion.model';
import { EstadoReserva, Reserva } from '../../../shared/models/reserva.model';
import { PartidoService } from '../../partidos/partido.service';
import { ReservaService } from '../reserva.service';

const JUGADORES_POR_PARTIDO = 4;

/** Notificaciones que cambian lo que esta página muestra (jugadores apuntados, reservas canceladas). */
const TIPOS_QUE_AFECTAN: ReadonlySet<TipoNotificacion> = new Set<TipoNotificacion>([
  'JUGADOR_SE_APUNTO',
  'JUGADOR_SALIO',
  'PARTIDO_COMPLETO',
  'RESERVA_CANCELADA',
]);

/** Ventana para agrupar varios push seguidos en un único refresco. */
const RETARDO_REFRESCO_MS = 300;

function sinElemento(ids: ReadonlySet<number>, id: number): ReadonlySet<number> {
  const copia = new Set(ids);
  copia.delete(id);
  return copia;
}

const ETIQUETA_ESTADO: Record<EstadoReserva, string> = {
  CONFIRMADA: 'Confirmada',
  CANCELADA: 'Cancelada',
};

@Component({
  selector: 'app-mis-reservas',
  imports: [MatButtonModule, MatProgressSpinnerModule, MatSlideToggleModule],
  templateUrl: './mis-reservas.html',
  styleUrl: './mis-reservas.scss',
})
export class MisReservas {
  private readonly reservaService = inject(ReservaService);
  private readonly partidoService = inject(PartidoService);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly notificaciones = inject(NotificacionesStore);

  protected readonly reservas = signal<Reserva[]>([]);
  protected readonly cargando = signal(true);
  protected readonly cancelando = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Reservas con un cambio de "partido abierto" en vuelo (su interruptor se bloquea). */
  protected readonly cambiando = signal<ReadonlySet<number>>(new Set());

  protected readonly jugadoresPorPartido = JUGADORES_POR_PARTIDO;
  protected readonly formatearFecha = formatearFechaLarga;
  protected readonly formatearRango = formatearRangoHoras;
  protected readonly formatearPrecio = formatearPrecio;

  /** Emite `true` si la carga debe mostrar el spinner o `false` si es un refresco silencioso. */
  private readonly cargar$ = new Subject<boolean>();

  constructor() {
    this.cargar$
      .pipe(
        switchMap((conSpinner) => this.consultar(conSpinner)),
        takeUntilDestroyed(),
      )
      .subscribe((reservas) => {
        this.reservas.set(reservas);
        this.cargando.set(false);
      });

    // Si alguien se apunta o sale, se completa un partido o se cancela una reserva, lo mostrado deja de ser
    // cierto: refresco silencioso agrupando ráfagas en una sola petición.
    this.notificaciones.recibidas$
      .pipe(
        filter((n) => TIPOS_QUE_AFECTAN.has(n.tipo)),
        debounceTime(RETARDO_REFRESCO_MS),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.cargar$.next(false));

    this.cargar$.next(true);
  }

  protected etiquetaEstado(reserva: Reserva): string {
    return ETIQUETA_ESTADO[reserva.estado];
  }

  /** Solo se puede cancelar una reserva confirmada que todavía no ha empezado. */
  protected puedeCancelar(reserva: Reserva): boolean {
    return reserva.estado === 'CONFIRMADA' && new Date(reserva.inicio).getTime() > Date.now();
  }

  /** Abrir o cerrar el partido solo tiene sentido en una reserva confirmada que aún no ha empezado. */
  protected puedeAbrir(reserva: Reserva): boolean {
    return this.puedeCancelar(reserva);
  }

  /**
   * Optimista: el interruptor cambia al instante y, si el backend lo rechaza (p. ej. cerrar con otros jugadores
   * ya apuntados), se vuelve al valor anterior y se muestra el motivo.
   */
  protected alternarAbierta(reserva: Reserva, evento: MatSlideToggleChange): void {
    if (this.cambiando().has(reserva.id)) {
      return;
    }
    const abierta = evento.checked;
    const anterior = reserva.abierta;
    this.reemplazar(reserva.id, (r) => ({ ...r, abierta }));
    this.cambiando.update((ids) => new Set(ids).add(reserva.id));

    this.partidoService.setAbierta(reserva.id, abierta).subscribe({
      next: (actualizada) => {
        this.cambiando.update((ids) => sinElemento(ids, reserva.id));
        this.reemplazar(reserva.id, () => actualizada);
      },
      error: (err: unknown) => {
        this.cambiando.update((ids) => sinElemento(ids, reserva.id));
        this.reemplazar(reserva.id, (r) => ({ ...r, abierta: anterior }));
        // el interruptor ya cambió por dentro: si el estado vuelve al valor previo antes de repintar,
        // el binding [checked] no se enteraría, así que se restaura explícitamente
        evento.source.checked = anterior;
        this.snack.open(
          mensajeDeError(err, 'No se pudo cambiar el estado del partido. Inténtalo de nuevo.'),
          'Cerrar',
          { duration: 6000 },
        );
      },
    });
  }

  private reemplazar(id: number, cambio: (reserva: Reserva) => Reserva): void {
    this.reservas.update((lista) => lista.map((r) => (r.id === id ? cambio(r) : r)));
  }

  protected cancelar(reserva: Reserva): void {
    if (this.cancelando()) {
      return;
    }
    const datos: ConfirmDialogData = {
      titulo: 'Cancelar reserva',
      mensaje: '¿Seguro que quieres cancelar esta reserva?',
      lineas: [
        { etiqueta: 'Club', valor: reserva.clubNombre },
        { etiqueta: 'Pista', valor: reserva.pistaNombre },
        { etiqueta: 'Fecha', valor: formatearFechaLarga(reserva.inicio) },
        { etiqueta: 'Hora', valor: formatearRangoHoras(reserva.inicio, reserva.fin) },
      ],
      textoConfirmar: 'Cancelar reserva',
      textoCancelar: 'Mantener',
    };

    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, { data: datos })
      .afterClosed()
      .subscribe((confirmado) => {
        if (confirmado) {
          this.ejecutarCancelacion(reserva);
        }
      });
  }

  private ejecutarCancelacion(reserva: Reserva): void {
    this.cancelando.set(true);
    this.reservaService.cancelar(reserva.id).subscribe({
      next: () => {
        this.cancelando.set(false);
        this.snack.open('Reserva cancelada.', 'Cerrar', { duration: 5000 });
        this.cargar$.next(true);
      },
      error: (err: unknown) => {
        this.cancelando.set(false);
        this.snack.open(
          mensajeDeError(err, 'No se pudo cancelar la reserva. Inténtalo de nuevo.'),
          'Cerrar',
          { duration: 6000 },
        );
      },
    });
  }

  private consultar(conSpinner: boolean): Observable<Reserva[]> {
    if (conSpinner) {
      this.cargando.set(true);
    }
    this.error.set(null);
    return this.reservaService.mias().pipe(
      catchError((err: unknown) => {
        this.error.set(mensajeDeError(err, 'No se pudieron cargar tus reservas.'));
        this.cargando.set(false);
        return EMPTY;
      }),
    );
  }
}
