import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { USUARIO_JUGADOR, crearJwt, segundosDesdeAhora } from '../../testing/test-utils';
import { AuthService, CLAVE_TOKEN } from './auth.service';

describe('AuthService', () => {
  let http: HttpTestingController;

  function crearServicio(): AuthService {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    return TestBed.inject(AuthService);
  }

  beforeEach(() => localStorage.clear());
  afterEach(() => localStorage.clear());

  it('arranca sin sesión si no hay token guardado', () => {
    const auth = crearServicio();
    expect(auth.token()).toBeNull();
    expect(auth.estaAutenticado()).toBe(false);
    expect(auth.usuario()).toBeNull();
    expect(auth.rol()).toBeNull();
  });

  describe('login', () => {
    it('guarda el token, carga el usuario desde /me y expone el rol', async () => {
      const auth = crearServicio();
      const token = crearJwt(segundosDesdeAhora(3600));

      const resultado = firstValueFrom(auth.login('ana@vibora.es', 'secreto123'));
      const login = http.expectOne('/api/auth/login');
      expect(login.request.method).toBe('POST');
      expect(login.request.body).toEqual({ email: 'ana@vibora.es', password: 'secreto123' });
      login.flush({ token, tipo: 'Bearer', expiraEn: '2099-01-01T00:00:00Z' });

      http.expectOne('/api/auth/me').flush(USUARIO_JUGADOR);

      expect(await resultado).toEqual(USUARIO_JUGADOR);
      expect(auth.token()).toBe(token);
      expect(localStorage.getItem(CLAVE_TOKEN)).toBe(token);
      expect(auth.usuario()).toEqual(USUARIO_JUGADOR);
      expect(auth.estaAutenticado()).toBe(true);
      expect(auth.rol()).toBe('JUGADOR');
    });

    it('propaga el error 401 y no deja sesión', async () => {
      const auth = crearServicio();

      const resultado = firstValueFrom(auth.login('ana@vibora.es', 'mala'));
      http
        .expectOne('/api/auth/login')
        .flush({ error: 'Credenciales incorrectas' }, { status: 401, statusText: 'Unauthorized' });

      await expect(resultado).rejects.toMatchObject({ status: 401 });
      expect(auth.estaAutenticado()).toBe(false);
      expect(localStorage.getItem(CLAVE_TOKEN)).toBeNull();
    });

    it('limpia la sesión si /me falla justo después del login', async () => {
      const auth = crearServicio();
      const token = crearJwt(segundosDesdeAhora(3600));

      const resultado = firstValueFrom(auth.login('ana@vibora.es', 'secreto123'));
      http.expectOne('/api/auth/login').flush({ token, tipo: 'Bearer', expiraEn: 'x' });
      http.expectOne('/api/auth/me').flush({}, { status: 500, statusText: 'Server Error' });

      await expect(resultado).rejects.toMatchObject({ status: 500 });
      expect(auth.estaAutenticado()).toBe(false);
      expect(localStorage.getItem(CLAVE_TOKEN)).toBeNull();
    });
  });

  describe('registro', () => {
    it('envía los datos, devuelve el usuario y NO abre sesión', async () => {
      const auth = crearServicio();
      const datos = {
        email: 'ana@vibora.es',
        password: 'secreto123',
        nombre: 'Ana',
        apellidos: 'García López',
      };

      const resultado = firstValueFrom(auth.registro(datos));
      const req = http.expectOne('/api/auth/registro');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(datos);
      req.flush(USUARIO_JUGADOR, { status: 201, statusText: 'Created' });

      expect(await resultado).toEqual(USUARIO_JUGADOR);
      expect(auth.estaAutenticado()).toBe(false);
    });

    it('propaga el 409 de email duplicado', async () => {
      const auth = crearServicio();

      const resultado = firstValueFrom(
        auth.registro({ email: 'a@b.es', password: '12345678', apellidos: 'X' }),
      );
      http
        .expectOne('/api/auth/registro')
        .flush({ error: 'El email ya está registrado' }, { status: 409, statusText: 'Conflict' });

      await expect(resultado).rejects.toMatchObject({
        status: 409,
        error: { error: 'El email ya está registrado' },
      });
    });
  });

  describe('logout', () => {
    it('borra token, usuario y almacenamiento', async () => {
      localStorage.setItem(CLAVE_TOKEN, crearJwt(segundosDesdeAhora(3600)));
      const auth = crearServicio();
      const carga = firstValueFrom(auth.cargarUsuario());
      http.expectOne('/api/auth/me').flush(USUARIO_JUGADOR);
      await carga;

      auth.logout();

      expect(auth.token()).toBeNull();
      expect(auth.usuario()).toBeNull();
      expect(auth.estaAutenticado()).toBe(false);
      expect(localStorage.getItem(CLAVE_TOKEN)).toBeNull();
    });
  });

  describe('arranque (cargarUsuario)', () => {
    it('no hace ninguna petición si no hay token', async () => {
      const auth = crearServicio();

      expect(await firstValueFrom(auth.cargarUsuario())).toBeNull();
      http.expectNone('/api/auth/me');
    });

    it('descarta un token caducado sin llamar al backend', async () => {
      localStorage.setItem(CLAVE_TOKEN, crearJwt(segundosDesdeAhora(-60)));
      const auth = crearServicio();

      expect(auth.token()).toBeNull();
      expect(localStorage.getItem(CLAVE_TOKEN)).toBeNull();
      expect(await firstValueFrom(auth.cargarUsuario())).toBeNull();
      http.expectNone('/api/auth/me');
    });

    it('descarta un token ilegible', () => {
      localStorage.setItem(CLAVE_TOKEN, 'basura');
      const auth = crearServicio();

      expect(auth.estaAutenticado()).toBe(false);
      expect(localStorage.getItem(CLAVE_TOKEN)).toBeNull();
    });

    it('restaura el usuario con /me cuando el token es válido', async () => {
      localStorage.setItem(CLAVE_TOKEN, crearJwt(segundosDesdeAhora(3600)));
      const auth = crearServicio();

      const carga = firstValueFrom(auth.cargarUsuario());
      http.expectOne('/api/auth/me').flush(USUARIO_JUGADOR);

      expect(await carga).toEqual(USUARIO_JUGADOR);
      expect(auth.usuario()).toEqual(USUARIO_JUGADOR);
      expect(auth.estaAutenticado()).toBe(true);
    });

    it('cierra la sesión (sin romper el arranque) si /me falla', async () => {
      localStorage.setItem(CLAVE_TOKEN, crearJwt(segundosDesdeAhora(3600)));
      const auth = crearServicio();

      const carga = firstValueFrom(auth.cargarUsuario());
      http.expectOne('/api/auth/me').flush({}, { status: 401, statusText: 'Unauthorized' });

      expect(await carga).toBeNull();
      expect(auth.estaAutenticado()).toBe(false);
      expect(localStorage.getItem(CLAVE_TOKEN)).toBeNull();
    });
  });
});
