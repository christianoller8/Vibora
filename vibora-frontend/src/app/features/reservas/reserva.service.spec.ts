import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { ReservaService } from './reserva.service';

describe('ReservaService', () => {
  let servicio: ReservaService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(ReservaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('pide la agenda con la fecha como parámetro YYYY-MM-DD', async () => {
    const resultado = firstValueFrom(servicio.agenda(1, 10, '2026-07-15'));
    const req = http.expectOne(
      (r) => r.url === '/api/clubes/1/pistas/10/agenda' && r.params.get('fecha') === '2026-07-15',
    );
    expect(req.request.method).toBe('GET');
    req.flush([]);
    expect(await resultado).toEqual([]);
  });

  it('crea una reserva enviando solo el inicio', async () => {
    const resultado = firstValueFrom(servicio.crear(1, 10, '2026-07-15T16:00:00Z'));
    const req = http.expectOne('/api/clubes/1/pistas/10/reservas');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ inicio: '2026-07-15T16:00:00Z' });
    req.flush({ id: 5 }, { status: 201, statusText: 'Created' });
    expect(await resultado).toEqual({ id: 5 });
  });

  it('lista las reservas propias', async () => {
    const resultado = firstValueFrom(servicio.mias());
    http.expectOne('/api/reservas/mias').flush([{ id: 1 }]);
    expect(await resultado).toEqual([{ id: 1 }]);
  });

  it('lista las reservas de un club en una fecha (vista del administrador)', async () => {
    const resultado = firstValueFrom(servicio.delClub(5, '2026-07-15'));
    const req = http.expectOne(
      (r) => r.url === '/api/clubes/5/reservas' && r.params.get('fecha') === '2026-07-15',
    );
    expect(req.request.method).toBe('GET');
    req.flush([{ id: 3 }]);
    expect(await resultado).toEqual([{ id: 3 }]);
  });

  it('cancela con POST /api/reservas/{id}/cancelar', async () => {
    const resultado = firstValueFrom(servicio.cancelar(7));
    const req = http.expectOne('/api/reservas/7/cancelar');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 7, estado: 'CANCELADA' });
    expect(await resultado).toEqual({ id: 7, estado: 'CANCELADA' });
  });
});
