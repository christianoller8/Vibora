import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { PartidoService } from './partido.service';

describe('PartidoService', () => {
  let servicio: PartidoService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(PartidoService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista los partidos abiertos con GET /api/partidos sin filtros', async () => {
    const resultado = firstValueFrom(servicio.listar());
    const req = http.expectOne('/api/partidos');
    expect(req.request.method).toBe('GET');
    req.flush([]);
    expect(await resultado).toEqual([]);
  });

  it('filtra por clubId y fecha', async () => {
    const resultado = firstValueFrom(servicio.listar({ clubId: 2, fecha: '2026-07-15' }));
    const req = http.expectOne((r) => r.url === '/api/partidos');
    expect(req.request.params.get('clubId')).toBe('2');
    expect(req.request.params.get('fecha')).toBe('2026-07-15');
    req.flush([]);
    await resultado;
  });

  it('omite los filtros vacíos o nulos', async () => {
    const resultado = firstValueFrom(servicio.listar({ clubId: null, fecha: '' }));
    const req = http.expectOne('/api/partidos');
    expect(req.request.params.keys()).toHaveLength(0);
    req.flush([]);
    await resultado;
  });

  it('se apunta con POST /api/reservas/{id}/apuntarse', async () => {
    const resultado = firstValueFrom(servicio.apuntarse(5));
    const req = http.expectOne('/api/reservas/5/apuntarse');
    expect(req.request.method).toBe('POST');
    req.flush({ reservaId: 5 });
    expect(await resultado).toEqual({ reservaId: 5 });
  });

  it('sale con POST /api/reservas/{id}/salir', async () => {
    const resultado = firstValueFrom(servicio.salir(5));
    const req = http.expectOne('/api/reservas/5/salir');
    expect(req.request.method).toBe('POST');
    req.flush({ reservaId: 5 });
    await resultado;
  });

  it('abre o cierra el partido con PUT /api/reservas/{id}/abierta y {abierta}', async () => {
    const resultado = firstValueFrom(servicio.setAbierta(5, true));
    const req = http.expectOne('/api/reservas/5/abierta');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ abierta: true });
    req.flush({ id: 5, abierta: true });
    expect(await resultado).toMatchObject({ id: 5, abierta: true });
  });
});
