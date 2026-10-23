import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Pista, PistaRequest } from '../../shared/models/pista.model';

/** Altas, cambios y bajas de pistas (solo el administrador del club). El listado vive en ClubService. */
@Injectable({ providedIn: 'root' })
export class PistaService {
  private readonly http = inject(HttpClient);

  crear(clubId: number, datos: PistaRequest): Observable<Pista> {
    return this.http.post<Pista>(`/api/clubes/${clubId}/pistas`, datos);
  }

  actualizar(clubId: number, id: number, datos: PistaRequest): Observable<Pista> {
    return this.http.put<Pista>(`/api/clubes/${clubId}/pistas/${id}`, datos);
  }

  /** Baja lógica (la pista queda inactiva): las reservas existentes la siguen referenciando. */
  darDeBaja(clubId: number, id: number): Observable<void> {
    return this.http.delete<void>(`/api/clubes/${clubId}/pistas/${id}`);
  }
}
