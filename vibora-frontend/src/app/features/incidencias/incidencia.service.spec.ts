import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { IncidenciaService } from './incidencia.service';

describe('IncidenciaService', () => {
  let servicio: IncidenciaService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(IncidenciaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista sin filtros con GET /api/clubes/{id}/incidencias y sin parámetros', async () => {
    const resultado = firstValueFrom(servicio.listar(5));
    const req = http.expectOne('/api/clubes/5/incidencias');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.keys()).toEqual([]);
    req.flush([]);
    expect(await resultado).toEqual([]);
  });

  it('envía solo los filtros definidos (estado, pistaId, asignadaA, page, limit)', async () => {
    const resultado = firstValueFrom(
      servicio.listar(5, { estado: 'ABIERTA', pistaId: 3, asignadaA: 20, page: 2, limit: 10 }),
    );
    const req = http.expectOne((r) => r.url === '/api/clubes/5/incidencias');
    expect(req.request.params.get('estado')).toBe('ABIERTA');
    expect(req.request.params.get('pistaId')).toBe('3');
    expect(req.request.params.get('asignadaA')).toBe('20');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('limit')).toBe('10');
    req.flush([]);
    await resultado;
  });

  it('un filtro con valor 0 (primera página) sí se envía; los indefinidos no', async () => {
    const resultado = firstValueFrom(servicio.listar(5, { page: 0, estado: undefined }));
    const req = http.expectOne((r) => r.url === '/api/clubes/5/incidencias');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.has('estado')).toBe(false);
    req.flush([]);
    await resultado;
  });

  it('obtiene el detalle con GET /api/incidencias/{id}', async () => {
    const resultado = firstValueFrom(servicio.obtener(7));
    const req = http.expectOne('/api/incidencias/7');
    expect(req.request.method).toBe('GET');
    req.flush({ id: 7 });
    expect(await resultado).toMatchObject({ id: 7 });
  });

  it('crea con POST /api/clubes/{id}/incidencias y el cuerpo tal cual', async () => {
    const cuerpo = {
      titulo: 'Foco fundido',
      descripcion: 'No se ve nada',
      categoria: 'ILUMINACION' as const,
      pistaId: 3,
    };
    const resultado = firstValueFrom(servicio.crear(5, cuerpo));
    const req = http.expectOne('/api/clubes/5/incidencias');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(cuerpo);
    req.flush({ id: 11 });
    expect(await resultado).toMatchObject({ id: 11 });
  });

  it('asigna con POST /api/incidencias/{id}/asignar (usuarioId y prioridad opcional)', async () => {
    const resultado = firstValueFrom(servicio.asignar(7, { usuarioId: 20, prioridad: 'ALTA' }));
    const req = http.expectOne('/api/incidencias/7/asignar');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ usuarioId: 20, prioridad: 'ALTA' });
    req.flush({ id: 7 });
    await resultado;
  });

  it('cambia el estado con POST /api/incidencias/{id}/estado', async () => {
    const resultado = firstValueFrom(
      servicio.cambiarEstado(7, { estado: 'RECHAZADA', comentario: 'Duplicada' }),
    );
    const req = http.expectOne('/api/incidencias/7/estado');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ estado: 'RECHAZADA', comentario: 'Duplicada' });
    req.flush({ id: 7 });
    await resultado;
  });

  it('comenta con POST /api/incidencias/{id}/comentarios y body { texto }', async () => {
    const resultado = firstValueFrom(servicio.comentar(7, 'Ya voy para allá'));
    const req = http.expectOne('/api/incidencias/7/comentarios');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ texto: 'Ya voy para allá' });
    req.flush({ id: 7 });
    await resultado;
  });
});
