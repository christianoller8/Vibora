export type TipoPista = 'INTERIOR' | 'EXTERIOR' | 'CUBIERTA';

export interface Pista {
  id: number;
  nombre: string;
  tipo: TipoPista;
  activa: boolean;
  clubId: number;
}
