import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AuthService, CLAVE_TOKEN } from './core/auth/auth.service';
import {
  FABRICA_CLIENTE_TIEMPO_REAL,
  FabricaClienteTiempoReal,
} from './core/notificaciones/cliente-tiempo-real';
import { Usuario } from './shared/models/usuario.model';
import { USUARIO_ADMIN, USUARIO_JUGADOR, crearJwt, segundosDesdeAhora } from './testing/test-utils';
import { routes } from './app.routes';

/** Las páginas autenticadas arrancan la conexión en tiempo real: aquí no se abre ningún socket. */
const fabricaSinSockets: FabricaClienteTiempoReal = () =>
  Promise.resolve({ activar: () => undefined, desactivar: () => Promise.resolve() });

describe('rutas de la aplicación', () => {
  async function navegar(url: string, conSesion: boolean, usuario?: Usuario): Promise<string> {
    if (conSesion) {
      localStorage.setItem(CLAVE_TOKEN, crearJwt(segundosDesdeAhora(3600)));
    }
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: FABRICA_CLIENTE_TIEMPO_REAL, useValue: fabricaSinSockets },
      ],
    });
    if (usuario) {
      TestBed.inject(AuthService).usuario.set(usuario); // como si /me ya hubiera respondido
    }
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    await harness.fixture.whenStable();
    return TestBed.inject(Router).url;
  }

  beforeEach(() => localStorage.clear());
  afterEach(() => {
    TestBed.inject(HttpTestingController).match(() => true); // descarta peticiones de las páginas
    localStorage.clear();
  });

  it('la raíz sin sesión acaba en /login (con redirectTo=/agenda)', async () => {
    expect(await navegar('/', false)).toBe('/login?redirectTo=%2Fagenda');
  });

  it('la raíz con sesión acaba en /agenda', async () => {
    expect(await navegar('/', true)).toBe('/agenda');
  });

  it('una ruta desconocida acaba en /agenda (y de ahí en /login sin sesión)', async () => {
    expect(await navegar('/no-existe', true)).toBe('/agenda');
  });

  it('/mis-reservas sin sesión va a /login conservando redirectTo', async () => {
    expect(await navegar('/mis-reservas', false)).toBe('/login?redirectTo=%2Fmis-reservas');
  });

  it('/partidos sin sesión va a /login conservando redirectTo', async () => {
    expect(await navegar('/partidos', false)).toBe('/login?redirectTo=%2Fpartidos');
  });

  it('/deudas sin sesión va a /login conservando redirectTo', async () => {
    expect(await navegar('/deudas', false)).toBe('/login?redirectTo=%2Fdeudas');
  });

  it('/partidos con sesión se queda en /partidos', async () => {
    expect(await navegar('/partidos', true)).toBe('/partidos');
  });

  it('/deudas con sesión se queda en /deudas', async () => {
    expect(await navegar('/deudas', true)).toBe('/deudas');
  });

  it('/club sin sesión va a /login conservando redirectTo', async () => {
    expect(await navegar('/club', false)).toBe('/login?redirectTo=%2Fclub');
  });

  it('/club/crear sin sesión va a /login conservando redirectTo', async () => {
    expect(await navegar('/club/crear', false)).toBe('/login?redirectTo=%2Fclub%2Fcrear');
  });

  it('/club con un administrador se queda en /club', async () => {
    expect(await navegar('/club', true, USUARIO_ADMIN)).toBe('/club');
  });

  it('/club con un jugador rebota a /agenda', async () => {
    expect(await navegar('/club', true, USUARIO_JUGADOR)).toBe('/agenda');
  });

  it('/club/crear con un jugador sin club se queda en /club/crear', async () => {
    expect(await navegar('/club/crear', true, USUARIO_JUGADOR)).toBe('/club/crear');
  });

  it('/club/crear con un administrador rebota a /agenda', async () => {
    expect(await navegar('/club/crear', true, USUARIO_ADMIN)).toBe('/agenda');
  });

  it('/login con sesión rebota a /agenda', async () => {
    expect(await navegar('/login', true)).toBe('/agenda');
  });

  it('/registro sin sesión se queda en /registro', async () => {
    expect(await navegar('/registro', false)).toBe('/registro');
  });

  it('login y registro no llevan el menú lateral', async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/login');
    await harness.fixture.whenStable();
    const el = harness.routeNativeElement as HTMLElement;
    expect(el.querySelector('mat-sidenav')).toBeNull();
    expect(el.querySelector('form')).not.toBeNull();
  });
});
