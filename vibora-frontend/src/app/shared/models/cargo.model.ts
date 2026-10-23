export type EstadoCargo = 'PENDIENTE' | 'PAGADO' | 'ANULADO' | 'REEMBOLSADO';

export interface UsuarioCargo {
  id: number;
  nombre: string;
  apellidos: string | null;
}

export interface Cargo {
  id: number;
  reservaId: number;
  clubNombre: string;
  pistaNombre: string;
  /** Instante ISO-8601 (UTC) del inicio del partido. */
  inicio: string;
  importe: number;
  estado: EstadoCargo;
  deudor: UsuarioCargo;
  acreedor: UsuarioCargo;
  /** Cuándo se pagó (null si nunca se pagó). */
  pagadoEn: string | null;
}

export interface SaldoUsuario {
  usuarioId: number;
  nombre: string;
  apellidos: string | null;
  debo: number;
  meDeben: number;
  /** meDeben - debo. */
  neto: number;
}

export interface Saldos {
  saldos: SaldoUsuario[];
  totalDebo: number;
  totalMeDeben: number;
  totalNeto: number;
}
