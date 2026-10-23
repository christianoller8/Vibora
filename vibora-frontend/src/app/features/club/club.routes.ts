import { Routes } from '@angular/router';
import { adminClubGuard, sinClubGuard } from '../../core/auth/auth.guard';

/** Cuelga de `/club` (con authGuard). Cada pantalla se protege por rol: el resto vuelve a la agenda. */
export const CLUB_ROUTES: Routes = [
  {
    path: 'crear',
    title: 'Crear mi club · Víbora',
    canActivate: [sinClubGuard],
    loadComponent: () => import('./crear-club/crear-club').then((m) => m.CrearClub),
  },
  {
    path: '',
    title: 'Mi club · Víbora',
    canActivate: [adminClubGuard],
    loadComponent: () => import('./club-admin/club-admin').then((m) => m.ClubAdmin),
  },
];
