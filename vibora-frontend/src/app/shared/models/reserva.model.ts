export type EstadoReserva = 'CONFIRMADA' | 'CANCELADA';

export interface Reserva {
  id: number;
  pistaId: number;
  pistaNombre: string;
  clubId: number;
  clubNombre: string;
  /** Instante ISO-8601 (UTC). */
  inicio: string;
  fin: string;
  estado: EstadoReserva;
  precioTotal: number;
  /** true si es un partido abierto: otros jugadores pueden apuntarse. */
  abierta: boolean;
  /** Jugadores apuntados (el organizador cuenta), de 4. */
  jugadores: number;
  cuotaPorJugador: number;
}

/** Reserva vista por el administrador del club, con un resumen de quien reservó. */
export interface ReservaClub {
  id: number;
  pistaId: number;
  pistaNombre: string;
  inicio: string;
  fin: string;
  estado: EstadoReserva;
  precioTotal: number;
  usuario: UsuarioReserva;
}

export interface UsuarioReserva {
  id: number;
  nombre: string | null;
  apellidos: string;
  email: string;
}

/** Casilla de la agenda de una pista; precio es null si ninguna tarifa la cubre. */
export interface CasillaAgenda {
  inicio: string;
  fin: string;
  disponible: boolean;
  precio: number | null;
  /**
   * La casilla solapa un bloqueo de mantenimiento activo (y entonces `disponible` es false). El backend siempre
   * lo envía; se tolera su ausencia (un backend anterior) como "no bloqueada".
   */
  bloqueada?: boolean;
}

export interface ReservaRequest {
  inicio: string;
}
