import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { PistaService } from './pista.service';

describe('PistaService', () => {
  let servicio: PistaService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(PistaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('crea una pista con POST /api/clubes/{club}/pistas', async () => {
    const resultado = firstValueFrom(servicio.crear(5, { nombre: 'Pista 3', tipo: 'EXTERIOR' }));
    const req = http.expectOne('/api/clubes/5/pistas');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ nombre: 'Pista 3', tipo: 'EXTERIOR' });
    req.flush(
      { id: 11, nombre: 'Pista 3', tipo: 'EXTERIOR', activa: true, clubId: 5 },
      { status: 201, statusText: 'Created' },
    );
    expect((await resultado).id).toBe(11);
  });

  it('actualiza una pista con PUT, incluyendo "activa" cuando se cambia', async () => {
    const resultado = firstValueFrom(
      servicio.actualizar(5, 11, { nombre: 'Pista 3', tipo: 'CUBIERTA', activa: false }),
    );
    const req = http.expectOne('/api/clubes/5/pistas/11');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ nombre: 'Pista 3', tipo: 'CUBIERTA', activa: false });
    req.flush({ id: 11, nombre: 'Pista 3', tipo: 'CUBIERTA', activa: false, clubId: 5 });
    expect((await resultado).activa).toBe(false);
  });

  it('da de baja una pista con DELETE (baja lógica, 204 sin cuerpo)', async () => {
    const resultado = firstValueFrom(servicio.darDeBaja(5, 11), { defaultValue: undefined });
    const req = http.expectOne('/api/clubes/5/pistas/11');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
    await expect(resultado).resolves.toBeNull();
  });

  it('propaga el 409 de nombre duplicado', async () => {
    const resultado = firstValueFrom(servicio.crear(5, { nombre: 'Pista 1', tipo: 'INTERIOR' }));
    http
      .expectOne('/api/clubes/5/pistas')
      .flush(
        { error: 'Ya existe una pista con ese nombre en el club' },
        { status: 409, statusText: 'Conflict' },
      );
    await expect(resultado).rejects.toMatchObject({ status: 409 });
  });
});
