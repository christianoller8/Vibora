import { BloqueoPista, EstadoBloqueo } from '../../../shared/models/bloqueo.model';

/**
 * Estado visible de un bloqueo en el instante `ahora` (ms), con la misma regla que el backend: activo = sin levantar y
 * sin caducar (`hasta` nulo o posterior a ahora). Levantado gana a caducado.
 */
export function estadoBloqueo(
  bloqueo: Pick<BloqueoPista, 'levantadoEn' | 'hasta'>,
  ahora: number = Date.now(),
): EstadoBloqueo {
  if (bloqueo.levantadoEn !== null) {
    return 'LEVANTADO';
  }
  if (bloqueo.hasta !== null && Date.parse(bloqueo.hasta) <= ahora) {
    return 'CADUCADO';
  }
  return 'ACTIVO';
}
