import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

const PREFIJO_API = '/api/';
// Un 401 en estas llamadas significa "credenciales incorrectas", no "sesión caducada": no redirigir
const RUTAS_PUBLICAS = ['/api/auth/login', '/api/auth/registro'];

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith(PREFIJO_API)) {
    return next(req); // nunca mandamos el token a otros orígenes
  }

  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.token();
  const peticion = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(peticion).pipe(
    catchError((err: unknown) => {
      const esPublica = RUTAS_PUBLICAS.some((ruta) => req.url.startsWith(ruta));
      if (err instanceof HttpErrorResponse && err.status === 401 && token && !esPublica) {
        auth.logout();
        const destino = router.url;
        const queryParams =
          destino.startsWith('/login') || destino === '/' ? {} : { redirectTo: destino };
        void router.navigate(['/login'], { queryParams });
      }
      return throwError(() => err);
    }),
  );
};
