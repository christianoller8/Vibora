import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { TarifaRequest } from '../../shared/models/tarifa.model';
import { TarifaService } from './tarifa.service';

const PETICION: TarifaRequest = {
  diaSemana: 'MONDAY',
  horaInicio: '09:00',
  horaFin: '14:00',
  precioHora: 12.5,
  tipo: 'NORMAL',
};

describe('TarifaService', () => {
  let servicio: TarifaService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(TarifaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista las franjas con GET /api/clubes/{club}/tarifas', async () => {
    const resultado = firstValueFrom(servicio.listar(5));
    const req = http.expectOne('/api/clubes/5/tarifas');
    expect(req.request.method).toBe('GET');
    req.flush([{ id: 1, clubId: 5, ...PETICION }]);
    expect(await resultado).toHaveLength(1);
  });

  it('crea una franja con POST y el día en inglés (enum del backend)', async () => {
    const resultado = firstValueFrom(servicio.crear(5, PETICION));
    const req = http.expectOne('/api/clubes/5/tarifas');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(PETICION);
    req.flush({ id: 2, clubId: 5, ...PETICION }, { status: 201, statusText: 'Created' });
    expect((await resultado).id).toBe(2);
  });

  it('actualiza una franja con PUT /api/clubes/{club}/tarifas/{id}', async () => {
    const resultado = firstValueFrom(servicio.actualizar(5, 2, { ...PETICION, precioHora: 15 }));
    const req = http.expectOne('/api/clubes/5/tarifas/2');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ ...PETICION, precioHora: 15 });
    req.flush({ id: 2, clubId: 5, ...PETICION, precioHora: 15 });
    expect((await resultado).precioHora).toBe(15);
  });

  it('elimina una franja con DELETE', async () => {
    const resultado = firstValueFrom(servicio.eliminar(5, 2), { defaultValue: undefined });
    const req = http.expectOne('/api/clubes/5/tarifas/2');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
    await expect(resultado).resolves.toBeNull();
  });

  it('propaga el 409 de solape', async () => {
    const resultado = firstValueFrom(servicio.crear(5, PETICION));
    http
      .expectOne('/api/clubes/5/tarifas')
      .flush(
        { error: 'La franja se solapa con otra del mismo día' },
        { status: 409, statusText: 'Conflict' },
      );
    await expect(resultado).rejects.toMatchObject({ status: 409 });
  });
});
