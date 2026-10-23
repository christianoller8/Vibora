import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { AuthLayout } from './core/layout/auth-layout/auth-layout';

/*
 * ORDEN IMPORTANTE: el layout con redirección de '' va primero. Una ruta de path '' con loadChildren
 * "se come" la URL vacía aunque ningún hijo coincida, y '/' acabaría pintando el layout con un
 * router-outlet vacío. Para /login y /registro el primer grupo no encuentra hijo y el router
 * retrocede al segundo.
 */
export const routes: Routes = [
  {
    path: '',
    // el layout es el "marco" de las rutas hijas; carga diferida porque arrastra toolbar/sidenav/list de Material
    loadComponent: () => import('./core/layout/main-layout/main-layout').then((m) => m.MainLayout),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'agenda' },
      {
        path: '',
        canActivate: [authGuard],
        loadChildren: () =>
          import('./features/reservas/reservas.routes').then((m) => m.RESERVAS_ROUTES),
      },
      {
        path: 'partidos',
        canActivate: [authGuard],
        loadChildren: () =>
          import('./features/partidos/partidos.routes').then((m) => m.PARTIDOS_ROUTES),
      },
      {
        path: 'deudas',
        canActivate: [authGuard],
        loadChildren: () => import('./features/deudas/deudas.routes').then((m) => m.DEUDAS_ROUTES),
      },
      {
        path: 'club',
        canActivate: [authGuard],
        loadChildren: () => import('./features/club/club.routes').then((m) => m.CLUB_ROUTES),
      },
    ],
  },
  {
    // login y registro: marco limpio, sin menú lateral
    path: '',
    component: AuthLayout,
    loadChildren: () => import('./features/auth/auth.routes').then((m) => m.AUTH_ROUTES),
  },
  { path: '**', redirectTo: 'agenda' },
];
