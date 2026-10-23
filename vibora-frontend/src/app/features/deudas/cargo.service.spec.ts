import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { CargoService } from './cargo.service';

describe('CargoService', () => {
  let servicio: CargoService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(CargoService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista lo que debo con GET /api/cargos/mios', async () => {
    const resultado = firstValueFrom(servicio.mios());
    const req = http.expectOne('/api/cargos/mios');
    expect(req.request.method).toBe('GET');
    req.flush([]);
    expect(await resultado).toEqual([]);
  });

  it('filtra lo que debo por estado', async () => {
    const resultado = firstValueFrom(servicio.mios('PENDIENTE'));
    const req = http.expectOne((r) => r.url === '/api/cargos/mios');
    expect(req.request.params.get('estado')).toBe('PENDIENTE');
    req.flush([]);
    await resultado;
  });

  it('lista lo que me deben con GET /api/cargos/por-cobrar (y estado opcional)', async () => {
    const todos = firstValueFrom(servicio.porCobrar());
    http.expectOne('/api/cargos/por-cobrar').flush([]);
    await todos;

    const pendientes = firstValueFrom(servicio.porCobrar('PENDIENTE'));
    const req = http.expectOne((r) => r.url === '/api/cargos/por-cobrar');
    expect(req.request.params.get('estado')).toBe('PENDIENTE');
    req.flush([]);
    await pendientes;
  });

  it('paga con POST /api/cargos/{id}/pagar y devuelve el cargo', async () => {
    const resultado = firstValueFrom(servicio.pagar(3));
    const req = http.expectOne('/api/cargos/3/pagar');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 3, estado: 'PAGADO' });
    expect(await resultado).toMatchObject({ id: 3, estado: 'PAGADO' });
  });

  it('consulta los saldos con GET /api/saldos', async () => {
    const resultado = firstValueFrom(servicio.saldos());
    const req = http.expectOne('/api/saldos');
    expect(req.request.method).toBe('GET');
    req.flush({ saldos: [], totalDebo: 0, totalMeDeben: 0, totalNeto: 0 });
    expect((await resultado).totalNeto).toBe(0);
  });
});
