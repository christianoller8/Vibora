import { Routes } from '@angular/router';

export const RESERVAS_ROUTES: Routes = [
  {
    path: 'agenda',
    title: 'Agenda · Víbora',
    loadComponent: () => import('./agenda/agenda').then((m) => m.Agenda),
  },
  {
    path: 'mis-reservas',
    title: 'Mis reservas · Víbora',
    loadComponent: () => import('./mis-reservas/mis-reservas').then((m) => m.MisReservas),
  },
];
