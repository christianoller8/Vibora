import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { FotoIncidencia } from '../../shared/models/incidencia.model';

/**
 * Fotos de una incidencia. Los bytes exigen el JWT, que un `<img src>` no puede enviar: se piden con HttpClient
 * como blob (el interceptor añade el Bearer) y se muestran con una URL de objeto (ver FotoBlobCache).
 */
@Injectable({ providedIn: 'root' })
export class FotoIncidenciaService {
  private readonly http = inject(HttpClient);

  listar(incidenciaId: number): Observable<FotoIncidencia[]> {
    return this.http.get<FotoIncidencia[]>(`/api/incidencias/${incidenciaId}/fotos`);
  }

  /** Multipart con el campo `file`. El Content-Type (con su boundary) lo pone el navegador. */
  subir(incidenciaId: number, archivo: File): Observable<FotoIncidencia> {
    const datos = new FormData();
    datos.append('file', archivo, archivo.name);
    return this.http.post<FotoIncidencia>(`/api/incidencias/${incidenciaId}/fotos`, datos);
  }

  eliminar(incidenciaId: number, fotoId: number): Observable<void> {
    return this.http.delete<void>(`/api/incidencias/${incidenciaId}/fotos/${fotoId}`);
  }

  obtenerBlob(incidenciaId: number, fotoId: number): Observable<Blob> {
    return this.http.get(`/api/incidencias/${incidenciaId}/fotos/${fotoId}`, {
      responseType: 'blob',
    });
  }
}
