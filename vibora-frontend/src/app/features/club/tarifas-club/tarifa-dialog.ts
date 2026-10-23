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
import {
  RangoHorasMatcher,
  aHoraCorta,
  rangoHorasValidator,
} from '../../../shared/forms/rango-horas';
import {
  DIAS_SEMANA,
  DiaSemana,
  ETIQUETA_DIA,
  ETIQUETA_TIPO_TARIFA,
  TIPOS_TARIFA,
  Tarifa,
  TarifaRequest,
  TipoTarifa,
} from '../../../shared/models/tarifa.model';
import { TarifaService } from '../tarifa.service';

/** Máximo que admite el backend: `@Digits(integer = 6, fraction = 2)`. */
const PRECIO_MAXIMO = 999_999.99;

/** El backend guarda céntimos: más de dos decimales no se pueden representar. */
function maxDosDecimales(control: AbstractControl): ValidationErrors | null {
  const valor: unknown = control.value;
  if (typeof valor !== 'number') {
    return null;
  }
  return Math.abs(valor * 100 - Math.round(valor * 100)) < 1e-6 ? null : { decimales: true };
}

export interface TarifaDialogData {
  clubId: number;
  /** Franja a editar; sin ella el diálogo da de alta una nueva. */
  tarifa?: Tarifa;
}

/** Alta y edición de una franja tarifaria. Hace la petición él mismo para mostrar el 409 de solape sin cerrarse. */
@Component({
  selector: 'app-tarifa-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  templateUrl: './tarifa-dialog.html',
  styleUrl: './tarifa-dialog.scss',
})
export class TarifaDialog {
  private readonly datos = inject<TarifaDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<TarifaDialog, Tarifa>>(MatDialogRef);
  private readonly tarifaService = inject(TarifaService);

  protected readonly edicion = this.datos.tarifa !== undefined;
  protected readonly dias = DIAS_SEMANA;
  protected readonly etiquetaDia = ETIQUETA_DIA;
  protected readonly tipos = TIPOS_TARIFA;
  protected readonly etiquetaTipo = ETIQUETA_TIPO_TARIFA;
  protected readonly matcherFin = new RangoHorasMatcher();

  // Las reglas replican las del backend (TarifaRequest + «fin posterior a inicio» del servicio)
  protected readonly form = inject(FormBuilder).nonNullable.group(
    {
      diaSemana: [this.datos.tarifa?.diaSemana ?? ('MONDAY' as DiaSemana), [Validators.required]],
      horaInicio: [aHoraCorta(this.datos.tarifa?.horaInicio ?? ''), [Validators.required]],
      horaFin: [aHoraCorta(this.datos.tarifa?.horaFin ?? ''), [Validators.required]],
      precioHora: [
        this.datos.tarifa?.precioHora ?? (null as number | null),
        [Validators.required, Validators.min(0), Validators.max(PRECIO_MAXIMO), maxDosDecimales],
      ],
      tipo: [this.datos.tarifa?.tipo ?? ('NORMAL' as TipoTarifa), [Validators.required]],
    },
    { validators: rangoHorasValidator('horaInicio', 'horaFin') },
  );
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected guardar(): void {
    if (this.form.invalid || this.guardando()) {
      this.form.markAllAsTouched();
      return;
    }
    const { diaSemana, horaInicio, horaFin, precioHora, tipo } = this.form.getRawValue();
    const peticion: TarifaRequest = {
      diaSemana,
      horaInicio: aHoraCorta(horaInicio),
      horaFin: aHoraCorta(horaFin),
      precioHora: Number(precioHora),
      tipo,
    };
    this.guardando.set(true);
    this.error.set(null);

    const { clubId, tarifa } = this.datos;
    const operacion =
      tarifa === undefined
        ? this.tarifaService.crear(clubId, peticion)
        : this.tarifaService.actualizar(clubId, tarifa.id, peticion);
    operacion.subscribe({
      next: (guardada) => this.dialogRef.close(guardada),
      error: (err: unknown) => {
        this.error.set(mensajeDeError(err, 'No se pudo guardar la tarifa. Inténtalo de nuevo.'));
        this.guardando.set(false);
      },
    });
  }

  protected cancelar(): void {
    this.dialogRef.close();
  }
}
