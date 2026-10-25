import {
  MAX_FOTOS_INCIDENCIA,
  TAMANO_MAX_FOTO,
  TIPOS_FOTO_PERMITIDOS,
} from '../../shared/models/incidencia.model';

export interface ResultadoValidacionFotos {
  aceptadas: File[];
  /** Un mensaje por cada archivo rechazado, nombrándolo. */
  errores: string[];
}

/**
 * Comprobaciones previas a enviar las fotos (el backend las repite y es quien manda: mira los bytes, no el tipo
 * que declara el navegador). `actuales` son las fotos que la incidencia ya tiene o que ya se han elegido.
 */
export function validarFotos(actuales: number, nuevas: readonly File[]): ResultadoValidacionFotos {
  const aceptadas: File[] = [];
  const errores: string[] = [];

  for (const archivo of nuevas) {
    if (!TIPOS_FOTO_PERMITIDOS.includes(archivo.type)) {
      errores.push(`«${archivo.name}» no es una imagen JPEG, PNG o WebP.`);
    } else if (archivo.size === 0) {
      errores.push(`«${archivo.name}» está vacío.`);
    } else if (archivo.size > TAMANO_MAX_FOTO) {
      errores.push(`«${archivo.name}» pesa más de 5 MB.`);
    } else if (actuales + aceptadas.length >= MAX_FOTOS_INCIDENCIA) {
      errores.push(
        `«${archivo.name}» no se ha añadido: una incidencia admite como máximo ${MAX_FOTOS_INCIDENCIA} fotos.`,
      );
    } else {
      aceptadas.push(archivo);
    }
  }
  return { aceptadas, errores };
}
