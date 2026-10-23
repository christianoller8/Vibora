import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { mensajeDeError } from '../../../core/http/error.util';
import {
  ETIQUETA_TIPO_PISTA,
  Pista,
  PistaRequest,
  TIPOS_PISTA,
  TipoPista,
} from '../../../shared/models/pista.model';
import { PistaService } from '../pista.service';

export interface PistaDialogData {
  clubId: number;
  /** Pista a editar; sin ella el diálogo da de alta una nueva. */
  pista?: Pista;
}

/** Alta y edición de una pista. Hace la petición él mismo para mostrar el 409 (nombre duplicado) sin cerrarse. */
@Component({
  selector: 'app-pista-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  templateUrl: './pista-dialog.html',
  styleUrl: './pista-dialog.scss',
})
export class PistaDialog {
  private readonly datos = inject<PistaDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<PistaDialog, Pista>>(MatDialogRef);
  private readonly pistaService = inject(PistaService);

  protected readonly edicion = this.datos.pista !== undefined;
  protected readonly tipos = TIPOS_PISTA;
  protected readonly etiquetaTipo = ETIQUETA_TIPO_PISTA;

  protected readonly form = inject(FormBuilder).nonNullable.group({
    nombre: [
      this.datos.pista?.nombre ?? '',
      [Validators.required, Validators.pattern(/\S/), Validators.maxLength(255)],
    ],
    tipo: [this.datos.pista?.tipo ?? ('INTERIOR' as TipoPista), [Validators.required]],
  });
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected guardar(): void {
    if (this.form.invalid || this.guardando()) {
      this.form.markAllAsTouched();
      return;
    }
    const { nombre, tipo } = this.form.getRawValue();
    const peticion: PistaRequest = { nombre: nombre.trim(), tipo };
    this.guardando.set(true);
    this.error.set(null);

    const { clubId, pista } = this.datos;
    const operacion =
      pista === undefined
        ? this.pistaService.crear(clubId, peticion)
        : this.pistaService.actualizar(clubId, pista.id, peticion);
    operacion.subscribe({
      next: (guardada) => this.dialogRef.close(guardada),
      error: (err: unknown) => {
        this.error.set(mensajeDeError(err, 'No se pudo guardar la pista. Inténtalo de nuevo.'));
        this.guardando.set(false);
      },
    });
  }

  protected cancelar(): void {
    this.dialogRef.close();
  }
}
