import { Injectable, effect, inject, signal, untracked } from '@angular/core';
import { Observable, Subject, forkJoin } from 'rxjs';
import { Notificacion } from '../../shared/models/notificacion.model';
import { AuthService } from '../auth/auth.service';
import { NotificacionService } from './notificacion.service';

/** Cuántas notificaciones recientes se mantienen en memoria (las que cabe ver en la campana). */
export const MAX_NOTIFICACIONES = 20;

/**
 * Estado de las notificaciones del usuario: últimas {@link MAX_NOTIFICACIONES} y contador de no leídas.
 * La verdad vive en el backend (REST); el push por WebSocket solo la adelanta, por eso al (re)conectar se
 * vuelve a pedir todo con {@link recargar}.
 */
@Injectable({ providedIn: 'root' })
export class NotificacionesStore {
  private readonly auth = inject(AuthService);
  private readonly api = inject(NotificacionService);

  private readonly _items = signal<Notificacion[]>([]);
  private readonly _noLeidas = signal(0);
  readonly items = this._items.asReadonly();
  readonly noLeidas = this._noLeidas.asReadonly();

  private readonly _recibidas = new Subject<Notificacion>();
  /**
   * Cada notificación empujada por el servidor (no las cargadas por REST). Las páginas se suscriben para
   * refrescar sus datos cuando algo cambia; hay que cancelar la suscripción al destruirse.
   */
  readonly recibidas$: Observable<Notificacion> = this._recibidas.asObservable();

  /** Cambia con cada inicio/cierre de sesión: descarta respuestas HTTP de una sesión anterior. */
  private generacion = 0;

  constructor() {
    effect(() => {
      const token = this.auth.token();
      untracked(() => {
        this.generacion++;
        if (token === null) {
          this._items.set([]);
          this._noLeidas.set(0);
        } else {
          this.recargar();
        }
      });
    });
  }

  /** Vuelve a pedir la lista y el contador al backend y sustituye lo que hay. */
  recargar(): void {
    const generacion = this.generacion;
    forkJoin({
      items: this.api.listar({ limit: MAX_NOTIFICACIONES }),
      noLeidas: this.api.contarNoLeidas(),
    }).subscribe({
      next: ({ items, noLeidas }) => {
        if (generacion !== this.generacion) {
          return;
        }
        this._items.set(items);
        this._noLeidas.set(noLeidas);
      },
      error: () => {
        // sin red o backend caído: se conserva lo que había; la próxima (re)conexión reintenta
      },
    });
  }

  /** Notificación empujada por el servidor: va la primera; si ya la teníamos no se cuenta dos veces. */
  recibir(notificacion: Notificacion): void {
    this._recibidas.next(notificacion);
    if (this._items().some((n) => n.id === notificacion.id)) {
      return;
    }
    this._items.update((items) => [notificacion, ...items].slice(0, MAX_NOTIFICACIONES));
    if (!notificacion.leida) {
      this._noLeidas.update((n) => n + 1);
    }
  }

  marcarLeida(id: number): void {
    const antes = this._items();
    const contadorAntes = this._noLeidas();
    const objetivo = antes.find((n) => n.id === id);
    if (!objetivo || objetivo.leida) {
      return;
    }
    const generacion = this.generacion;

    this._items.set(antes.map((n) => (n.id === id ? { ...n, leida: true } : n)));
    this._noLeidas.set(Math.max(0, contadorAntes - 1));

    this.api.leer(id).subscribe({
      error: () => {
        if (generacion === this.generacion) {
          this._items.set(antes);
          this._noLeidas.set(contadorAntes);
        }
      },
    });
  }

  marcarTodas(): void {
    const antes = this._items();
    const contadorAntes = this._noLeidas();
    const generacion = this.generacion;

    this._items.set(antes.map((n) => ({ ...n, leida: true })));
    this._noLeidas.set(0);

    this.api.leerTodas().subscribe({
      error: () => {
        if (generacion === this.generacion) {
          this._items.set(antes);
          this._noLeidas.set(contadorAntes);
        }
      },
    });
  }
}
