import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AbiertaRequest, Partido } from '../../shared/models/partido.model';
import { Reserva } from '../../shared/models/reserva.model';

export interface FiltrosPartidos {
  clubId?: number | null;
  /** YYYY-MM-DD (día del club, en Madrid). */
  fecha?: string | null;
}

@Injectable({ providedIn: 'root' })
export class PartidoService {
  private readonly http = inject(HttpClient);

  listar(filtros: FiltrosPartidos = {}): Observable<Partido[]> {
    let params = new HttpParams();
    if (filtros.clubId !== undefined && filtros.clubId !== null) {
      params = params.set('clubId', filtros.clubId);
    }
    if (filtros.fecha) {
      params = params.set('fecha', filtros.fecha);
    }
    return this.http.get<Partido[]>('/api/partidos', { params });
  }

  apuntarse(reservaId: number): Observable<Partido> {
    return this.http.post<Partido>(`/api/reservas/${reservaId}/apuntarse`, {});
  }

  salir(reservaId: number): Observable<Partido> {
    return this.http.post<Partido>(`/api/reservas/${reservaId}/salir`, {});
  }

  /** Abre o cierra una reserva propia a otros jugadores. */
  setAbierta(reservaId: number, abierta: boolean): Observable<Reserva> {
    const cuerpo: AbiertaRequest = { abierta };
    return this.http.put<Reserva>(`/api/reservas/${reservaId}/abierta`, cuerpo);
  }
}
