import { tiempoRelativo } from './tiempo-relativo';

describe('tiempoRelativo', () => {
  const AHORA = Date.parse('2026-07-15T16:00:00Z');
  const hace = (ms: number): string => new Date(AHORA - ms).toISOString();
  const SEG = 1000;
  const MIN = 60 * SEG;
  const HORA = 60 * MIN;
  const DIA = 24 * HORA;

  it('menos de un minuto: "hace un momento"', () => {
    expect(tiempoRelativo(hace(0), AHORA)).toBe('hace un momento');
    expect(tiempoRelativo(hace(59 * SEG), AHORA)).toBe('hace un momento');
  });

  it('un instante futuro (reloj desfasado) también es "hace un momento"', () => {
    expect(tiempoRelativo(hace(-5 * MIN), AHORA)).toBe('hace un momento');
  });

  it('minutos: "hace 5 min"', () => {
    expect(tiempoRelativo(hace(MIN), AHORA)).toBe('hace 1 min');
    expect(tiempoRelativo(hace(5 * MIN), AHORA)).toBe('hace 5 min');
    expect(tiempoRelativo(hace(59 * MIN + 59 * SEG), AHORA)).toBe('hace 59 min');
  });

  it('horas: "hace 3 h"', () => {
    expect(tiempoRelativo(hace(HORA), AHORA)).toBe('hace 1 h');
    expect(tiempoRelativo(hace(3 * HORA + 20 * MIN), AHORA)).toBe('hace 3 h');
    expect(tiempoRelativo(hace(23 * HORA + 59 * MIN), AHORA)).toBe('hace 23 h');
  });

  it('días: singular y plural', () => {
    expect(tiempoRelativo(hace(DIA), AHORA)).toBe('hace 1 día');
    expect(tiempoRelativo(hace(3 * DIA), AHORA)).toBe('hace 3 días');
    expect(tiempoRelativo(hace(6 * DIA + 23 * HORA), AHORA)).toBe('hace 6 días');
  });

  it('a partir de una semana: fecha corta en hora de Madrid', () => {
    expect(tiempoRelativo('2026-07-01T10:00:00Z', AHORA)).toBe('01/07/2026');
  });

  it('usa el día de Madrid, no el de UTC, en la fecha corta', () => {
    // 23:30 UTC del 30/06 ya es 01:30 del 01/07 en Madrid (verano, UTC+2)
    expect(tiempoRelativo('2026-06-30T23:30:00Z', AHORA)).toBe('01/07/2026');
  });
});
