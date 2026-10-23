import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ListarNotificacionesOpciones, Notificacion } from '../../shared/models/notificacion.model';

@Injectable({ providedIn: 'root' })
export class NotificacionService {
  private readonly http = inject(HttpClient);

  listar(opciones: ListarNotificacionesOpciones = {}): Observable<Notificacion[]> {
    let params = new HttpParams();
    if (opciones.soloNoLeidas !== undefined) {
      params = params.set('soloNoLeidas', opciones.soloNoLeidas);
    }
    if (opciones.page !== undefined) {
      params = params.set('page', opciones.page);
    }
    if (opciones.limit !== undefined) {
      params = params.set('limit', opciones.limit);
    }
    return this.http.get<Notificacion[]>('/api/notificaciones', { params });
  }

  contarNoLeidas(): Observable<number> {
    return this.http
      .get<{ count: number }>('/api/notificaciones/no-leidas/count')
      .pipe(map((respuesta) => respuesta.count));
  }

  leer(id: number): Observable<Notificacion> {
    return this.http.post<Notificacion>(`/api/notificaciones/${id}/leer`, {});
  }

  /** Devuelve cuántas se marcaron. */
  leerTodas(): Observable<number> {
    return this.http
      .post<{ marcadas: number }>('/api/notificaciones/leer-todas', {})
      .pipe(map((respuesta) => respuesta.marcadas));
  }
}
