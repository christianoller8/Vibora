export type Rol = 'JUGADOR' | 'MONITOR' | 'MANTENIMIENTO' | 'ADMIN_CLUB';

export interface Usuario {
  id: number;
  email: string;
  nombre: string | null;
  apellidos: string;
  rol: Rol;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegistroRequest {
  email: string;
  password: string;
  nombre?: string | null;
  apellidos: string;
}

export interface TokenResponse {
  token: string;
  tipo: string;
  expiraEn: string;
}

/** Texto visible de cada rol. */
export const ETIQUETA_ROL: Record<Rol, string> = {
  JUGADOR: 'Jugador',
  MONITOR: 'Monitor',
  MANTENIMIENTO: 'Mantenimiento',
  ADMIN_CLUB: 'Admin de club',
};
