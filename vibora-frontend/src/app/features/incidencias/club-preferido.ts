import { Club } from '../../shared/models/club.model';

export const CLAVE_CLUB_INCIDENCIAS = 'vibora.incidencias.club';

/** Último club elegido en incidencias; null si no hay, está corrupto o el almacenamiento no está disponible. */
export function leerClubPreferido(): number | null {
  try {
    const valor = localStorage.getItem(CLAVE_CLUB_INCIDENCIAS);
    if (valor === null || !/^[1-9]\d*$/.test(valor)) {
      return null;
    }
    return Number(valor);
  } catch {
    return null;
  }
}

export function guardarClubPreferido(id: number): void {
  try {
    localStorage.setItem(CLAVE_CLUB_INCIDENCIAS, String(id));
  } catch {
    // sin almacenamiento: la elección dura lo que dure la pantalla
  }
}

/** Club propio si existe en la lista; si no, el último elegido; si no, el primero. */
export function elegirClubInicial(
  clubes: readonly Club[],
  clubIdUsuario: number | null,
  preferido: number | null,
): number | null {
  const existe = (id: number | null): id is number =>
    id !== null && clubes.some((c) => c.id === id);
  if (existe(clubIdUsuario)) {
    return clubIdUsuario;
  }
  if (existe(preferido)) {
    return preferido;
  }
  return clubes[0]?.id ?? null;
}
