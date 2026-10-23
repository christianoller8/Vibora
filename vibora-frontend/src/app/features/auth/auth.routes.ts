import { Routes } from '@angular/router';
import { invitadoGuard } from '../../core/auth/auth.guard';

export const AUTH_ROUTES: Routes = [
  {
    path: 'login',
    canActivate: [invitadoGuard],
    title: 'Iniciar sesión · Víbora',
    loadComponent: () => import('./login/login').then((m) => m.Login),
  },
  {
    path: 'registro',
    canActivate: [invitadoGuard],
    title: 'Crear cuenta · Víbora',
    loadComponent: () => import('./registro/registro').then((m) => m.Registro),
  },
];
