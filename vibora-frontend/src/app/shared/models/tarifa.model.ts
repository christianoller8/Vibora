/** Nombre del enum java.time.DayOfWeek, que es lo que envía y espera el backend. */
export type DiaSemana =
  'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

/** De lunes a domingo (el orden en que se muestran, no el de java.time, que también empieza en lunes). */
export const DIAS_SEMANA: readonly DiaSemana[] = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
];

export const ETIQUETA_DIA: Record<DiaSemana, string> = {
  MONDAY: 'Lunes',
  TUESDAY: 'Martes',
  WEDNESDAY: 'Miércoles',
  THURSDAY: 'Jueves',
  FRIDAY: 'Viernes',
  SATURDAY: 'Sábado',
  SUNDAY: 'Domingo',
};

export type TipoTarifa = 'NORMAL' | 'HORA_PUNTA' | 'LUZ_ARTIFICIAL';

export const TIPOS_TARIFA: readonly TipoTarifa[] = ['NORMAL', 'HORA_PUNTA', 'LUZ_ARTIFICIAL'];

export const ETIQUETA_TIPO_TARIFA: Record<TipoTarifa, string> = {
  NORMAL: 'Normal',
  HORA_PUNTA: 'Hora punta',
  LUZ_ARTIFICIAL: 'Luz artificial',
};

export interface Tarifa {
  id: number;
  clubId: number;
  diaSemana: DiaSemana;
  /** LocalTime del backend: "HH:mm:ss". */
  horaInicio: string;
  horaFin: string;
  /** Euros por hora. */
  precioHora: number;
  tipo: TipoTarifa;
}

/** Body de alta/modificación de franja. Las horas se envían como "HH:mm". */
export interface TarifaRequest {
  diaSemana: DiaSemana;
  horaInicio: string;
  horaFin: string;
  precioHora: number;
  tipo: TipoTarifa;
}
