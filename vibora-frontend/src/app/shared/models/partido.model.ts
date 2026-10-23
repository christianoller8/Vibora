export interface JugadorPartido {
  usuarioId: number;
  nombre: string;
  /** Inicial del apellido en mayúscula ("" si no tiene). Nunca el apellido completo ni el email. */
  inicialApellido: string;
}

export interface Partido {
  reservaId: number;
  club: { id: number; nombre: string };
  pistaNombre: string;
  /** Instantes ISO-8601 (UTC). */
  inicio: string;
  fin: string;
  cuotaPorJugador: number;
  plazasLibres: number;
  jugadores: JugadorPartido[];
  yaApuntado: boolean;
  esOrganizador: boolean;
}

export interface AbiertaRequest {
  abierta: boolean;
}
