import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Club } from '../../shared/models/club.model';
import { Pista } from '../../shared/models/pista.model';

@Injectable({ providedIn: 'root' })
export class ClubService {
  private readonly http = inject(HttpClient);

  listar(): Observable<Club[]> {
    return this.http.get<Club[]>('/api/clubes');
  }

  pistas(clubId: number): Observable<Pista[]> {
    return this.http.get<Pista[]>(`/api/clubes/${clubId}/pistas`);
  }
}
