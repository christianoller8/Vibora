import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { MiembroPersonal } from '../../shared/models/incidencia.model';
import { PersonalRequest } from '../../shared/models/personal.model';

/** Personal de un club: solo lo gestiona su administrador. */
@Injectable({ providedIn: 'root' })
export class PersonalService {
  private readonly http = inject(HttpClient);

  /** Administradores del club (primero) y su personal. */
  listar(clubId: number): Observable<MiembroPersonal[]> {
    return this.http.get<MiembroPersonal[]>(`/api/clubes/${clubId}/personal`);
  }

  /**
   * Solo MONITOR o MANTENIMIENTO. 404 si no existe ningún usuario con ese email; 409 si ya pertenece a un club. Quien
   * se añade debe volver a iniciar sesión: su token actual sigue diciendo JUGADOR.
   */
  agregar(clubId: number, datos: PersonalRequest): Observable<MiembroPersonal> {
    return this.http.post<MiembroPersonal>(`/api/clubes/${clubId}/personal`, datos);
  }

  /**
   * 404 si no es personal de este club; 409 si es administrador. Sus incidencias activas quedan sin asignar y debe
   * volver a iniciar sesión.
   */
  quitar(clubId: number, usuarioId: number): Observable<void> {
    return this.http.delete<void>(`/api/clubes/${clubId}/personal/${usuarioId}`);
  }
}
