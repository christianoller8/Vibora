import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import {
  EMPTY,
  Observable,
  Subject,
  catchError,
  debounceTime,
  filter,
  merge,
  switchMap,
} from 'rxjs';
import { ClubService } from '../../../core/api/club.service';
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
  ConfirmDialogLinea,
} from '../../../shared/components/confirm-dialog/confirm-dialog';
import { Club } from '../../../shared/models/club.model';
import { TipoNotificacion } from '../../../shared/models/notificacion.model';
import { JugadorPartido, Partido } from '../../../shared/models/partido.model';
import { PartidoService } from '../partido.service';

/** Notificaciones que cambian lo que esta página muestra (plazas, jugadores, partidos cancelados). */
const TIPOS_QUE_AFECTAN: ReadonlySet<TipoNotificacion> = new Set<TipoNotificacion>([
  'JUGADOR_SE_APUNTO',
  'JUGADOR_SALIO',
  'PARTIDO_COMPLETO',
  'RESERVA_CANCELADA',
]);

/** Ventana para agrupar varios push seguidos en un único refresco. */
const RETARDO_REFRESCO_MS = 300;

@Component({
  selector: 'app-partidos-abiertos',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './partidos-abiertos.html',
  styleUrl: './partidos-abiertos.scss',
})
export class PartidosAbiertos {
  private readonly partidoService = inject(PartidoService);
  private readonly clubService = inject(ClubService);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly notificaciones = inject(NotificacionesStore);

  /** Un partido de un día anterior a hoy (en Madrid) ya no se puede jugar. */
  protected readonly minFecha = hoyEnMadrid();

  readonly clubCtrl = new FormControl<number | null>(null);
  readonly fechaCtrl = new FormControl('', { nonNullable: true });

  protected readonly clubes = signal<Club[]>([]);
  protected readonly partidos = signal<Partido[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  /** Partido sobre el que hay una acción (apuntarse/salir) en vuelo. */
  protected readonly enCurso = signal<number | null>(null);

  protected readonly formatearFecha = formatearFechaLarga;
  protected readonly formatearRango = formatearRangoHoras;
  protected readonly formatearPrecio = formatearPrecio;

  /** Emite `true` si la recarga debe mostrar el spinner (filtros) o `false` si es un refresco silencioso. */
  private readonly recargar$ = new Subject<boolean>();

  constructor() {
    merge(this.clubCtrl.valueChanges, this.fechaCtrl.valueChanges)
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.recargar$.next(true));

    this.recargar$
      .pipe(
        switchMap((conSpinner) => this.consultar(conSpinner)),
        takeUntilDestroyed(),
      )
      .subscribe((partidos) => {
        this.partidos.set(partidos);
        this.cargando.set(false);
      });

    // Si alguien se apunta/sale, se llena o se cancela un partido, las plazas mostradas dejan de ser ciertas:
    // refresco silencioso (sin spinner, filtros intactos) agrupando ráfagas en una sola petición.
    this.notificaciones.recibidas$
      .pipe(
        filter((n) => TIPOS_QUE_AFECTAN.has(n.tipo)),
        debounceTime(RETARDO_REFRESCO_MS),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.recargar$.next(false));

    this.clubService.listar().subscribe({
      next: (clubes) => this.clubes.set(clubes),
      error: () => {
        // sin lista de clubes solo falta el filtro; los partidos se siguen viendo
      },
    });
    this.recargar$.next(true);
  }

  protected asientosLibres(partido: Partido): number[] {
    return Array.from({ length: Math.max(0, partido.plazasLibres) }, (_, i) => i);
  }

  protected textoPlazas(partido: Partido): string {
    if (partido.plazasLibres <= 0) {
      return 'Completo';
    }
    return partido.plazasLibres === 1 ? '1 plaza libre' : `${partido.plazasLibres} plazas libres`;
  }

  protected nombreJugador(jugador: JugadorPartido): string {
    return jugador.inicialApellido
      ? `${jugador.nombre} ${jugador.inicialApellido}.`
      : jugador.nombre;
  }

  protected puedeApuntarse(partido: Partido): boolean {
    return !partido.yaApuntado && partido.plazasLibres > 0;
  }

  /** El organizador no se "sale": tiene que cancelar la reserva. */
  protected puedeSalir(partido: Partido): boolean {
    return partido.yaApuntado && !partido.esOrganizador;
  }

  protected apuntarse(partido: Partido): void {
    const datos: ConfirmDialogData = {
      titulo: 'Apuntarte al partido',
      mensaje: 'El cargo con tu parte aparecerá en "Deudas" para que lo pagues.',
      lineas: [
        ...this.resumen(partido),
        { etiqueta: 'Tu parte', valor: formatearPrecio(partido.cuotaPorJugador) },
      ],
      textoConfirmar: 'Apuntarme',
    };
    this.confirmar(datos, () =>
      this.ejecutar(
        partido,
        this.partidoService.apuntarse(partido.reservaId),
        'Te has apuntado al partido.',
      ),
    );
  }

  protected salir(partido: Partido): void {
    const datos: ConfirmDialogData = {
      titulo: 'Salir del partido',
      mensaje: '¿Seguro que quieres salir? Tu cargo pendiente se anulará.',
      lineas: this.resumen(partido),
      textoConfirmar: 'Salir',
      textoCancelar: 'Quedarme',
    };
    this.confirmar(datos, () =>
      this.ejecutar(
        partido,
        this.partidoService.salir(partido.reservaId),
        'Has salido del partido.',
      ),
    );
  }

  private resumen(partido: Partido): ConfirmDialogLinea[] {
    return [
      { etiqueta: 'Club', valor: partido.club.nombre },
      { etiqueta: 'Pista', valor: partido.pistaNombre },
      { etiqueta: 'Fecha', valor: formatearFechaLarga(partido.inicio) },
      { etiqueta: 'Hora', valor: formatearRangoHoras(partido.inicio, partido.fin) },
    ];
  }

  private confirmar(datos: ConfirmDialogData, alConfirmar: () => void): void {
    if (this.enCurso() !== null) {
      return;
    }
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, { data: datos })
      .afterClosed()
      .subscribe((confirmado) => {
        if (confirmado) {
          alConfirmar();
        }
      });
  }

  private ejecutar(partido: Partido, accion: Observable<Partido>, exito: string): void {
    this.enCurso.set(partido.reservaId);
    accion.subscribe({
      next: () => {
        this.enCurso.set(null);
        this.aviso(exito);
        this.recargar$.next(false);
      },
      error: (err: unknown) => {
        this.enCurso.set(null);
        this.aviso(mensajeDeError(err, 'No se pudo completar la acción. Inténtalo de nuevo.'));
        // 409/404: el partido cambió (completo, cerrado...) mientras mirabas: mostramos el estado real
        if (err instanceof HttpErrorResponse && (err.status === 409 || err.status === 404)) {
          this.recargar$.next(false);
        }
      },
    });
  }

  private consultar(conSpinner: boolean): Observable<Partido[]> {
    if (conSpinner) {
      this.cargando.set(true);
    }
    this.error.set(null);
    return this.partidoService
      .listar({
        clubId: this.clubCtrl.value ?? undefined,
        fecha: this.fechaCtrl.value || undefined,
      })
      .pipe(
        catchError((err: unknown) => {
          this.error.set(mensajeDeError(err, 'No se pudieron cargar los partidos.'));
          this.partidos.set([]);
          this.cargando.set(false);
          return EMPTY;
        }),
      );
  }

  private aviso(mensaje: string): void {
    this.snack.open(mensaje, 'Cerrar', { duration: 6000 });
  }
}
