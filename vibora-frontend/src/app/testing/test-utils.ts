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
  clubId: null,
  clubNombre: null,
};

export const USUARIO_ADMIN: Usuario = {
  id: 9,
  email: 'admin@club.es',
  nombre: 'Marta',
  apellidos: 'Ruiz',
  rol: 'ADMIN_CLUB',
  clubId: 5,
  clubNombre: 'Club Víbora Demo',
};

/** Simula que el usuario escribe (o elige) un valor en un campo nativo y sale de él. */
export function escribir(
  campo: HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement,
  valor: string,
): void {
  campo.value = valor;
  campo.dispatchEvent(new Event('input', { bubbles: true }));
  campo.dispatchEvent(new Event('change', { bubbles: true }));
  campo.dispatchEvent(new Event('blur'));
}

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

/** Simula que el usuario elige archivos en un `<input type="file">` (jsdom no permite asignar `files`). */
export function elegirArchivos(campo: HTMLInputElement, archivos: File[]): void {
  Object.defineProperty(campo, 'files', { value: archivos, configurable: true });
  campo.dispatchEvent(new Event('change', { bubbles: true }));
}

/** Archivo de imagen de prueba (el contenido no importa: el navegador solo mira tipo y tamaño). */
export function crearImagen(nombre: string, tipo = 'image/png', bytes = 100): File {
  return new File([new Uint8Array(bytes)], nombre, { type: tipo });
}

/**
 * jsdom no implementa `URL.createObjectURL` / `revokeObjectURL`: los sustituye por dobles que numeran las URLs
 * ("blob:prueba/1", "blob:prueba/2"...). Hay que llamar a `restaurar()` al terminar cada test.
 */
export function simularObjectUrls() {
  let contador = 0;
  const crear = vi.fn((_objeto: Blob | MediaSource) => `blob:prueba/${++contador}`);
  const revocar = vi.fn((_url: string) => undefined);
  const crearOriginal = URL.createObjectURL;
  const revocarOriginal = URL.revokeObjectURL;
  URL.createObjectURL = crear;
  URL.revokeObjectURL = revocar;
  return {
    crear,
    revocar,
    restaurar: (): void => {
      URL.createObjectURL = crearOriginal;
      URL.revokeObjectURL = revocarOriginal;
    },
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
    rol: computed(() => usuarioSignal()?.rol ?? null),
    clubId: computed(() => usuarioSignal()?.clubId ?? null),
    clubNombre: computed(() => usuarioSignal()?.clubNombre ?? null),
    esAdminClub: computed(() => usuarioSignal()?.rol === 'ADMIN_CLUB'),
    puedeCrearClub: computed(
      () => usuarioSignal()?.rol === 'JUGADOR' && (usuarioSignal()?.clubId ?? null) === null,
    ),
    login: vi.fn(),
    registro: vi.fn(),
    logout: vi.fn(),
  };
}
