import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AsignarIncidenciaRequest,
  CambioEstadoRequest,
  FiltrosIncidencias,
  IncidenciaDetalle,
  IncidenciaRequest,
  IncidenciaResumen,
} from '../../shared/models/incidencia.model';

/**
 * Incidencias de un club. El backend decide la visibilidad: el administrador ve todas, mantenimiento las que
 * tiene asignadas y el resto las que reportó (quien no tiene ninguna recibe una lista vacía, no un 403).
 */
@Injectable({ providedIn: 'root' })
export class IncidenciaService {
  private readonly http = inject(HttpClient);

  /** Orden del servidor: urgentes primero y luego las más nuevas. `page` empieza en 0. */
  listar(clubId: number, filtros: FiltrosIncidencias = {}): Observable<IncidenciaResumen[]> {
    let params = new HttpParams();
    for (const [clave, valor] of Object.entries(filtros)) {
      if (valor !== undefined) {
        params = params.set(clave, String(valor));
      }
    }
    return this.http.get<IncidenciaResumen[]>(`/api/clubes/${clubId}/incidencias`, { params });
  }

  obtener(id: number): Observable<IncidenciaDetalle> {
    return this.http.get<IncidenciaDetalle>(`/api/incidencias/${id}`);
  }

  /** Nace ABIERTA y con prioridad MEDIA. 403 sin vínculo con el club; 409 con 5 incidencias sin cerrar. */
  crear(clubId: number, datos: IncidenciaRequest): Observable<IncidenciaDetalle> {
    return this.http.post<IncidenciaDetalle>(`/api/clubes/${clubId}/incidencias`, datos);
  }

  /** Solo el administrador. Desde ABIERTA, ASIGNADA o EN_CURSO; `prioridad` es opcional. */
  asignar(id: number, datos: AsignarIncidenciaRequest): Observable<IncidenciaDetalle> {
    return this.http.post<IncidenciaDetalle>(`/api/incidencias/${id}/asignar`, datos);
  }

  cambiarEstado(id: number, datos: CambioEstadoRequest): Observable<IncidenciaDetalle> {
    return this.http.post<IncidenciaDetalle>(`/api/incidencias/${id}/estado`, datos);
  }

  comentar(id: number, texto: string): Observable<IncidenciaDetalle> {
    return this.http.post<IncidenciaDetalle>(`/api/incidencias/${id}/comentarios`, { texto });
  }
}
