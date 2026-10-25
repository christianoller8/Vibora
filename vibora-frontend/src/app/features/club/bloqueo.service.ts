import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  BloqueoCreado,
  BloqueoPista,
  BloqueoRequest,
  CancelacionAfectadas,
  ReservaAfectada,
} from '../../shared/models/bloqueo.model';

/**
 * Bloqueos de pista por mantenimiento. Crear, ver las afectadas, levantar y cancelarlas es solo del administrador del
 * club; la lista también la lee el personal de mantenimiento.
 */
@Injectable({ providedIn: 'root' })
export class BloqueoService {
  private readonly http = inject(HttpClient);

  private base(clubId: number, pistaId: number): string {
    return `/api/clubes/${clubId}/pistas/${pistaId}/bloqueos`;
  }

  /** `activos` es tri-estado: sin valor, todos; `true`, solo los activos; `false`, los levantados o caducados. */
  listar(clubId: number, pistaId: number, activos?: boolean): Observable<BloqueoPista[]> {
    let params = new HttpParams();
    if (activos !== undefined) {
      params = params.set('activos', String(activos));
    }
    return this.http.get<BloqueoPista[]>(this.base(clubId, pistaId), { params });
  }

  /**
   * No toca las reservas que ya existen: las devuelve como `afectadas`. 400 si `hasta` no es posterior a `desde` (o no
   * es futuro) o si la incidencia es de otra pista; 404 si la incidencia no es del club; 409 si ya tiene un bloqueo
   * activo o está cerrada/rechazada.
   */
  crear(clubId: number, pistaId: number, datos: BloqueoRequest): Observable<BloqueoCreado> {
    return this.http.post<BloqueoCreado>(this.base(clubId, pistaId), datos);
  }

  /** Las reservas confirmadas y futuras que el bloqueo afecta ahora (vacío si ya no está activo). */
  afectadas(clubId: number, pistaId: number, bloqueoId: number): Observable<ReservaAfectada[]> {
    return this.http.get<ReservaAfectada[]>(`${this.base(clubId, pistaId)}/${bloqueoId}/afectadas`);
  }

  /** Idempotente: levantar uno ya levantado responde 204 igualmente. */
  levantar(clubId: number, pistaId: number, bloqueoId: number): Observable<void> {
    return this.http.delete<void>(`${this.base(clubId, pistaId)}/${bloqueoId}`);
  }

  /** Repetirlo devuelve `canceladas: 0`; un bloqueo levantado o caducado responde 409. */
  cancelarAfectadas(
    clubId: number,
    pistaId: number,
    bloqueoId: number,
  ): Observable<CancelacionAfectadas> {
    return this.http.post<CancelacionAfectadas>(
      `${this.base(clubId, pistaId)}/${bloqueoId}/cancelar-afectadas`,
      null,
    );
  }

  /** Atajo desde una incidencia con pista: bloquea esa pista con la incidencia como motivo. 201 con las afectadas. */
  bloquearPistaDeIncidencia(incidenciaId: number): Observable<BloqueoCreado> {
    return this.http.post<BloqueoCreado>(`/api/incidencias/${incidenciaId}/bloquear-pista`, null);
  }
}
