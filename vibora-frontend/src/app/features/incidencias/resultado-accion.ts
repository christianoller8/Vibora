import { IncidenciaDetalle } from '../../shared/models/incidencia.model';

/**
 * Con qué se cierra un diálogo de acción sobre una incidencia (asignar, cambiar de estado). Los diálogos llaman
 * ellos mismos a la API para poder mostrar un 403/409 sin cerrarse; solo se cierran con `ok` (la incidencia ya
 * actualizada) o con `conflicto` (otra persona la modificó a la vez: la página recarga y avisa).
 */
export type ResultadoAccion =
  { tipo: 'ok'; incidencia: IncidenciaDetalle } | { tipo: 'conflicto'; mensaje: string };
