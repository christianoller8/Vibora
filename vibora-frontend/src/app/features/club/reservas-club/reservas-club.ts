import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { EMPTY, Observable, Subject, catchError, debounceTime, filter, switchMap } from 'rxjs';
import { mensajeDeError } from '../../../core/http/error.util';
import { NotificacionesStore } from '../../../core/notificaciones/notificaciones.store';
import {
  formatearFechaLarga,
  formatearPrecio,
  formatearRangoHoras,
  hoyEnMadrid,
} from '../../../core/util/fecha';
import {
  ConfirmDialog,
  ConfirmDialogData,
} from '../../../shared/components/confirm-dialog/confirm-dialog';
import { TipoNotificacion } from '../../../shared/models/notificacion.model';
import { EstadoReserva, ReservaClub } from '../../../shared/models/reserva.model';
import { ReservaService } from '../../reservas/reserva.service';

/** Notificaciones que pueden cambiar las reservas del club (alguien se apunta o sale, se cancela una reserva). */
const TIPOS_QUE_AFECTAN: ReadonlySet<TipoNotificacion> = new Set<TipoNotificacion>([
  'JUGADOR_SE_APUNTO',
  'JUGADOR_SALIO',
  'RESERVA_CANCELADA',
]);

/** Ventana para agrupar varios push seguidos en un único refresco. */
const RETARDO_REFRESCO_MS = 300;

const ETIQUETA_ESTADO: Record<EstadoReserva, string> = {
  CONFIRMADA: 'Confirmada',
  CANCELADA: 'Cancelada',
};

@Component({
  selector: 'app-reservas-club',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatChipsModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatTableModule,
  ],
  templateUrl: './reservas-club.html',
  styleUrl: './reservas-club.scss',
})
export class ReservasClub implements OnInit {
  private readonly reservaService = inject(ReservaService);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly notificaciones = inject(NotificacionesStore);

  readonly clubId = input.required<number>();

  /** Día mostrado (YYYY-MM-DD, Madrid); hoy por defecto. */
  readonly fechaCtrl = new FormControl(hoyEnMadrid(), { nonNullable: true });

  protected readonly reservas = signal<ReservaClub[]>([]);
  protected readonly ordenadas = computed(() =>
    [...this.reservas()].sort(
      (a, b) =>
        new Date(a.inicio).getTime() - new Date(b.inicio).getTime() ||
        a.pistaNombre.localeCompare(b.pistaNombre),
    ),
  );
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly cancelando = signal(false);

  protected readonly columnas = ['hora', 'pista', 'jugador', 'estado', 'precio', 'acciones'];
  protected readonly formatearRango = formatearRangoHoras;
  protected readonly formatearPrecio = formatearPrecio;

  /** Emite `true` si la carga debe mostrar el spinner (entrada, cambio de fecha) o `false` si es un refresco. */
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

    this.fechaCtrl.valueChanges
      .pipe(
        filter((fecha) => fecha !== ''),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.cargar$.next(true));

    // Si alguien se apunta o sale, o se cancela una reserva, lo mostrado deja de ser cierto: refresco
    // silencioso (sin spinner, fecha intacta) agrupando ráfagas en una sola petición.
    this.notificaciones.recibidas$
      .pipe(
        filter((n) => TIPOS_QUE_AFECTAN.has(n.tipo)),
        debounceTime(RETARDO_REFRESCO_MS),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.cargar$.next(false));
  }

  /** `clubId` es un input requerido: no se puede leer en el constructor, solo a partir de ngOnInit. */
  ngOnInit(): void {
    this.cargar$.next(true);
  }

  protected recargar(): void {
    this.cargar$.next(true);
  }

  protected etiquetaEstado(reserva: ReservaClub): string {
    return ETIQUETA_ESTADO[reserva.estado];
  }

  protected nombreJugador(reserva: ReservaClub): string {
    return `${reserva.usuario.nombre ?? ''} ${reserva.usuario.apellidos}`.trim();
  }

  /** Solo se puede cancelar una reserva confirmada que todavía no ha empezado. */
  protected puedeCancelar(reserva: ReservaClub): boolean {
    return reserva.estado === 'CONFIRMADA' && new Date(reserva.inicio).getTime() > Date.now();
  }

  protected cancelar(reserva: ReservaClub): void {
    if (this.cancelando()) {
      return;
    }
    const datos: ConfirmDialogData = {
      titulo: 'Cancelar reserva',
      mensaje: 'Se avisará a los jugadores de que la reserva se ha cancelado.',
      lineas: [
        { etiqueta: 'Pista', valor: reserva.pistaNombre },
        { etiqueta: 'Fecha', valor: formatearFechaLarga(reserva.inicio) },
        { etiqueta: 'Hora', valor: formatearRangoHoras(reserva.inicio, reserva.fin) },
        { etiqueta: 'Jugador', valor: this.nombreJugador(reserva) },
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

  private ejecutarCancelacion(reserva: ReservaClub): void {
    this.cancelando.set(true);
    this.reservaService.cancelar(reserva.id).subscribe({
      next: () => {
        this.cancelando.set(false);
        this.aviso('Reserva cancelada. Se ha avisado a los jugadores.');
        this.cargar$.next(false);
      },
      error: (err: unknown) => {
        this.cancelando.set(false);
        this.aviso(mensajeDeError(err, 'No se pudo cancelar la reserva. Inténtalo de nuevo.'));
        // 409/404: la reserva cambió (ya empezó, ya cancelada...) mientras mirabas: mostramos el estado real
        if (err instanceof HttpErrorResponse && (err.status === 409 || err.status === 404)) {
          this.cargar$.next(false);
        }
      },
    });
  }

  private consultar(conSpinner: boolean): Observable<ReservaClub[]> {
    if (conSpinner) {
      this.cargando.set(true);
    }
    this.error.set(null);
    return this.reservaService.delClub(this.clubId(), this.fechaCtrl.value).pipe(
      catchError((err: unknown) => {
        this.error.set(mensajeDeError(err, 'No se pudieron cargar las reservas.'));
        this.cargando.set(false);
        return EMPTY;
      }),
    );
  }

  private aviso(mensaje: string): void {
    this.snack.open(mensaje, 'Cerrar', { duration: 6000 });
  }
}
