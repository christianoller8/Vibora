import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogActions,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { mensajeDeError } from '../../../core/http/error.util';
import { formatearFechaLarga, formatearRangoHoras } from '../../../core/util/fecha';
import {
  ConfirmDialog,
  ConfirmDialogData,
} from '../../../shared/components/confirm-dialog/confirm-dialog';
import { ReservaAfectada } from '../../../shared/models/bloqueo.model';
import { BloqueoService } from '../bloqueo.service';
import { datosConfirmacionCancelar, mensajeCancelacion } from './afectadas-textos';

export interface AfectadasDialogData {
  clubId: number;
  pistaId: number;
  pistaNombre: string;
  bloqueoId: number;
  /** Si ya se conocen (respuesta de crear un bloqueo) no se piden de nuevo. */
  afectadas?: ReservaAfectada[];
}

const JUGADORES_POR_RESERVA = 4;

/**
 * Reservas afectadas por un bloqueo, con la opción de cancelarlas (tras confirmar). Se cierra devolviendo cuántas
 * reservas se han cancelado desde aquí (nada si ninguna).
 */
@Component({
  selector: 'app-afectadas-dialog',
  // Directivas sueltas y no MatDialogModule: este diálogo inyecta MatDialog (para confirmar) y el módulo lo
  // proveería de nuevo en el inyector del componente
  imports: [
    MatButtonModule,
    MatChipsModule,
    MatDialogActions,
    MatDialogContent,
    MatDialogTitle,
    MatProgressSpinnerModule,
    MatTableModule,
  ],
  templateUrl: './afectadas-dialog.html',
  styleUrl: './afectadas-dialog.scss',
})
export class AfectadasDialog {
  protected readonly datos = inject<AfectadasDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<AfectadasDialog, number>>(MatDialogRef);
  private readonly bloqueoService = inject(BloqueoService);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly afectadas = signal<ReservaAfectada[]>(this.datos.afectadas ?? []);
  protected readonly cargando = signal(this.datos.afectadas === undefined);
  protected readonly errorCarga = signal<string | null>(null);
  protected readonly cancelando = signal(false);
  protected readonly errorCancelar = signal<string | null>(null);
  protected readonly resultado = signal<string | null>(null);
  private canceladasAqui = 0;

  protected readonly columnas = ['cuando', 'pista', 'organizador', 'jugadores', 'estado'];
  protected readonly maxJugadores = JUGADORES_POR_RESERVA;
  protected readonly fechaLarga = formatearFechaLarga;
  protected readonly rango = formatearRangoHoras;

  constructor() {
    if (this.datos.afectadas === undefined) {
      this.cargar();
    }
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.errorCarga.set(null);
    const { clubId, pistaId, bloqueoId } = this.datos;
    this.bloqueoService
      .afectadas(clubId, pistaId, bloqueoId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (afectadas) => {
          this.afectadas.set(afectadas);
          this.cargando.set(false);
        },
        error: (err: unknown) => {
          this.errorCarga.set(mensajeDeError(err, 'No se pudieron cargar las reservas afectadas.'));
          this.cargando.set(false);
        },
      });
  }

  protected cancelarAfectadas(): void {
    const cantidad = this.afectadas().length;
    if (cantidad === 0 || this.cancelando()) {
      return;
    }
    const datos: ConfirmDialogData = datosConfirmacionCancelar(cantidad, this.datos.pistaNombre);
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, { data: datos })
      .afterClosed()
      .subscribe((confirmado) => {
        if (confirmado) {
          this.ejecutarCancelacion();
        }
      });
  }

  protected cerrar(): void {
    this.dialogRef.close(this.canceladasAqui > 0 ? this.canceladasAqui : undefined);
  }

  private ejecutarCancelacion(): void {
    this.cancelando.set(true);
    this.errorCancelar.set(null);
    this.resultado.set(null);
    const { clubId, pistaId, bloqueoId } = this.datos;
    this.bloqueoService.cancelarAfectadas(clubId, pistaId, bloqueoId).subscribe({
      next: (resultado) => {
        this.cancelando.set(false);
        this.canceladasAqui += resultado.canceladas;
        this.resultado.set(mensajeCancelacion(resultado.canceladas));
        // Las que ya no están afectadas salen de la lista; si el servidor canceló menos de las que se veían, el resto
        // sigue ahí (por ejemplo, una que se movió) y se puede reintentar.
        this.afectadas.update((lista) => lista.filter((a) => !resultado.ids.includes(a.id)));
        if (resultado.canceladas === 0) {
          this.afectadas.set([]);
        }
      },
      error: (err: unknown) => {
        this.cancelando.set(false);
        this.errorCancelar.set(
          mensajeDeError(err, 'No se pudieron cancelar las reservas. Inténtalo de nuevo.'),
        );
      },
    });
  }
}
