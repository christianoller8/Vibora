import { Routes } from '@angular/router';

/** Cuelga de `/incidencias` (con authGuard). `nueva` va antes que `:id` para que no se tome por un id. */
export const INCIDENCIAS_ROUTES: Routes = [
  {
    path: '',
    title: 'Incidencias · Víbora',
    loadComponent: () =>
      import('./incidencias-lista/incidencias-lista').then((m) => m.IncidenciasLista),
  },
  {
    path: 'nueva',
    title: 'Reportar incidencia · Víbora',
    loadComponent: () =>
      import('./incidencia-nueva/incidencia-nueva').then((m) => m.IncidenciaNueva),
  },
  {
    path: ':id',
    title: 'Incidencia · Víbora',
    loadComponent: () =>
      import('./incidencia-detalle/incidencia-detalle').then((m) => m.DetalleIncidencia),
  },
];
