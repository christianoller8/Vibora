// Utilidades de fechas en hora local de Madrid (el backend interpreta todo en Europe/Madrid).
// Sin dependencias: solo Intl, que ya incluye la base de datos de zonas horarias.

export const ZONA = 'Europe/Madrid';

const formateador = new Intl.DateTimeFormat('en-GB', {
  timeZone: ZONA,
  hourCycle: 'h23',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
});

const RE_FECHA = /^(\d{4})-(\d{2})-(\d{2})$/;
const RE_HORA = /^([01]\d|2[0-3]):([0-5]\d)$/;

/** Componentes de pared (en Madrid) de un instante. */
function componentesMadrid(instante) {
  const partes = Object.fromEntries(
    formateador.formatToParts(instante).map((p) => [p.type, p.value]),
  );
  return {
    anyo: Number(partes.year),
    mes: Number(partes.month),
    dia: Number(partes.day),
    hora: Number(partes.hour),
    minuto: Number(partes.minute),
    segundo: Number(partes.second),
  };
}

/** Desfase (ms) de Madrid respecto a UTC en un instante dado: +1h en invierno, +2h en verano. */
function desfaseMadridMs(instante) {
  const c = componentesMadrid(instante);
  const comoUtc = Date.UTC(c.anyo, c.mes - 1, c.dia, c.hora, c.minuto, c.segundo);
  return comoUtc - Math.floor(instante.getTime() / 1000) * 1000;
}

const dos = (n) => String(n).padStart(2, '0');

/**
 * Instante (Date) que corresponde a "fecha 'YYYY-MM-DD' y hora 'HH:mm' en hora local de Madrid".
 * Se resuelve el desfase dos veces para acertar tambien cerca de los cambios de hora.
 */
export function instanteMadrid(fecha, hora) {
  const f = RE_FECHA.exec(fecha);
  const h = RE_HORA.exec(hora);
  if (!f || !h) {
    throw new Error(`Fecha u hora invalida: "${fecha}" "${hora}" (se esperaba YYYY-MM-DD y HH:mm)`);
  }
  const pared = Date.UTC(Number(f[1]), Number(f[2]) - 1, Number(f[3]), Number(h[1]), Number(h[2]));
  let instante = new Date(pared - desfaseMadridMs(new Date(pared)));
  instante = new Date(pared - desfaseMadridMs(instante));
  return instante;
}

/** { fecha: 'YYYY-MM-DD', hora: 'HH:mm' } de un instante, en hora local de Madrid. */
export function fechaYHoraMadrid(instante) {
  const c = componentesMadrid(instante);
  return { fecha: `${c.anyo}-${dos(c.mes)}-${dos(c.dia)}`, hora: `${dos(c.hora)}:${dos(c.minuto)}` };
}

/** Fecha 'YYYY-MM-DD' de "hoy (en Madrid) + dias" por calendario, no por multiplos de 24h. */
export function fechaMadridMasDias(ahora, dias) {
  const { fecha } = fechaYHoraMadrid(ahora);
  const [a, m, d] = fecha.split('-').map(Number);
  const suma = new Date(Date.UTC(a, m - 1, d + dias));
  return `${suma.getUTCFullYear()}-${dos(suma.getUTCMonth() + 1)}-${dos(suma.getUTCDate())}`;
}
