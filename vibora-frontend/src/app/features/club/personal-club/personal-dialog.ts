import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { mensajeDeError } from '../../../core/http/error.util';
import { MiembroPersonal } from '../../../shared/models/incidencia.model';
import { ROLES_PERSONAL, RolPersonal } from '../../../shared/models/personal.model';
import { ETIQUETA_ROL } from '../../../shared/models/usuario.model';
import { PersonalService } from '../personal.service';

export interface PersonalDialogData {
  clubId: number;
}

/** Máximo que admite el backend (`@Size(max = 255)`). */
const EMAIL_MAXIMO = 255;

/** Valida el email recortado: es lo que se envía, y los espacios que se cuelan al pegar no deben invalidarlo. */
function emailRecortado(control: AbstractControl): ValidationErrors | null {
  const valor = String(control.value ?? '').trim();
  if (valor === '') {
    return { required: true };
  }
  if (valor.length > EMAIL_MAXIMO) {
    return { maxlength: true };
  }
  return Validators.email({ value: valor } as AbstractControl);
}

/**
 * Alta de personal. Hace la petición él mismo para mostrar los errores (404 "no existe", 409 "ya pertenece a un club")
 * sin cerrarse, y se cierra con la persona añadida.
 */
@Component({
  selector: 'app-personal-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  templateUrl: './personal-dialog.html',
  styleUrl: './personal-dialog.scss',
})
export class PersonalDialog {
  private readonly datos = inject<PersonalDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<PersonalDialog, MiembroPersonal>>(MatDialogRef);
  private readonly personalService = inject(PersonalService);

  protected readonly roles = ROLES_PERSONAL;
  protected readonly etiquetaRol = ETIQUETA_ROL;

  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [emailRecortado]],
    rol: ['MONITOR' as RolPersonal, [Validators.required]],
  });
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected guardar(): void {
    if (this.form.invalid || this.guardando()) {
      this.form.markAllAsTouched();
      return;
    }
    const { email, rol } = this.form.getRawValue();
    this.guardando.set(true);
    this.error.set(null);
    this.personalService.agregar(this.datos.clubId, { email: email.trim(), rol }).subscribe({
      next: (persona) => this.dialogRef.close(persona),
      error: (err: unknown) => {
        this.error.set(mensajeDeError(err, this.mensajePorDefecto(err)));
        this.guardando.set(false);
      },
    });
  }

  protected cancelar(): void {
    this.dialogRef.close();
  }

  /** Texto propio por si el backend no manda mensaje. */
  private mensajePorDefecto(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      if (err.status === 404) {
        return 'No existe ningún usuario con ese email.';
      }
      if (err.status === 409) {
        return 'Ese usuario ya pertenece a un club.';
      }
    }
    return 'No se pudo añadir a la persona. Inténtalo de nuevo.';
  }
}
