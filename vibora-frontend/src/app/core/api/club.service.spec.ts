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

  it('lista las pistas de un club con GET /api/clubes/{id}/pistas', async () => {
    const resultado = firstValueFrom(servicio.pistas(3));
    const req = http.expectOne('/api/clubes/3/pistas');
    expect(req.request.method).toBe('GET');
    req.flush([{ id: 10, nombre: 'Pista 1' }]);
    expect(await resultado).toEqual([{ id: 10, nombre: 'Pista 1' }]);
  });
});
