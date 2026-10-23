import { DOCUMENT } from '@angular/common';
import { Injectable, effect, inject, untracked } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { Notificacion } from '../../shared/models/notificacion.model';
import { AuthService } from '../auth/auth.service';
import { ClienteTiempoReal, FABRICA_CLIENTE_TIEMPO_REAL } from './cliente-tiempo-real';
import { destinoNotificacion } from './notificacion-destino';
import { NotificacionesStore } from './notificaciones.store';

const DESTINO_NOTIFICACIONES = '/user/queue/notificaciones';
/** Mensaje del frame ERROR cuando el backend rechaza el JWT del CONNECT. */
const ERROR_NO_AUTENTICADO = 'No autenticado';

/**
 * Mantiene la conexión en tiempo real mientras haya sesión: conecta al iniciar sesión, desconecta al cerrarla
 * y entrega cada notificación empujada al store (más un aviso con acción "Ver").
 *
 * Si el servidor rechaza el token ("No autenticado") la sesión se da por caducada: se cierra y se vuelve al
 * login en vez de reintentar con un token que no va a funcionar. Una conexión ya abierta no se revalida
 * cuando caduca el token; eso lo detecta la primera llamada REST (interceptor) o la siguiente reconexión.
 */
@Injectable({ providedIn: 'root' })
export class TiempoRealService {
  private readonly auth = inject(AuthService);
  private readonly store = inject(NotificacionesStore);
  private readonly router = inject(Router);
  private readonly snack = inject(MatSnackBar);
  private readonly fabrica = inject(FABRICA_CLIENTE_TIEMPO_REAL);
  private readonly location = inject(DOCUMENT).location;

  private cliente: ClienteTiempoReal | null = null;
  /** Identifica la conexión vigente: los eventos de una anterior se ignoran. */
  private generacion = 0;

  constructor() {
    effect(() => {
      const token = this.auth.token();
      untracked(() => {
        this.desconectar();
        if (token !== null) {
          this.conectar(token);
        }
      });
    });
  }

  private conectar(token: string): void {
    const generacion = ++this.generacion;
    const vigente = (): boolean => generacion === this.generacion;
    const protocolo = this.location.protocol === 'https:' ? 'wss' : 'ws';

    this.fabrica({
      url: `${protocolo}://${this.location.host}/ws`,
      cabeceras: { Authorization: `Bearer ${token}` },
      destino: DESTINO_NOTIFICACIONES,
      alConectar: () => {
        if (vigente()) {
          // mientras estuvimos desconectados pudo llegar algo que no se empujó
          this.store.recargar();
        }
      },
      alMensaje: (cuerpo) => {
        if (vigente()) {
          this.alRecibir(cuerpo);
        }
      },
      alErrorStomp: (mensaje) => {
        if (vigente() && mensaje.includes(ERROR_NO_AUTENTICADO)) {
          this.sesionCaducada();
        }
      },
    }).then(
      (cliente) => {
        if (!vigente()) {
          void cliente.desactivar(); // la sesión cambió mientras se cargaba la librería
          return;
        }
        this.cliente = cliente;
        cliente.activar();
      },
      () => {
        // la librería no se pudo cargar (sin red): la campana sigue funcionando por REST
      },
    );
  }

  private desconectar(): void {
    this.generacion++;
    const cliente = this.cliente;
    this.cliente = null;
    if (cliente) {
      void cliente.desactivar();
    }
  }

  private alRecibir(cuerpo: string): void {
    const notificacion = leerNotificacion(cuerpo);
    if (!notificacion) {
      return;
    }
    this.store.recibir(notificacion);
    this.snack
      .open(notificacion.mensaje, 'Ver', { duration: 8000 })
      .onAction()
      .subscribe(() => {
        void this.router.navigateByUrl(destinoNotificacion(notificacion.tipo));
        this.store.marcarLeida(notificacion.id);
      });
  }

  private sesionCaducada(): void {
    this.desconectar(); // sin reconexión: el token es inválido
    this.auth.logout();
    const destino = this.router.url;
    const queryParams =
      destino.startsWith('/login') || destino === '/' ? {} : { redirectTo: destino };
    void this.router.navigate(['/login'], { queryParams });
  }
}

/** Valida lo justo del JSON recibido: lo que no tenga forma de notificación se descarta. */
function leerNotificacion(cuerpo: string): Notificacion | null {
  let datos: unknown;
  try {
    datos = JSON.parse(cuerpo);
  } catch {
    return null;
  }
  if (datos === null || typeof datos !== 'object') {
    return null;
  }
  const candidata = datos as Partial<Notificacion>;
  if (
    typeof candidata.id !== 'number' ||
    typeof candidata.tipo !== 'string' ||
    typeof candidata.mensaje !== 'string'
  ) {
    return null;
  }
  return candidata as Notificacion;
}
