import { Component, effect, inject, input, output } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import {
  RangoHorasMatcher,
  aHoraCorta,
  rangoHorasValidator,
} from '../../../shared/forms/rango-horas';
import { Club, ClubRequest } from '../../../shared/models/club.model';

/**
 * Formulario de datos de un club, compartido por el alta («Crear mi club») y la edición («Datos»). No hace
 * peticiones: emite la petición ya limpia y quien lo usa decide qué hacer con ella.
 */
@Component({
  selector: 'app-club-formulario',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './club-formulario.html',
  styleUrl: './club-formulario.scss',
})
export class ClubFormulario {
  /** Club a editar; sin él, el formulario arranca vacío (alta). */
  readonly club = input<Club | null>(null);
  readonly textoEnviar = input.required<string>();
  readonly enviando = input(false);
  readonly error = input<string | null>(null);
  readonly enviar = output<ClubRequest>();

  // Las reglas replican las del backend (ClubRequest + «cierre posterior a apertura» del servicio)
  protected readonly form = inject(FormBuilder).nonNullable.group(
    {
      nombre: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(255)]],
      direccion: ['', [Validators.maxLength(255)]],
      ciudad: ['', [Validators.maxLength(255)]],
      horaApertura: ['09:00', [Validators.required]],
      horaCierre: ['22:00', [Validators.required]],
    },
    { validators: rangoHorasValidator('horaApertura', 'horaCierre') },
  );
  protected readonly matcherCierre = new RangoHorasMatcher();

  constructor() {
    effect(() => {
      const club = this.club();
      if (club !== null) {
        this.form.reset({
          nombre: club.nombre,
          direccion: club.direccion ?? '',
          ciudad: club.ciudad ?? '',
          horaApertura: aHoraCorta(club.horaApertura),
          horaCierre: aHoraCorta(club.horaCierre),
        });
      }
    });
  }

  protected alEnviar(): void {
    if (this.form.invalid || this.enviando()) {
      this.form.markAllAsTouched();
      return;
    }
    const { nombre, direccion, ciudad, horaApertura, horaCierre } = this.form.getRawValue();
    this.enviar.emit({
      nombre: nombre.trim(),
      direccion: direccion.trim() || null,
      ciudad: ciudad.trim() || null,
      horaApertura: aHoraCorta(horaApertura),
      horaCierre: aHoraCorta(horaCierre),
    });
  }
}
