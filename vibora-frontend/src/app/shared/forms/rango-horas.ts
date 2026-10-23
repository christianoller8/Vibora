import {
  AbstractControl,
  FormGroupDirective,
  NgForm,
  ValidationErrors,
  ValidatorFn,
} from '@angular/forms';
import { ErrorStateMatcher } from '@angular/material/core';

/** "09:00:00" (LocalTime del backend) → "09:00", que es lo que entiende `<input type="time">`. */
export function aHoraCorta(hora: string): string {
  return hora.slice(0, 5);
}

/**
 * Validador de grupo: la hora de `fin` debe ser posterior a la de `inicio` (misma regla que el backend para
 * horario del club y franjas tarifarias). Con algún campo vacío no opina: de eso se encarga «obligatorio».
 * Las horas "HH:mm" se comparan como texto porque es el orden natural del formato.
 */
export function rangoHorasValidator(inicio: string, fin: string): ValidatorFn {
  return (grupo: AbstractControl): ValidationErrors | null => {
    const desde = grupo.get(inicio)?.value as string | null | undefined;
    const hasta = grupo.get(fin)?.value as string | null | undefined;
    if (!desde || !hasta) {
      return null;
    }
    return aHoraCorta(hasta) > aHoraCorta(desde) ? null : { rangoHoras: true };
  };
}

/** Marca el campo de fin como erróneo cuando falla la regla del grupo (Material solo mira el propio control). */
export class RangoHorasMatcher implements ErrorStateMatcher {
  isErrorState(control: AbstractControl | null, form: FormGroupDirective | NgForm | null): boolean {
    const rangoMal = form?.form.hasError('rangoHoras') ?? false;
    return !!control && (control.invalid || rangoMal) && (control.touched || !!form?.submitted);
  }
}
