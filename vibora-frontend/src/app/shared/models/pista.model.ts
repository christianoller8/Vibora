export type TipoPista = 'INTERIOR' | 'EXTERIOR' | 'CUBIERTA';

export const TIPOS_PISTA: readonly TipoPista[] = ['INTERIOR', 'EXTERIOR', 'CUBIERTA'];

export const ETIQUETA_TIPO_PISTA: Record<TipoPista, string> = {
  INTERIOR: 'Interior',
  EXTERIOR: 'Exterior',
  CUBIERTA: 'Cubierta',
};

export interface Pista {
  id: number;
  nombre: string;
  tipo: TipoPista;
  activa: boolean;
  clubId: number;
}

/** Body de alta/modificación de pista. `activa` solo aplica al PUT (omitida = no cambiar). */
export interface PistaRequest {
  nombre: string;
  tipo: TipoPista;
  activa?: boolean;
}
