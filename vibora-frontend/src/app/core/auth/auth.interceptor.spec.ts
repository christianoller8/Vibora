import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { crearJwt, segundosDesdeAhora } from '../../testing/test-utils';
import { AuthService, CLAVE_TOKEN } from './auth.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  let router: Router;
  let navegar: ReturnType<typeof vi.spyOn>;

  function preparar(conToken: boolean, urlActual = '/mis-reservas'): void {
    if (conToken) {
      localStorage.setItem(CLAVE_TOKEN, crearJwt(segundosDesdeAhora(3600)));
    }
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'url', 'get').mockReturnValue(urlActual);
    navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);
  }

  beforeEach(() => localStorage.clear());
  afterEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('añade Authorization: Bearer a las peticiones /api/', () => {
    preparar(true);
    const token = localStorage.getItem(CLAVE_TOKEN);

    http.get('/api/clubes').subscribe();

    const req = controller.expectOne('/api/clubes');
    expect(req.request.headers.get('Authorization')).toBe(`Bearer ${token}`);
  });

  it('no añade la cabecera a URLs que no son /api/', () => {
    preparar(true);

    http.get('/assets/datos.json').subscribe();
    http.get('https://externo.example.com/api/algo').subscribe();

    expect(controller.expectOne('/assets/datos.json').request.headers.has('Authorization')).toBe(
      false,
    );
    expect(
      controller
        .expectOne('https://externo.example.com/api/algo')
        .request.headers.has('Authorization'),
    ).toBe(false);
  });

  it('no añade la cabecera si no hay sesión', () => {
    preparar(false);

    http.get('/api/clubes').subscribe();

    expect(controller.expectOne('/api/clubes').request.headers.has('Authorization')).toBe(false);
  });

  it('ante un 401 en una llamada autenticada cierra sesión y va a /login con redirectTo', () => {
    preparar(true, '/mis-reservas');
    const auth = TestBed.inject(AuthService);
    let fallo: unknown;

    http.get('/api/reservas/mias').subscribe({ error: (e: unknown) => (fallo = e) });
    controller
      .expectOne('/api/reservas/mias')
      .flush({ error: 'No autenticado' }, { status: 401, statusText: 'Unauthorized' });

    expect(auth.estaAutenticado()).toBe(false);
    expect(localStorage.getItem(CLAVE_TOKEN)).toBeNull();
    expect(navegar).toHaveBeenCalledWith(['/login'], {
      queryParams: { redirectTo: '/mis-reservas' },
    });
    // el error sigue llegando a quien hizo la llamada
    expect(fallo).toMatchObject({ status: 401 });
  });

  it('no añade redirectTo si ya estamos en /login', () => {
    preparar(true, '/login?redirectTo=%2Fagenda');

    http.get('/api/clubes').subscribe({ error: () => undefined });
    controller.expectOne('/api/clubes').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(navegar).toHaveBeenCalledWith(['/login'], { queryParams: {} });
  });

  it('no cierra sesión ni redirige ante un 401 de /api/auth/login (sin bucle)', () => {
    preparar(true);
    const auth = TestBed.inject(AuthService);

    http.post('/api/auth/login', {}).subscribe({ error: () => undefined });
    controller
      .expectOne('/api/auth/login')
      .flush({ error: 'Credenciales incorrectas' }, { status: 401, statusText: 'Unauthorized' });

    expect(navegar).not.toHaveBeenCalled();
    expect(auth.estaAutenticado()).toBe(true);
  });

  it('no redirige ante un 401 de /api/auth/registro', () => {
    preparar(true);

    http.post('/api/auth/registro', {}).subscribe({ error: () => undefined });
    controller
      .expectOne('/api/auth/registro')
      .flush({ error: 'x' }, { status: 401, statusText: 'Unauthorized' });

    expect(navegar).not.toHaveBeenCalled();
  });

  it('no redirige ante un 401 si no había sesión', () => {
    preparar(false);

    http.get('/api/clubes').subscribe({ error: () => undefined });
    controller.expectOne('/api/clubes').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(navegar).not.toHaveBeenCalled();
  });

  it.each([400, 403, 404, 409, 500])('no toca la sesión ante un %i', (status) => {
    preparar(true);
    const auth = TestBed.inject(AuthService);

    http.get('/api/clubes').subscribe({ error: () => undefined });
    controller.expectOne('/api/clubes').flush({}, { status, statusText: 'Error' });

    expect(navegar).not.toHaveBeenCalled();
    expect(auth.estaAutenticado()).toBe(true);
  });
});
