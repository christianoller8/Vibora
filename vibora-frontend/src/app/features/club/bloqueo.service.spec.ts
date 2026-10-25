import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { BloqueoService } from './bloqueo.service';

const BASE = '/api/clubes/5/pistas/10/bloqueos';

describe('BloqueoService', () => {
  let servicio: BloqueoService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(BloqueoService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  describe('listar (activos es tri-estado)', () => {
    it('sin el argumento no envía el parámetro: devuelve todos', async () => {
      const resultado = firstValueFrom(servicio.listar(5, 10));
      const req = http.expectOne((r) => r.url === BASE);
      expect(req.request.method).toBe('GET');
      expect(req.request.params.has('activos')).toBe(false);
      req.flush([]);
      expect(await resultado).toEqual([]);
    });

    it('activos=true pide solo los activos', async () => {
      const resultado = firstValueFrom(servicio.listar(5, 10, true));
      const req = http.expectOne((r) => r.url === BASE);
      expect(req.request.params.get('activos')).toBe('true');
      req.flush([]);
      await resultado;
    });

    it('activos=false pide solo los levantados o caducados (no se confunde con "sin filtro")', async () => {
      const resultado = firstValueFrom(servicio.listar(5, 10, false));
      const req = http.expectOne((r) => r.url === BASE);
      expect(req.request.params.get('activos')).toBe('false');
      req.flush([]);
      await resultado;
    });
  });

  it('crea con POST y el cuerpo tal cual, y devuelve el bloqueo con sus afectadas', async () => {
    const cuerpo = { motivo: 'Foco', desde: '2026-07-15T16:00:00.000Z', incidenciaId: 7 };
    const resultado = firstValueFrom(servicio.crear(5, 10, cuerpo));
    const req = http.expectOne(BASE);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(cuerpo);
    req.flush({ bloqueo: { id: 3 }, afectadas: [{ id: 1 }] });
    const creado = await resultado;
    expect(creado.bloqueo.id).toBe(3);
    expect(creado.afectadas).toHaveLength(1);
  });

  it('pide las afectadas con GET .../{id}/afectadas', async () => {
    const resultado = firstValueFrom(servicio.afectadas(5, 10, 3));
    const req = http.expectOne(`${BASE}/3/afectadas`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
    expect(await resultado).toEqual([]);
  });

  it('levanta con DELETE .../{id} (204 sin cuerpo)', async () => {
    const resultado = firstValueFrom(servicio.levantar(5, 10, 3));
    const req = http.expectOne(`${BASE}/3`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
    expect(await resultado).toBeNull();
  });

  it('cancela las afectadas con POST .../{id}/cancelar-afectadas y devuelve el recuento', async () => {
    const resultado = firstValueFrom(servicio.cancelarAfectadas(5, 10, 3));
    const req = http.expectOne(`${BASE}/3/cancelar-afectadas`);
    expect(req.request.method).toBe('POST');
    req.flush({ canceladas: 2, ids: [1, 4] });
    expect(await resultado).toEqual({ canceladas: 2, ids: [1, 4] });
  });

  it('el atajo de una incidencia hace POST /api/incidencias/{id}/bloquear-pista sin cuerpo', async () => {
    const resultado = firstValueFrom(servicio.bloquearPistaDeIncidencia(7));
    const req = http.expectOne('/api/incidencias/7/bloquear-pista');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBeNull();
    req.flush({ bloqueo: { id: 3 }, afectadas: [] });
    expect((await resultado).bloqueo.id).toBe(3);
  });
});
