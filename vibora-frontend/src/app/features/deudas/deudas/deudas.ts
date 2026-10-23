import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipListbox, MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { EMPTY, Subject, catchError, debounceTime, filter, forkJoin, switchMap } from 'rxjs';
import { mensajeDeError } from '../../../core/http/error.util';
import { NotificacionesStore } from '../../../core/notificaciones/notificaciones.store';
import {
  formatearFecha,
  formatearFechaLarga,
  formatearHora,
  formatearPrecio,
} from '../../../core/util/fecha';
import {
  ConfirmDialog,
  ConfirmDialogData,
} from '../../../shared/components/confirm-dialog/confirm-dialog';
import { Cargo, EstadoCargo, Saldos, UsuarioCargo } from '../../../shared/models/cargo.model';
import { TipoNotificacion } from '../../../shared/models/notificacion.model';
import { CargoService } from '../cargo.service';

export type FiltroEstado = 'PENDIENTE' | 'TODOS';

/**
 * Notificaciones que cambian lo que esta página muestra: cargos nuevos o pagados y, como una salida o una
 * cancelación pueden anular cargos, también esas dos.
 */
const TIPOS_QUE_AFECTAN: ReadonlySet<TipoNotificacion> = new Set<TipoNotificacion>([
  'CARGO_NUEVO',
  'PAGO_RECIBIDO',
  'JUGADOR_SALIO',
  'RESERVA_CANCELADA',
]);

/** Ventana para agrupar varios push seguidos en un único refresco. */
const RETARDO_REFRESCO_MS = 300;

const ETIQUETA_ESTADO: Record<EstadoCargo, string> = {
  PENDIENTE: 'Pendiente',
  PAGADO: 'Pagado',
  ANULADO: 'Anulado',
  REEMBOLSADO: 'Reembolsado',
};

function nombreCompleto(usuario: { nombre: string; apellidos: string | null }): string {
  return `${usuario.nombre} ${usuario.apellidos ?? ''}`.trim();
}

@Component({
  selector: 'app-deudas',
  imports: [
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatTabsModule,
  ],
  templateUrl: './deudas.html',
  styleUrl: './deudas.scss',
})
export class Deudas {
  private readonly cargoService = inject(CargoService);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly notificaciones = inject(NotificacionesStore);

  protected readonly filtro = signal<FiltroEstado>('PENDIENTE');
  protected readonly saldos = signal<Saldos | null>(null);
  protected readonly debo = signal<Cargo[]>([]);
  protected readonly meDeben = signal<Cargo[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly pagando = signal(false);

  protected readonly formatearFecha = formatearFecha;
  protected readonly formatearFechaLarga = formatearFechaLarga;
  protected readonly formatearHora = formatearHora;
  protected readonly formatearPrecio = formatearPrecio;
  protected readonly nombreCompleto = nombreCompleto;

  /** Emite `true` si la carga debe mostrar el spinner (entrada, cambio de filtro) o `false` si es un refresco. */
  private readonly cargar$ = new Subject<boolean>();

  constructor() {
    this.cargar$
      .pipe(
        switchMap((conSpinner) => this.consultar(conSpinner)),
        takeUntilDestroyed(),
      )
      .subscribe(({ saldos, debo, meDeben }) => {
        this.saldos.set(saldos);
        this.debo.set(debo);
        this.meDeben.set(meDeben);
        this.cargando.set(false);
      });
    // Refresco silencioso (sin spinner, pestaña y filtro intactos) agrupando ráfagas en una sola petición.
    this.notificaciones.recibidas$
      .pipe(
        filter((n) => TIPOS_QUE_AFECTAN.has(n.tipo)),
        debounceTime(RETARDO_REFRESCO_MS),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.cargar$.next(false));
    this.cargar$.next(true);
  }

  protected cargar(conSpinner = true): void {
    this.cargar$.next(conSpinner);
  }

  protected cambiarFiltro(valor: FiltroEstado | undefined, lista: MatChipListbox): void {
    if (valor === undefined) {
      lista.value = this.filtro(); // pulsar el chip ya elegido no lo deselecciona
      return;
    }
    if (valor !== this.filtro()) {
      this.filtro.set(valor);
      this.cargar(false); // refresco silencioso: sin spinner, para no perder la pestaña elegida
    }
  }

  protected etiquetaEstado(estado: EstadoCargo): string {
    return ETIQUETA_ESTADO[estado];
  }

  /** "+5,00 €" cuando me deben más, "-5,00 €" cuando debo más. */
  protected formatearNeto(neto: number): string {
    return neto > 0 ? `+${formatearPrecio(neto)}` : formatearPrecio(neto);
  }

  protected claseNeto(neto: number): string {
    return neto > 0 ? 'positivo' : neto < 0 ? 'negativo' : '';
  }

  protected pagar(cargo: Cargo): void {
    if (this.pagando() || cargo.estado !== 'PENDIENTE') {
      return;
    }
    const datos: ConfirmDialogData = {
      titulo: 'Pagar cargo',
      mensaje:
        'Es un pago simulado (un Bizum de mentira): no se mueve dinero real, solo se marca el cargo como pagado.',
      lineas: [
        { etiqueta: 'Club', valor: cargo.clubNombre },
        { etiqueta: 'Pista', valor: cargo.pistaNombre },
        { etiqueta: 'Fecha', valor: formatearFechaLarga(cargo.inicio) },
        { etiqueta: 'Importe', valor: formatearPrecio(cargo.importe) },
        { etiqueta: 'Pagar a', valor: nombreCompleto(cargo.acreedor) },
      ],
      textoConfirmar: 'Pagar',
    };
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, { data: datos })
      .afterClosed()
      .subscribe((confirmado) => {
        if (confirmado) {
          this.ejecutarPago(cargo);
        }
      });
  }

  /** Nombre de la otra parte: a quien debo (acreedor) en "Debo"; quien me debe (deudor) en "Me deben". */
  protected contraparte(cargo: Cargo, lista: 'debo' | 'me-deben'): UsuarioCargo {
    return lista === 'debo' ? cargo.acreedor : cargo.deudor;
  }

  private ejecutarPago(cargo: Cargo): void {
    this.pagando.set(true);
    this.cargoService.pagar(cargo.id).subscribe({
      next: () => {
        this.pagando.set(false);
        this.aviso('Pago simulado realizado. El cargo figura como pagado.');
        this.cargar(false);
      },
      error: (err: unknown) => {
        this.pagando.set(false);
        this.aviso(mensajeDeError(err, 'No se pudo realizar el pago. Inténtalo de nuevo.'));
        this.cargar(false); // quizá el cargo cambió de estado: mostramos lo real
      },
    });
  }

  private consultar(conSpinner: boolean) {
    if (conSpinner) {
      this.cargando.set(true);
    }
    this.error.set(null);
    const estado = this.filtro() === 'PENDIENTE' ? 'PENDIENTE' : undefined;
    return forkJoin({
      saldos: this.cargoService.saldos(),
      debo: this.cargoService.mios(estado),
      meDeben: this.cargoService.porCobrar(estado),
    }).pipe(
      catchError((err: unknown) => {
        this.error.set(mensajeDeError(err, 'No se pudieron cargar tus deudas.'));
        this.cargando.set(false);
        return EMPTY;
      }),
    );
  }

  private aviso(mensaje: string): void {
    this.snack.open(mensaje, 'Cerrar', { duration: 6000 });
  }
}
