import { Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';
import { switchMap, tap } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/http/error.util';
import { DESTINO_POR_DEFECTO } from '../redirect.util';

/** BCrypt (backend) admite 72 BYTES, no caracteres. */
const MAX_BYTES_PASSWORD = 72;

/** Longitud en bytes UTF-8 de la forma NFC del texto (la que normaliza y hashea el backend). */
function maxBytesUtf8(max: number): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const valor: unknown = control.value;
    if (typeof valor !== 'string') {
      return null;
    }
    const bytes = new TextEncoder().encode(valor.normalize('NFC')).length;
    return bytes > max ? { maxBytes: { max, actual: bytes } } : null;
  };
}

@Component({
  selector: 'app-registro',
  imports: [ReactiveFormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './registro.html',
  styleUrl: './registro.scss',
})
export class Registro {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  // Las reglas replican las del backend (RegistroRequest)
  protected readonly form = inject(FormBuilder).nonNullable.group({
    nombre: ['', [Validators.maxLength(255)]],
    apellidos: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(255)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    password: [
      '',
      [Validators.required, Validators.minLength(8), maxBytesUtf8(MAX_BYTES_PASSWORD)],
    ],
  });
  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected enviar(): void {
    if (this.form.invalid || this.enviando()) {
      this.form.markAllAsTouched();
      return;
    }
    this.enviando.set(true);
    this.error.set(null);

    const { nombre, apellidos, email, password } = this.form.getRawValue();
    const datos = {
      email: email.trim(),
      password,
      nombre: nombre.trim() || null,
      apellidos: apellidos.trim(),
    };

    let cuentaCreada = false;
    this.auth
      .registro(datos)
      .pipe(
        tap(() => (cuentaCreada = true)),
        switchMap(() => this.auth.login(datos.email, datos.password)),
      )
      .subscribe({
        next: () => void this.router.navigateByUrl(DESTINO_POR_DEFECTO),
        error: (err: unknown) => {
          this.error.set(
            cuentaCreada
              ? 'Tu cuenta se creó, pero no pudimos iniciar sesión. Prueba desde «Iniciar sesión».'
              : mensajeDeError(err, 'No se pudo completar el registro. Inténtalo de nuevo.'),
          );
          this.enviando.set(false);
        },
      });
  }
}
