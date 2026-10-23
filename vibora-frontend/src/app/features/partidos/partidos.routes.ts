import { Routes } from '@angular/router';

export const PARTIDOS_ROUTES: Routes = [
  {
    path: '',
    title: 'Partidos · Víbora',
    loadComponent: () =>
      import('./partidos-abiertos/partidos-abiertos').then((m) => m.PartidosAbiertos),
  },
];
