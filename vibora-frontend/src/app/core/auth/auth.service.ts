import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, map, of, switchMap, tap, throwError } from 'rxjs';
import {
  LoginRequest,
  RegistroRequest,
  TokenResponse,
  Usuario,
} from '../../shared/models/usuario.model';
import { tokenCaducado } from './jwt.util';

export const CLAVE_TOKEN = 'vibora.token';

/*
 * El token vive en localStorage: sobrevive a recargas, pero cualquier XSS puede leerlo. Una cookie
 * httpOnly lo evitaría, pero exige que el backend la emita y proteger contra CSRF; hoy el backend es
 * un API stateless con Bearer. Se asume el trade-off mientras no haya contenido de terceros en la app
 * (y la CSP / el escapado de Angular reducen el riesgo de XSS).
 */
function leerTokenGuardado(): string | null {
  try {
    return localStorage.getItem(CLAVE_TOKEN);
  } catch {
    return null; // almacenamiento no disponible (modo privado estricto, etc.)
  }
}

function guardarToken(token: string | null): void {
  try {
    if (token === null) {
      localStorage.removeItem(CLAVE_TOKEN);
    } else {
      localStorage.setItem(CLAVE_TOKEN, token);
    }
  } catch {
    // sin almacenamiento: la sesión dura lo que dure la pestaña
  }
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  readonly token = signal<string | null>(null);
  readonly usuario = signal<Usuario | null>(null);
  readonly estaAutenticado = computed(() => this.token() !== null);
  readonly rol = computed(() => this.usuario()?.rol ?? null);

  constructor() {
    const guardado = leerTokenGuardado();
    if (guardado !== null && tokenCaducado(guardado)) {
      guardarToken(null); // caducado: ni siquiera preguntamos al backend
    } else {
      this.token.set(guardado);
    }
  }

  login(email: string, password: string): Observable<Usuario> {
    const peticion: LoginRequest = { email, password };
    return this.http.post<TokenResponse>('/api/auth/login', peticion).pipe(
      tap((respuesta) => {
        this.token.set(respuesta.token);
        guardarToken(respuesta.token);
      }),
      switchMap(() => this.http.get<Usuario>('/api/auth/me')),
      tap((usuario) => this.usuario.set(usuario)),
      catchError((err: unknown) => {
        this.limpiarSesion();
        return throwError(() => err);
      }),
    );
  }

  /** Crea la cuenta (siempre JUGADOR). No abre sesión: el llamante hace login a continuación. */
  registro(datos: RegistroRequest): Observable<Usuario> {
    return this.http.post<Usuario>('/api/auth/registro', datos);
  }

  logout(): void {
    this.limpiarSesion();
  }

  /** Para el arranque: si hay token vigente recupera el usuario; nunca falla (sin sesión devuelve null). */
  cargarUsuario(): Observable<Usuario | null> {
    if (this.token() === null) {
      return of(null);
    }
    return this.http.get<Usuario>('/api/auth/me').pipe(
      tap((usuario) => this.usuario.set(usuario)),
      map((usuario): Usuario | null => usuario),
      catchError(() => {
        this.limpiarSesion();
        return of(null);
      }),
    );
  }

  private limpiarSesion(): void {
    this.token.set(null);
    this.usuario.set(null);
    guardarToken(null);
  }
}
