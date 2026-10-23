import { Routes } from '@angular/router';

export const DEUDAS_ROUTES: Routes = [
  {
    path: '',
    title: 'Deudas · Víbora',
    loadComponent: () => import('./deudas/deudas').then((m) => m.Deudas),
  },
];
