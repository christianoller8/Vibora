/**
 * Utilidades de fecha y hora. Los clubes operan en Europe/Madrid y el backend manda instantes UTC,
 * así que SIEMPRE formateamos con esa zona explícita, sin depender de la zona del navegador.
 */
export const ZONA_CLUB = 'Europe/Madrid';

const formatoHora = new Intl.DateTimeFormat('es-ES', {
  timeZone: ZONA_CLUB,
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

const formatoFechaLarga = new Intl.DateTimeFormat('es-ES', {
  timeZone: ZONA_CLUB,
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
});

const formatoFecha = new Intl.DateTimeFormat('es-ES', {
  timeZone: ZONA_CLUB,
  day: 'numeric',
  month: 'long',
  year: 'numeric',
});

const formatoPartesMadrid = new Intl.DateTimeFormat('en-US', {
  timeZone: ZONA_CLUB,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

const formatoEuros = new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' });

/** "18:00" a partir de un instante ISO, en hora de Madrid. */
export function formatearHora(instanteIso: string): string {
  return formatoHora.format(new Date(instanteIso));
}

/** "18:00 – 19:30" (hora de Madrid). */
export function formatearRangoHoras(inicioIso: string, finIso: string): string {
  return `${formatearHora(inicioIso)} – ${formatearHora(finIso)}`;
}

/** "miércoles, 15 de julio de 2026" según el calendario de Madrid. */
export function formatearFechaLarga(instanteIso: string): string {
  return formatoFechaLarga.format(new Date(instanteIso));
}

/** "15 de julio de 2026" (sin día de la semana) según el calendario de Madrid. */
export function formatearFecha(instanteIso: string): string {
  return formatoFecha.format(new Date(instanteIso));
}

/** "12,50 €";"—" cuando no hay precio. */
export function formatearPrecio(precio: number | null): string {
  return precio === null ? '—' : formatoEuros.format(precio);
}

/**
 * YYYY-MM-DD con las partes LOCALES de la fecha. No usar toISOString(): convierte a UTC y, en zonas por
 * delante de UTC, la medianoche local cae en el día anterior.
 */
export function fechaAString(fecha: Date): string {
  const mes = String(fecha.getMonth() + 1).padStart(2, '0');
  const dia = String(fecha.getDate()).padStart(2, '0');
  return `${fecha.getFullYear()}-${mes}-${dia}`;
}

/** Fecha de hoy (YYYY-MM-DD) en Madrid, que es la que importa para reservar en un club. */
export function hoyEnMadrid(ahora: Date = new Date()): string {
  const partes = formatoPartesMadrid.formatToParts(ahora);
  const valor = (tipo: string): string => partes.find((p) => p.type === tipo)!.value;
  return `${valor('year')}-${valor('month')}-${valor('day')}`;
}
