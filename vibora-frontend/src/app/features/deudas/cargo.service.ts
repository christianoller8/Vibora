import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Cargo, EstadoCargo, Saldos } from '../../shared/models/cargo.model';

@Injectable({ providedIn: 'root' })
export class CargoService {
  private readonly http = inject(HttpClient);

  /** Lo que yo debo. */
  mios(estado?: EstadoCargo): Observable<Cargo[]> {
    return this.http.get<Cargo[]>('/api/cargos/mios', { params: this.params(estado) });
  }

  /** Lo que otros me deben. */
  porCobrar(estado?: EstadoCargo): Observable<Cargo[]> {
    return this.http.get<Cargo[]>('/api/cargos/por-cobrar', { params: this.params(estado) });
  }

  /** Pago simulado de un cargo propio. */
  pagar(id: number): Observable<Cargo> {
    return this.http.post<Cargo>(`/api/cargos/${id}/pagar`, {});
  }

  saldos(): Observable<Saldos> {
    return this.http.get<Saldos>('/api/saldos');
  }

  private params(estado?: EstadoCargo): HttpParams {
    return estado ? new HttpParams().set('estado', estado) : new HttpParams();
  }
}
