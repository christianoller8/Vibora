import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { PersonalService } from './personal.service';

describe('PersonalService', () => {
  let servicio: PersonalService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(PersonalService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista el personal con GET /api/clubes/{id}/personal', async () => {
    const resultado = firstValueFrom(servicio.listar(5));
    const req = http.expectOne('/api/clubes/5/personal');
    expect(req.request.method).toBe('GET');
    req.flush([
      { usuarioId: 20, nombre: 'Pepe', apellidos: 'Gil', email: 'p@x.es', rol: 'MANTENIMIENTO' },
    ]);
    expect(await resultado).toHaveLength(1);
  });

  it('añade con POST /api/clubes/{id}/personal y {email, rol}; devuelve a la persona', async () => {
    const resultado = firstValueFrom(
      servicio.agregar(5, { email: 'pepe@club.es', rol: 'MANTENIMIENTO' }),
    );
    const req = http.expectOne('/api/clubes/5/personal');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'pepe@club.es', rol: 'MANTENIMIENTO' });
    req.flush(
      {
        usuarioId: 20,
        nombre: 'Pepe',
        apellidos: 'Gil',
        email: 'pepe@club.es',
        rol: 'MANTENIMIENTO',
      },
      { status: 201, statusText: 'Created' },
    );
    expect((await resultado).usuarioId).toBe(20);
  });

  it('quita con DELETE /api/clubes/{id}/personal/{usuarioId} (204)', async () => {
    const resultado = firstValueFrom(servicio.quitar(5, 20));
    const req = http.expectOne('/api/clubes/5/personal/20');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
    expect(await resultado).toBeNull();
  });
});
