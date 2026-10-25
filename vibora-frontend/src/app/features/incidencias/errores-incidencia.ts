import { HttpErrorResponse } from '@angular/common/http';
import { mensajeDeError } from '../../core/http/error.util';

/** Fragmento del mensaje del 409 de bloqueo optimista (@Version) del backend. */
const FRAGMENTO_CONFLICTO_VERSION = 'modificada por otra persona';

/**
 * ¿Es el 409 de "otra persona modificó la incidencia a la vez"? Se distingue de los demás 409 (reglas de
 * negocio) porque este se resuelve recargando, no avisando de que la acción es ilegal.
 */
export function esConflictoDeVersion(err: unknown): boolean {
  if (!(err instanceof HttpErrorResponse) || err.status !== 409) {
    return false;
  }
  const cuerpo: unknown = err.error;
  if (cuerpo === null || typeof cuerpo !== 'object' || !('error' in cuerpo)) {
    return false;
  }
  const mensaje = (cuerpo as { error: unknown }).error;
  return typeof mensaje === 'string' && mensaje.includes(FRAGMENTO_CONFLICTO_VERSION);
}

/** Mensaje del backend si lo hay; si no, uno propio para 403 y 404 y, por último, el texto por defecto. */
export function mensajeDeAccion(err: unknown, porDefecto: string): string {
  const generico = mensajeDeError(err, '');
  if (generico !== '') {
    return generico;
  }
  if (err instanceof HttpErrorResponse) {
    if (err.status === 403) {
      return 'No tienes permiso para hacer esto.';
    }
    if (err.status === 404) {
      return 'La incidencia ya no existe o no tienes acceso a ella.';
    }
  }
  return porDefecto;
}
