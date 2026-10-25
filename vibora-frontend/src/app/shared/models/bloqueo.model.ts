import { PersonaIncidencia } from './incidencia.model';

/** Bloqueo de una pista por mantenimiento (`GET .../pistas/{id}/bloqueos`). `hasta` nulo = sin fin. */
export interface BloqueoPista {
  id: number;
  pistaId: number;
  pistaNombre: string;
  /** Incidencia que lo motiva, si la hay. */
  incidenciaId: number | null;
  motivo: string;
  /** Instantes ISO-8601 (UTC). */
  desde: string;
  hasta: string | null;
  creadoPor: PersonaIncidencia;
  creadoEn: string;
  levantadoEn: string | null;
  levantadoPor: PersonaIncidencia | null;
  /** Lo calcula el servidor al responder: sin levantar y sin caducar en ese momento. */
  activo: boolean;
}

/** Estado visible de un bloqueo; ver `estadoBloqueo`. */
export type EstadoBloqueo = 'ACTIVO' | 'LEVANTADO' | 'CADUCADO';

export const ETIQUETA_ESTADO_BLOQUEO: Record<EstadoBloqueo, string> = {
  ACTIVO: 'Activo',
  LEVANTADO: 'Levantado',
  CADUCADO: 'Caducado',
};

/** Filtro de la lista: el parámetro `activos` del backend es tri-estado (ausente = todos). */
export type FiltroBloqueos = 'ACTIVOS' | 'LEVANTADOS' | 'TODOS';

export const FILTROS_BLOQUEOS: readonly FiltroBloqueos[] = ['ACTIVOS', 'LEVANTADOS', 'TODOS'];

export const ETIQUETA_FILTRO_BLOQUEOS: Record<FiltroBloqueos, string> = {
  ACTIVOS: 'Activos',
  LEVANTADOS: 'Levantados',
  TODOS: 'Todos',
};

/** Body de `POST .../bloqueos`: `desde` ausente = ahora, `hasta` ausente = sin fin (instantes ISO-8601). */
export interface BloqueoRequest {
  motivo: string;
  desde?: string;
  hasta?: string;
  incidenciaId?: number;
}

export const MAX_MOTIVO_BLOQUEO = 300;

/** Reserva confirmada y futura que un bloqueo deja "afectada". */
export interface ReservaAfectada {
  id: number;
  inicio: string;
  fin: string;
  organizador: { id: number; nombre: string; email: string };
  /** Jugadores apuntados (el organizador cuenta), de 4. */
  jugadores: PersonaIncidencia[];
}

/** Respuesta al crear un bloqueo: el bloqueo y las reservas que ya existían en su rango (no se tocan). */
export interface BloqueoCreado {
  bloqueo: BloqueoPista;
  afectadas: ReservaAfectada[];
}

export interface CancelacionAfectadas {
  canceladas: number;
  /** Ids de las reservas canceladas en esta llamada. */
  ids: number[];
}
