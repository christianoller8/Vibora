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

const formatoRelojMadrid = new Intl.DateTimeFormat('en-US', {
  timeZone: ZONA_CLUB,
  year: 'numeric',
  month: 'numeric',
  day: 'numeric',
  hour: 'numeric',
  minute: 'numeric',
  second: 'numeric',
  hourCycle: 'h23',
});

const formatoEuros = new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' });

/** "18:00" a partir de un instante ISO, en hora de Madrid. */
export function formatearHora(instanteIso: string): string {
  return formatoHora.format(new Date(instanteIso));
}

/** "15 de julio de 2026, 18:00" (hora de Madrid). */
export function formatearFechaHora(instanteIso: string): string {
  return `${formatearFecha(instanteIso)}, ${formatearHora(instanteIso)}`;
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

/**
 * Hora de reloj de Madrid ("2026-07-15T18:00", el valor de un `<input type="datetime-local">`) → instante ISO-8601
 * UTC. Devuelve null si el texto no es una fecha real o si esa hora no existe en Madrid (el salto de marzo).
 * No depende de la zona del navegador.
 */
export function madridAInstante(local: string): string | null {
  const partes = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(local);
  if (partes === null) {
    return null;
  }
  const [anio, mes, dia, hora, minuto] = partes.slice(1).map(Number);
  // La misma hora de reloj leída como si fuera UTC: sirve de punto de partida y para comprobar el calendario.
  const comoUtc = Date.UTC(anio, mes - 1, dia, hora, minuto);
  const normalizada = new Date(comoUtc);
  if (
    normalizada.getUTCFullYear() !== anio ||
    normalizada.getUTCMonth() !== mes - 1 ||
    normalizada.getUTCDate() !== dia ||
    normalizada.getUTCHours() !== hora
  ) {
    return null; // 30 de febrero, mes 13, hora 25...
  }
  // Dos pasadas: el desfase que corresponde al resultado puede no ser el del punto de partida cerca de un cambio de hora.
  let instante = comoUtc - desfaseMadrid(comoUtc);
  instante = comoUtc - desfaseMadrid(instante);
  if (instante + desfaseMadrid(instante) !== comoUtc) {
    return null; // la hora no existe en Madrid (el reloj salta de las 02:00 a las 03:00)
  }
  return new Date(instante).toISOString();
}

/** Diferencia entre la hora de reloj de Madrid y UTC en ese instante (ms): +1 h en invierno, +2 h en verano. */
function desfaseMadrid(instante: number): number {
  const partes = formatoRelojMadrid.formatToParts(new Date(instante));
  const valor = (tipo: string): number => Number(partes.find((p) => p.type === tipo)!.value);
  const relojComoUtc = Date.UTC(
    valor('year'),
    valor('month') - 1,
    valor('day'),
    valor('hour'),
    valor('minute'),
    valor('second'),
  );
  return relojComoUtc - Math.floor(instante / 1000) * 1000;
}

/** Fecha de hoy (YYYY-MM-DD) en Madrid, que es la que importa para reservar en un club. */
export function hoyEnMadrid(ahora: Date = new Date()): string {
  const partes = formatoPartesMadrid.formatToParts(ahora);
  const valor = (tipo: string): string => partes.find((p) => p.type === tipo)!.value;
  return `${valor('year')}-${valor('month')}-${valor('day')}`;
}
