export type TipoNotificacion =
  | 'JUGADOR_SE_APUNTO'
  | 'JUGADOR_SALIO'
  | 'PARTIDO_COMPLETO'
  | 'CARGO_NUEVO'
  | 'PAGO_RECIBIDO'
  | 'RESERVA_CANCELADA'
  | 'PERSONAL_ANADIDO'
  | 'PERSONAL_QUITADO'
  | 'INCIDENCIA_NUEVA'
  | 'INCIDENCIA_ASIGNADA'
  | 'INCIDENCIA_ESTADO'
  | 'INCIDENCIA_COMENTARIO';

export interface Notificacion {
  id: number;
  tipo: TipoNotificacion;
  mensaje: string;
  reservaId: number | null;
  leida: boolean;
  /** Instante ISO-8601 (UTC). */
  creadaEn: string;
}

export interface ListarNotificacionesOpciones {
  soloNoLeidas?: boolean;
  page?: number;
  limit?: number;
}
