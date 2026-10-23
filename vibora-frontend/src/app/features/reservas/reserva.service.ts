import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CasillaAgenda,
  Reserva,
  ReservaClub,
  ReservaRequest,
} from '../../shared/models/reserva.model';

@Injectable({ providedIn: 'root' })
export class ReservaService {
  private readonly http = inject(HttpClient);

  /** `fecha` es YYYY-MM-DD (día del club, en Madrid). */
  agenda(clubId: number, pistaId: number, fecha: string): Observable<CasillaAgenda[]> {
    return this.http.get<CasillaAgenda[]>(`/api/clubes/${clubId}/pistas/${pistaId}/agenda`, {
      params: new HttpParams().set('fecha', fecha),
    });
  }

  crear(clubId: number, pistaId: number, inicio: string): Observable<Reserva> {
    const cuerpo: ReservaRequest = { inicio };
    return this.http.post<Reserva>(`/api/clubes/${clubId}/pistas/${pistaId}/reservas`, cuerpo);
  }

  mias(): Observable<Reserva[]> {
    return this.http.get<Reserva[]>('/api/reservas/mias');
  }

  /** Reservas de un club en un día (YYYY-MM-DD, Madrid). Solo el administrador de ese club. */
  delClub(clubId: number, fecha: string): Observable<ReservaClub[]> {
    return this.http.get<ReservaClub[]>(`/api/clubes/${clubId}/reservas`, {
      params: new HttpParams().set('fecha', fecha),
    });
  }

  /** El dueño cancela hasta el plazo; el administrador del club puede cancelar cualquiera antes de que empiece. */
  cancelar(id: number): Observable<Reserva> {
    return this.http.post<Reserva>(`/api/reservas/${id}/cancelar`, {});
  }
}
