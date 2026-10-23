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
import { Usuario } from '../../shared/models/usuario.model';
import {
  USUARIO_ADMIN,
  USUARIO_JUGADOR,
  crearJwt,
  segundosDesdeAhora,
} from '../../testing/test-utils';
import { adminClubGuard, authGuard, invitadoGuard, sinClubGuard } from './auth.guard';
import { AuthService, CLAVE_TOKEN } from './auth.service';

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

  describe('guards de club', () => {
    function ejecutarConUsuario(guard: CanActivateFn, usuario: Usuario | null): boolean | string {
      TestBed.configureTestingModule({
        providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
      });
      TestBed.inject(AuthService).usuario.set(usuario);
      const router = TestBed.inject(Router);
      const resultado = TestBed.runInInjectionContext(() =>
        guard({} as ActivatedRouteSnapshot, { url: '/club' } as RouterStateSnapshot),
      );
      return resultado instanceof UrlTree ? router.serializeUrl(resultado) : (resultado as boolean);
    }

    it('adminClubGuard deja pasar al administrador de un club', () => {
      expect(ejecutarConUsuario(adminClubGuard, USUARIO_ADMIN)).toBe(true);
    });

    it('adminClubGuard manda a /agenda a un jugador', () => {
      expect(ejecutarConUsuario(adminClubGuard, USUARIO_JUGADOR)).toBe('/agenda');
    });

    it('adminClubGuard manda a /agenda a un monitor', () => {
      expect(ejecutarConUsuario(adminClubGuard, { ...USUARIO_ADMIN, rol: 'MONITOR' })).toBe(
        '/agenda',
      );
    });

    it('sinClubGuard deja pasar a un jugador sin club', () => {
      expect(ejecutarConUsuario(sinClubGuard, USUARIO_JUGADOR)).toBe(true);
    });

    it('sinClubGuard manda a /agenda a un administrador (ya tiene club)', () => {
      expect(ejecutarConUsuario(sinClubGuard, USUARIO_ADMIN)).toBe('/agenda');
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
