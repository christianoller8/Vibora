import { ZONA_CLUB } from './fecha';

const SEGUNDO = 1000;
const MINUTO = 60 * SEGUNDO;
const HORA = 60 * MINUTO;
const DIA = 24 * HORA;
const SEMANA = 7 * DIA;

const formatoFechaCorta = new Intl.DateTimeFormat('es-ES', {
  timeZone: ZONA_CLUB,
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
});

/**
 * Tiempo transcurrido en español: "hace un momento", "hace 5 min", "hace 3 h", "hace 2 días". A partir de una
 * semana, la fecha corta (dd/mm/aaaa) según el calendario de Madrid. Un instante futuro cuenta como "ahora".
 */
export function tiempoRelativo(instanteIso: string, ahora: number = Date.now()): string {
  const instante = new Date(instanteIso).getTime();
  const transcurrido = ahora - instante;

  if (transcurrido < MINUTO) {
    return 'hace un momento';
  }
  if (transcurrido < HORA) {
    return `hace ${Math.floor(transcurrido / MINUTO)} min`;
  }
  if (transcurrido < DIA) {
    return `hace ${Math.floor(transcurrido / HORA)} h`;
  }
  if (transcurrido < SEMANA) {
    const dias = Math.floor(transcurrido / DIA);
    return dias === 1 ? 'hace 1 día' : `hace ${dias} días`;
  }
  return formatoFechaCorta.format(instante);
}
