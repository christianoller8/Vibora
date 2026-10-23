import { HttpErrorResponse } from '@angular/common/http';

/**
 * Mensaje legible para el usuario a partir de un error HTTP. El backend responde con `{"error": "..."}`;
 * si no hay mensaje utilizable (HTML de un proxy, error de red, etc.) se usa el texto por defecto.
 */
export function mensajeDeError(err: unknown, porDefecto: string): string {
  if (!(err instanceof HttpErrorResponse)) {
    return porDefecto;
  }
  if (err.status === 0) {
    return 'No se pudo conectar con el servidor. Inténtalo de nuevo.';
  }
  const cuerpo: unknown = err.error;
  if (cuerpo !== null && typeof cuerpo === 'object' && 'error' in cuerpo) {
    const mensaje = (cuerpo as { error: unknown }).error;
    if (typeof mensaje === 'string' && mensaje.trim() !== '') {
      return mensaje;
    }
  }
  return porDefecto;
}
