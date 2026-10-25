import {
  ESTADOS_TERMINALES,
  EstadoIncidencia,
  IncidenciaResumen,
} from '../../shared/models/incidencia.model';
import { Usuario } from '../../shared/models/usuario.model';

/**
 * Máquina de estados de una incidencia, espejo de IncidenciaService (backend). El backend sigue siendo la
 * autoridad: esto solo decide qué botones se ofrecen; un 403/409 se muestra tal cual lo da el servidor.
 *
 * ABIERTA  -> asignar (admin) | rechazar (admin, con motivo)
 * ASIGNADA -> reasignar (admin) | EN_CURSO (asignado o admin)
 * EN_CURSO -> reasignar (admin, vuelve a ASIGNADA) | RESUELTA (asignado o admin)
 * RESUELTA -> CERRADA o reabrir a EN_CURSO (reportero o admin)
 * CERRADA y RECHAZADA: terminales
 */
export type AccionIncidencia =
  'ASIGNAR' | 'INICIAR' | 'RESOLVER' | 'CERRAR' | 'REABRIR' | 'RECHAZAR';

export interface ContextoIncidencia {
  /** Administrador del club de la incidencia. */
  esAdmin: boolean;
  /** Responsable actual, y todavía de mantenimiento del club. */
  esAsignado: boolean;
  esReportero: boolean;
}

const ACCION_A_ESTADO: Record<Exclude<AccionIncidencia, 'ASIGNAR'>, EstadoIncidencia> = {
  INICIAR: 'EN_CURSO',
  RESOLVER: 'RESUELTA',
  CERRAR: 'CERRADA',
  REABRIR: 'EN_CURSO',
  RECHAZAR: 'RECHAZADA',
};

/** Estado al que lleva cada acción (todas salvo ASIGNAR, que va por su propio endpoint). */
export function estadoDestino(accion: Exclude<AccionIncidencia, 'ASIGNAR'>): EstadoIncidencia {
  return ACCION_A_ESTADO[accion];
}

/** Acciones legales ahora mismo, en el orden en que se muestran. */
export function accionesDisponibles(
  estado: EstadoIncidencia,
  contexto: ContextoIncidencia,
): AccionIncidencia[] {
  const { esAdmin, esAsignado, esReportero } = contexto;
  switch (estado) {
    case 'ABIERTA':
      return esAdmin ? ['ASIGNAR', 'RECHAZAR'] : [];
    case 'ASIGNADA':
      return [
        ...(esAdmin ? (['ASIGNAR'] as const) : []),
        ...(esAdmin || esAsignado ? (['INICIAR'] as const) : []),
      ];
    case 'EN_CURSO':
      return [
        ...(esAdmin ? (['ASIGNAR'] as const) : []),
        ...(esAdmin || esAsignado ? (['RESOLVER'] as const) : []),
      ];
    case 'RESUELTA':
      return esAdmin || esReportero ? ['CERRAR', 'REABRIR'] : [];
    case 'CERRADA':
    case 'RECHAZADA':
      return [];
  }
}

/** Una incidencia terminal ya no admite comentarios (409 en el backend). */
export function puedeComentar(estado: EstadoIncidencia): boolean {
  return !ESTADOS_TERMINALES.includes(estado);
}

/** Ni subir ni borrar fotos en estados terminales (409 en el backend). */
export function puedeAdjuntarFotos(estado: EstadoIncidencia): boolean {
  return !ESTADOS_TERMINALES.includes(estado);
}

/** Qué es el usuario respecto a esa incidencia, con las mismas reglas que `AutorizacionClub` del backend. */
export function contextoDeIncidencia(
  incidencia: Pick<IncidenciaResumen, 'clubId' | 'reportadaPor' | 'asignadaA'>,
  usuario: Usuario | null,
): ContextoIncidencia {
  if (usuario === null) {
    return { esAdmin: false, esAsignado: false, esReportero: false };
  }
  const delClub = usuario.clubId === incidencia.clubId;
  return {
    esAdmin: usuario.rol === 'ADMIN_CLUB' && delClub,
    esAsignado:
      incidencia.asignadaA?.id === usuario.id && usuario.rol === 'MANTENIMIENTO' && delClub,
    esReportero: incidencia.reportadaPor.id === usuario.id,
  };
}
