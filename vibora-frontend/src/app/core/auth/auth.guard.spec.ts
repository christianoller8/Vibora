import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { crearJwt, segundosDesdeAhora } from '../../testing/test-utils';
import { authGuard, invitadoGuard } from './auth.guard';
import { CLAVE_TOKEN } from './auth.service';

describe('guards de autenticación', () => {
  function ejecutar(guard: CanActivateFn, url: string, conSesion: boolean): boolean | string {
    if (conSesion) {
      localStorage.setItem(CLAVE_TOKEN, crearJwt(segundosDesdeAhora(3600)));
    }
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    const router = TestBed.inject(Router);
    const resultado = TestBed.runInInjectionContext(() =>
      guard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    );
    return resultado instanceof UrlTree ? router.serializeUrl(resultado) : (resultado as boolean);
  }

  beforeEach(() => localStorage.clear());
  afterEach(() => localStorage.clear());

  describe('authGuard', () => {
    it('deja pasar a un usuario autenticado', () => {
      expect(ejecutar(authGuard, '/mis-reservas', true)).toBe(true);
    });

    it('redirige a /login conservando la URL pedida en redirectTo', () => {
      expect(ejecutar(authGuard, '/mis-reservas', false)).toBe('/login?redirectTo=%2Fmis-reservas');
    });
  });

  describe('invitadoGuard', () => {
    it('deja pasar a un usuario sin sesión', () => {
      expect(ejecutar(invitadoGuard, '/login', false)).toBe(true);
    });

    it('manda a /agenda a quien ya tiene sesión', () => {
      expect(ejecutar(invitadoGuard, '/login', true)).toBe('/agenda');
    });
  });
});
