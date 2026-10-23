import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { crearNotificacion } from '../../testing/test-utils';
import { NotificacionService } from './notificacion.service';

describe('NotificacionService', () => {
  let servicio: NotificacionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(NotificacionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista con GET /api/notificaciones sin parámetros por defecto', async () => {
    const resultado = firstValueFrom(servicio.listar());
    const req = http.expectOne('/api/notificaciones');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.keys()).toHaveLength(0);
    req.flush([crearNotificacion(1)]);
    expect(await resultado).toHaveLength(1);
  });

  it('envía soloNoLeidas, page y limit cuando se piden', async () => {
    const resultado = firstValueFrom(servicio.listar({ soloNoLeidas: true, page: 1, limit: 20 }));
    const req = http.expectOne((r) => r.url === '/api/notificaciones');
    expect(req.request.params.get('soloNoLeidas')).toBe('true');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('limit')).toBe('20');
    req.flush([]);
    expect(await resultado).toEqual([]);
  });

  it('cuenta las no leídas con GET /api/notificaciones/no-leidas/count y devuelve el número', async () => {
    const resultado = firstValueFrom(servicio.contarNoLeidas());
    const req = http.expectOne('/api/notificaciones/no-leidas/count');
    expect(req.request.method).toBe('GET');
    req.flush({ count: 3 });
    expect(await resultado).toBe(3);
  });

  it('marca una como leída con POST /api/notificaciones/{id}/leer', async () => {
    const resultado = firstValueFrom(servicio.leer(4));
    const req = http.expectOne('/api/notificaciones/4/leer');
    expect(req.request.method).toBe('POST');
    req.flush(crearNotificacion(4, { leida: true }));
    expect((await resultado).leida).toBe(true);
  });

  it('marca todas con POST /api/notificaciones/leer-todas y devuelve cuántas', async () => {
    const resultado = firstValueFrom(servicio.leerTodas());
    const req = http.expectOne('/api/notificaciones/leer-todas');
    expect(req.request.method).toBe('POST');
    req.flush({ marcadas: 5 });
    expect(await resultado).toBe(5);
  });
});
