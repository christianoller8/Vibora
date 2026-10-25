import { Rol } from './usuario.model';

export type CategoriaIncidencia = 'RED' | 'ILUMINACION' | 'CRISTAL' | 'SUELO' | 'OTRA';

export const CATEGORIAS_INCIDENCIA: readonly CategoriaIncidencia[] = [
  'RED',
  'ILUMINACION',
  'CRISTAL',
  'SUELO',
  'OTRA',
];

export const ETIQUETA_CATEGORIA: Record<CategoriaIncidencia, string> = {
  RED: 'Red',
  ILUMINACION: 'Iluminación',
  CRISTAL: 'Cristal',
  SUELO: 'Suelo',
  OTRA: 'Otra',
};

export type PrioridadIncidencia = 'BAJA' | 'MEDIA' | 'ALTA' | 'URGENTE';

export const PRIORIDADES_INCIDENCIA: readonly PrioridadIncidencia[] = [
  'BAJA',
  'MEDIA',
  'ALTA',
  'URGENTE',
];

export const ETIQUETA_PRIORIDAD: Record<PrioridadIncidencia, string> = {
  BAJA: 'Baja',
  MEDIA: 'Media',
  ALTA: 'Alta',
  URGENTE: 'Urgente',
};

export type EstadoIncidencia =
  'ABIERTA' | 'ASIGNADA' | 'EN_CURSO' | 'RESUELTA' | 'CERRADA' | 'RECHAZADA';

export const ESTADOS_INCIDENCIA: readonly EstadoIncidencia[] = [
  'ABIERTA',
  'ASIGNADA',
  'EN_CURSO',
  'RESUELTA',
  'CERRADA',
  'RECHAZADA',
];

/** Estados finales: la incidencia ya no cambia, no admite comentarios ni fotos. */
export const ESTADOS_TERMINALES: readonly EstadoIncidencia[] = ['CERRADA', 'RECHAZADA'];

export const ETIQUETA_ESTADO_INCIDENCIA: Record<EstadoIncidencia, string> = {
  ABIERTA: 'Abierta',
  ASIGNADA: 'Asignada',
  EN_CURSO: 'En curso',
  RESUELTA: 'Resuelta',
  CERRADA: 'Cerrada',
  RECHAZADA: 'Rechazada',
};

export type TipoEventoIncidencia =
  | 'CREADA'
  | 'ASIGNADA'
  | 'ESTADO'
  | 'COMENTARIO'
  | 'PRIORIDAD'
  | 'FOTO_ANADIDA'
  | 'FOTO_ELIMINADA'
  | 'BLOQUEO_CREADO'
  | 'BLOQUEO_LEVANTADO';

/** Persona implicada en una incidencia: solo id y nombre. */
export interface PersonaIncidencia {
  id: number;
  nombre: string;
}

/** Fila del listado (sin descripción ni historial). */
export interface IncidenciaResumen {
  id: number;
  clubId: number;
  pistaId: number | null;
  pistaNombre: string | null;
  titulo: string;
  categoria: CategoriaIncidencia;
  prioridad: PrioridadIncidencia;
  estado: EstadoIncidencia;
  reportadaPor: PersonaIncidencia;
  asignadaA: PersonaIncidencia | null;
  /** Instantes ISO-8601 (UTC). */
  creadaEn: string;
  actualizadaEn: string;
}

export interface EventoIncidencia {
  id: number;
  tipo: TipoEventoIncidencia;
  usuario: PersonaIncidencia;
  /** Estado (o prioridad, en eventos PRIORIDAD) anterior, tal como lo guarda el backend. */
  estadoAnterior: string | null;
  estadoNuevo: string | null;
  texto: string | null;
  creadoEn: string;
}

export interface FotoIncidencia {
  id: number;
  nombreOriginal: string;
  tipoContenido: string;
  /** Bytes. */
  tamano: number;
  creadaEn: string;
  subidaPor: PersonaIncidencia;
}

/** Bloqueo de pista activo ligado a la incidencia (`hasta` nulo = sin fin). */
export interface BloqueoActivo {
  id: number;
  desde: string;
  hasta: string | null;
  motivo: string;
}

export interface IncidenciaDetalle extends IncidenciaResumen {
  descripcion: string;
  resueltaEn: string | null;
  eventos: EventoIncidencia[];
  fotos: FotoIncidencia[];
  bloqueoActivo: BloqueoActivo | null;
}

export interface IncidenciaRequest {
  titulo: string;
  descripcion: string;
  categoria: CategoriaIncidencia;
  pistaId: number | null;
}

export interface AsignarIncidenciaRequest {
  usuarioId: number;
  prioridad?: PrioridadIncidencia;
}

export interface CambioEstadoRequest {
  estado: EstadoIncidencia;
  comentario?: string;
}

export interface FiltrosIncidencias {
  estado?: EstadoIncidencia;
  pistaId?: number;
  asignadaA?: number;
  page?: number;
  limit?: number;
}

/** Miembro del personal de un club (`GET /api/clubes/{id}/personal`, solo el administrador). */
export interface MiembroPersonal {
  usuarioId: number;
  nombre: string | null;
  apellidos: string;
  email: string;
  rol: Rol;
}

export const MAX_FOTOS_INCIDENCIA = 4;
export const TAMANO_MAX_FOTO = 5 * 1024 * 1024;
export const TIPOS_FOTO_PERMITIDOS: readonly string[] = ['image/jpeg', 'image/png', 'image/webp'];
