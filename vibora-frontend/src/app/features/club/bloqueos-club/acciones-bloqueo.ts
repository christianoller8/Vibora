import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable, catchError, finalize, map, of, switchMap } from 'rxjs';
import { mensajeDeError } from '../../../core/http/error.util';
import {
  ConfirmDialog,
  ConfirmDialogData,
} from '../../../shared/components/confirm-dialog/confirm-dialog';
import { ReservaAfectada } from '../../../shared/models/bloqueo.model';
import { BloqueoService } from '../bloqueo.service';
import { AfectadasDialog, AfectadasDialogData } from './afectadas-dialog';
import { datosConfirmacionCancelar, mensajeCancelacion } from './afectadas-textos';

/** Un bloqueo concreto: lo que hace falta para pedir sus afectadas, cancelarlas o levantarlo. */
export interface BloqueoRef {
  clubId: number;
  pistaId: number;
  pistaNombre: string;
  bloqueoId: number;
  /** Solo para mostrarlo en la confirmación de levantar. */
  motivo?: string;
}

/** 404 y 409 quieren decir que lo que se veía ya no es cierto (otra sesión lo cambió): conviene recargar. */
const esDatoCaducado = (err: unknown): boolean =>
  err instanceof HttpErrorResponse && (err.status === 404 || err.status === 409);

/**
 * Las tres acciones sobre un bloqueo existente (ver afectadas, cancelarlas y levantarlo), con sus confirmaciones y
 * avisos, compartidas por la pestaña "Bloqueos" y por el detalle de una incidencia. `cancelarAfectadas` y `levantar`
 * emiten `true` cuando lo que se ve en pantalla puede haber quedado desfasado y conviene recargarlo.
 */
@Injectable({ providedIn: 'root' })
export class AccionesBloqueo {
  private readonly bloqueos = inject(BloqueoService);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);

  /** Hay una petición en vuelo: los botones se deshabilitan para no repetirla. */
  readonly ocupado = signal(false);

  /** Abre el listado de afectadas; si ya se conocen (acaba de crearse el bloqueo) no se piden de nuevo. */
  verAfectadas(ref: BloqueoRef, precargadas?: ReservaAfectada[]): void {
    const datos: AfectadasDialogData = {
      clubId: ref.clubId,
      pistaId: ref.pistaId,
      pistaNombre: ref.pistaNombre,
      bloqueoId: ref.bloqueoId,
      ...(precargadas !== undefined ? { afectadas: precargadas } : {}),
    };
    this.dialog.open<AfectadasDialog, AfectadasDialogData, number>(AfectadasDialog, {
      data: datos,
      width: '56rem',
      maxWidth: '95vw',
    });
  }

  /** Averigua cuántas son, confirma diciéndolo, cancela y avisa del resultado (0 si no había ninguna). */
  cancelarAfectadas(ref: BloqueoRef): Observable<boolean> {
    if (this.ocupado()) {
      return of(false);
    }
    return this.consultarAfectadas(ref).pipe(
      switchMap((afectadas) => {
        if (afectadas === null) {
          return of(false); // el error ya se avisó
        }
        if (afectadas.length === 0) {
          this.aviso(mensajeCancelacion(0));
          return of(false);
        }
        return this.confirmar(datosConfirmacionCancelar(afectadas.length, ref.pistaNombre)).pipe(
          switchMap((confirmado) => (confirmado ? this.ejecutarCancelacion(ref) : of(false))),
        );
      }),
    );
  }

  /** Pide confirmación y levanta (idempotente en el backend). */
  levantar(ref: BloqueoRef): Observable<boolean> {
    if (this.ocupado()) {
      return of(false);
    }
    const datos: ConfirmDialogData = {
      titulo: 'Levantar bloqueo',
      mensaje:
        'La pista volverá a admitir reservas nuevas. Las reservas que se hayan cancelado no se restauran.',
      lineas: [
        { etiqueta: 'Pista', valor: ref.pistaNombre },
        ...(ref.motivo !== undefined ? [{ etiqueta: 'Motivo', valor: ref.motivo }] : []),
      ],
      textoConfirmar: 'Levantar',
      textoCancelar: 'Mantener',
    };
    return this.confirmar(datos).pipe(
      switchMap((confirmado) => (confirmado ? this.ejecutarLevantado(ref) : of(false))),
    );
  }

  private consultarAfectadas(ref: BloqueoRef): Observable<ReservaAfectada[] | null> {
    this.ocupado.set(true);
    return this.bloqueos.afectadas(ref.clubId, ref.pistaId, ref.bloqueoId).pipe(
      catchError((err: unknown) => {
        this.aviso(mensajeDeError(err, 'No se pudieron consultar las reservas afectadas.'));
        return of(null);
      }),
      finalize(() => this.ocupado.set(false)),
    );
  }

  private ejecutarCancelacion(ref: BloqueoRef): Observable<boolean> {
    this.ocupado.set(true);
    return this.bloqueos.cancelarAfectadas(ref.clubId, ref.pistaId, ref.bloqueoId).pipe(
      map((resultado) => {
        this.aviso(mensajeCancelacion(resultado.canceladas));
        return false;
      }),
      catchError((err: unknown) => {
        this.aviso(
          mensajeDeError(err, 'No se pudieron cancelar las reservas. Inténtalo de nuevo.'),
        );
        return of(esDatoCaducado(err));
      }),
      finalize(() => this.ocupado.set(false)),
    );
  }

  private ejecutarLevantado(ref: BloqueoRef): Observable<boolean> {
    this.ocupado.set(true);
    return this.bloqueos.levantar(ref.clubId, ref.pistaId, ref.bloqueoId).pipe(
      map(() => {
        this.aviso('Bloqueo levantado. La pista vuelve a admitir reservas.');
        return true;
      }),
      catchError((err: unknown) => {
        this.aviso(mensajeDeError(err, 'No se pudo levantar el bloqueo. Inténtalo de nuevo.'));
        return of(esDatoCaducado(err));
      }),
      finalize(() => this.ocupado.set(false)),
    );
  }

  private confirmar(datos: ConfirmDialogData): Observable<boolean> {
    return this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, { data: datos })
      .afterClosed()
      .pipe(map((confirmado) => confirmado === true));
  }

  private aviso(mensaje: string): void {
    this.snack.open(mensaje, 'Cerrar', { duration: 6000 });
  }
}
