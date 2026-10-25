import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { FotoIncidenciaService } from './foto-incidencia.service';

describe('FotoIncidenciaService', () => {
  let servicio: FotoIncidenciaService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(FotoIncidenciaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista con GET /api/incidencias/{id}/fotos', async () => {
    const resultado = firstValueFrom(servicio.listar(7));
    const req = http.expectOne('/api/incidencias/7/fotos');
    expect(req.request.method).toBe('GET');
    req.flush([]);
    expect(await resultado).toEqual([]);
  });

  it('sube con POST multipart: FormData con el campo "file" y el archivo', async () => {
    const archivo = new File([new Uint8Array([1, 2, 3])], 'foco.png', { type: 'image/png' });
    const resultado = firstValueFrom(servicio.subir(7, archivo));
    const req = http.expectOne('/api/incidencias/7/fotos');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBeInstanceOf(FormData);
    const cuerpo = req.request.body as FormData;
    expect(Array.from(cuerpo.keys())).toEqual(['file']);
    const enviado = cuerpo.get('file') as File;
    expect(enviado.name).toBe('foco.png');
    expect(enviado.type).toBe('image/png');
    // el Content-Type multipart (con su boundary) lo pone el navegador: no se fija a mano
    expect(req.request.headers.has('Content-Type')).toBe(false);
    req.flush({ id: 3 });
    expect(await resultado).toMatchObject({ id: 3 });
  });

  it('elimina con DELETE /api/incidencias/{id}/fotos/{fotoId}', async () => {
    const resultado = firstValueFrom(servicio.eliminar(7, 3));
    const req = http.expectOne('/api/incidencias/7/fotos/3');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
    await resultado;
  });

  it('obtiene los bytes con GET .../fotos/{fotoId} como blob', async () => {
    const resultado = firstValueFrom(servicio.obtenerBlob(7, 3));
    const req = http.expectOne('/api/incidencias/7/fotos/3');
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    const blob = new Blob([new Uint8Array([1, 2, 3])], { type: 'image/png' });
    req.flush(blob);
    expect(await resultado).toBe(blob);
  });
});
