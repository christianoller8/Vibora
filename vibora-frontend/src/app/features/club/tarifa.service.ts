import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Tarifa, TarifaRequest } from '../../shared/models/tarifa.model';

/** Franjas tarifarias de un club. Escribir (POST/PUT/DELETE) solo puede el administrador del club. */
@Injectable({ providedIn: 'root' })
export class TarifaService {
  private readonly http = inject(HttpClient);

  listar(clubId: number): Observable<Tarifa[]> {
    return this.http.get<Tarifa[]>(`/api/clubes/${clubId}/tarifas`);
  }

  /** 409 si la franja se solapa con otra del mismo día. */
  crear(clubId: number, datos: TarifaRequest): Observable<Tarifa> {
    return this.http.post<Tarifa>(`/api/clubes/${clubId}/tarifas`, datos);
  }

  actualizar(clubId: number, id: number, datos: TarifaRequest): Observable<Tarifa> {
    return this.http.put<Tarifa>(`/api/clubes/${clubId}/tarifas/${id}`, datos);
  }

  eliminar(clubId: number, id: number): Observable<void> {
    return this.http.delete<void>(`/api/clubes/${clubId}/tarifas/${id}`);
  }
}
