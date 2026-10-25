import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { CambioEstadoRequest } from '../../../shared/models/incidencia.model';
import { esConflictoDeVersion, mensajeDeAccion } from '../errores-incidencia';
import { AccionIncidencia, estadoDestino } from '../incidencia-estados';
import { IncidenciaService } from '../incidencia.service';
import { ResultadoAccion } from '../resultado-accion';

type AccionDeEstado = Exclude<AccionIncidencia, 'ASIGNAR'>;

export interface EstadoDialogData {
  incidenciaId: number;
  accion: AccionDeEstado;
}

interface TextosAccion {
  titulo: string;
  confirmar: string;
  etiqueta: string;
  obligatorio: boolean;
}

const TEXTOS: Record<AccionDeEstado, TextosAccion> = {
  INICIAR: {
    titulo: 'Marcar en curso',
    confirmar: 'Marcar en curso',
    etiqueta: 'Comentario (opcional)',
    obligatorio: false,
  },
  RESOLVER: {
    titulo: 'Resolver incidencia',
    confirmar: 'Resolver',
    etiqueta: 'Qué se ha hecho (opcional)',
    obligatorio: false,
  },
  CERRAR: {
    titulo: 'Cerrar incidencia',
    confirmar: 'Cerrar',
    etiqueta: 'Comentario (opcional)',
    obligatorio: false,
  },
  REABRIR: {
    titulo: 'Reabrir incidencia',
    confirmar: 'Reabrir',
    etiqueta: 'Motivo (opcional)',
    obligatorio: false,
  },
  RECHAZAR: {
    titulo: 'Rechazar incidencia',
    confirmar: 'Rechazar',
    etiqueta: 'Motivo del rechazo',
    obligatorio: true,
  },
};

const MAX_COMENTARIO = 2000;

/**
 * Cambio de estado con comentario (obligatorio solo al rechazar). Hace la petición él mismo para mostrar un 403/409
 * dentro del diálogo; si es el 409 de "modificada por otra persona" se cierra para que la página recargue.
 */
@Component({
  selector: 'app-estado-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  templateUrl: './estado-dialog.html',
  styleUrl: './estado-dialog.scss',
})
export class EstadoDialog {
  private readonly datos = inject<EstadoDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<EstadoDialog, ResultadoAccion>>(MatDialogRef);
  private readonly incidenciaService = inject(IncidenciaService);

  protected readonly textos = TEXTOS[this.datos.accion];
  protected readonly maximo = MAX_COMENTARIO;

  protected readonly form = inject(FormBuilder).nonNullable.group({
    comentario: [
      '',
      [
        Validators.maxLength(MAX_COMENTARIO),
        ...(this.textos.obligatorio ? [Validators.required, Validators.pattern(/\S/)] : []),
      ],
    ],
  });
  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected guardar(): void {
    if (this.form.invalid || this.enviando()) {
      this.form.markAllAsTouched();
      return;
    }
    const comentario = this.form.getRawValue().comentario.trim();
    const peticion: CambioEstadoRequest = {
      estado: estadoDestino(this.datos.accion),
      ...(comentario !== '' ? { comentario } : {}),
    };
    this.enviando.set(true);
    this.error.set(null);
    this.incidenciaService.cambiarEstado(this.datos.incidenciaId, peticion).subscribe({
      next: (incidencia) => this.dialogRef.close({ tipo: 'ok', incidencia }),
      error: (err: unknown) => {
        const mensaje = mensajeDeAccion(err, 'No se pudo cambiar el estado. Inténtalo de nuevo.');
        if (esConflictoDeVersion(err)) {
          this.dialogRef.close({ tipo: 'conflicto', mensaje });
          return;
        }
        this.error.set(mensaje);
        this.enviando.set(false);
      },
    });
  }

  protected cancelar(): void {
    this.dialogRef.close();
  }
}
