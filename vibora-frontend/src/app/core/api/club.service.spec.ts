import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { ClubService } from './club.service';

describe('ClubService', () => {
  let servicio: ClubService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(ClubService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista los clubes con GET /api/clubes', async () => {
    const resultado = firstValueFrom(servicio.listar());
    const req = http.expectOne('/api/clubes');
    expect(req.request.method).toBe('GET');
    req.flush([{ id: 1, nombre: 'Club Víbora' }]);
    expect(await resultado).toEqual([{ id: 1, nombre: 'Club Víbora' }]);
  });

  it('obtiene un club con GET /api/clubes/{id}', async () => {
    const resultado = firstValueFrom(servicio.obtener(4));
    const req = http.expectOne('/api/clubes/4');
    expect(req.request.method).toBe('GET');
    req.flush({ id: 4, nombre: 'Club Víbora' });
    expect(await resultado).toEqual({ id: 4, nombre: 'Club Víbora' });
  });

  it('crea un club con POST /api/clubes y el body tal cual', async () => {
    const datos = {
      nombre: 'Club Nuevo',
      direccion: 'Calle Mayor 1',
      ciudad: 'Madrid',
      horaApertura: '09:00',
      horaCierre: '22:00',
    };
    const resultado = firstValueFrom(servicio.crear(datos));
    const req = http.expectOne('/api/clubes');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(datos);
    req.flush({ id: 8, ...datos }, { status: 201, statusText: 'Created' });
    expect((await resultado).id).toBe(8);
  });

  it('propaga el 409 si el usuario ya tiene un club', async () => {
    const resultado = firstValueFrom(
      servicio.crear({
        nombre: 'X',
        direccion: null,
        ciudad: null,
        horaApertura: '09:00',
        horaCierre: '22:00',
      }),
    );
    http
      .expectOne('/api/clubes')
      .flush({ error: 'Ya perteneces a un club' }, { status: 409, statusText: 'Conflict' });
    await expect(resultado).rejects.toMatchObject({ status: 409 });
  });

  it('actualiza un club con PUT /api/clubes/{id}', async () => {
    const datos = {
      nombre: 'Club Renombrado',
      direccion: null,
      ciudad: 'Getafe',
      horaApertura: '08:00',
      horaCierre: '23:00',
    };
    const resultado = firstValueFrom(servicio.actualizar(4, datos));
    const req = http.expectOne('/api/clubes/4');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(datos);
    req.flush({ id: 4, ...datos });
    expect((await resultado).nombre).toBe('Club Renombrado');
  });

  it('lista las pistas de un club con GET /api/clubes/{id}/pistas', async () => {
    const resultado = firstValueFrom(servicio.pistas(3));
    const req = http.expectOne('/api/clubes/3/pistas');
    expect(req.request.method).toBe('GET');
    req.flush([{ id: 10, nombre: 'Pista 1' }]);
    expect(await resultado).toEqual([{ id: 10, nombre: 'Pista 1' }]);
  });
});
