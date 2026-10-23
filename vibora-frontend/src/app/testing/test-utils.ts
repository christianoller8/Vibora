import { computed, signal } from '@angular/core';
import { vi } from 'vitest';
import { Notificacion } from '../shared/models/notificacion.model';
import { Usuario } from '../shared/models/usuario.model';

/** Utilidades solo para los tests (no se importan desde código de la aplicación). */

/** Crea un JWT sin firma válida: solo importa el payload (`exp` en segundos). */
export function crearJwt(expSegundos: number): string {
  const base64Url = (obj: object): string =>
    btoa(JSON.stringify(obj)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${base64Url({ alg: 'HS256', typ: 'JWT' })}.${base64Url({ sub: 'ana@vibora.es', exp: expSegundos })}.firma`;
}

export function segundosDesdeAhora(delta: number): number {
  return Math.floor(Date.now() / 1000) + delta;
}

export const USUARIO_JUGADOR: Usuario = {
  id: 1,
  email: 'ana@vibora.es',
  nombre: 'Ana',
  apellidos: 'García López',
  rol: 'JUGADOR',
};

export function crearNotificacion(id: number, cambios: Partial<Notificacion> = {}): Notificacion {
  return {
    id,
    tipo: 'JUGADOR_SE_APUNTO',
    mensaje: `Aviso ${id}`,
    reservaId: 7,
    leida: false,
    creadaEn: new Date(Date.now() - id * 60_000).toISOString(),
    ...cambios,
  };
}

/** Doble de AuthService con signals, para los tests de componentes. */
export function crearAuthFalso(usuario: Usuario | null = USUARIO_JUGADOR) {
  const usuarioSignal = signal<Usuario | null>(usuario);
  const token = signal<string | null>(usuario === null ? null : 'jwt-de-prueba');
  return {
    token,
    usuario: usuarioSignal,
    estaAutenticado: computed(() => token() !== null),
    rol: signal(usuario?.rol ?? null),
    login: vi.fn(),
    registro: vi.fn(),
    logout: vi.fn(),
  };
}
