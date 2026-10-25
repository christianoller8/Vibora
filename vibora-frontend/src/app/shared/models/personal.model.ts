import { Rol } from './usuario.model';

/** Roles que se pueden dar de alta como personal del club (el administrador no se añade desde aquí). */
export type RolPersonal = Extract<Rol, 'MONITOR' | 'MANTENIMIENTO'>;

export const ROLES_PERSONAL: readonly RolPersonal[] = ['MONITOR', 'MANTENIMIENTO'];

/** Body de `POST /api/clubes/{id}/personal`. */
export interface PersonalRequest {
  email: string;
  rol: RolPersonal;
}
