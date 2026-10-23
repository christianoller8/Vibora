import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Solo usuarios con sesión; si no, a /login recordando a dónde querían ir. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.estaAutenticado()
    ? true
    : router.createUrlTree(['/login'], { queryParams: { redirectTo: state.url } });
};

/** Solo el administrador de un club; el resto vuelve a la agenda. */
export const adminClubGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.esAdminClub() ? true : router.createUrlTree(['/agenda']);
};

/** Solo un jugador que aún no tiene club (puede crear el suyo); el resto vuelve a la agenda. */
export const sinClubGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.puedeCrearClub() ? true : router.createUrlTree(['/agenda']);
};

/** Para login/registro: quien ya tiene sesión rebota a la agenda. */
export const invitadoGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.estaAutenticado() ? router.createUrlTree(['/agenda']) : true;
};
