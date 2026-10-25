import { estadoBloqueo } from './bloqueo-estado';

// Reloj fijo: 15 de julio de 2026, 12:00 UTC
const AHORA = Date.parse('2026-07-15T12:00:00Z');

describe('estadoBloqueo', () => {
  it('sin levantar y sin fecha de fin está activo', () => {
    expect(estadoBloqueo({ levantadoEn: null, hasta: null }, AHORA)).toBe('ACTIVO');
  });

  it('sin levantar y con fin futuro está activo', () => {
    expect(estadoBloqueo({ levantadoEn: null, hasta: '2026-07-15T12:00:01Z' }, AHORA)).toBe(
      'ACTIVO',
    );
  });

  it('con el fin exactamente ahora ya está caducado (el fin es exclusivo, como en el backend)', () => {
    expect(estadoBloqueo({ levantadoEn: null, hasta: '2026-07-15T12:00:00Z' }, AHORA)).toBe(
      'CADUCADO',
    );
  });

  it('sin levantar y con fin pasado está caducado', () => {
    expect(estadoBloqueo({ levantadoEn: null, hasta: '2026-07-15T11:59:59Z' }, AHORA)).toBe(
      'CADUCADO',
    );
  });

  it('levantado manda sobre todo lo demás, incluso con fin futuro o pasado', () => {
    const levantadoEn = '2026-07-14T09:00:00Z';
    expect(estadoBloqueo({ levantadoEn, hasta: null }, AHORA)).toBe('LEVANTADO');
    expect(estadoBloqueo({ levantadoEn, hasta: '2026-08-01T00:00:00Z' }, AHORA)).toBe('LEVANTADO');
    expect(estadoBloqueo({ levantadoEn, hasta: '2026-07-01T00:00:00Z' }, AHORA)).toBe('LEVANTADO');
  });

  it('compara instantes, no textos: un fin con otro formato de zona cuenta igual', () => {
    expect(estadoBloqueo({ levantadoEn: null, hasta: '2026-07-15T14:00:00+02:00' }, AHORA)).toBe(
      'CADUCADO',
    );
    expect(estadoBloqueo({ levantadoEn: null, hasta: '2026-07-15T14:00:01+02:00' }, AHORA)).toBe(
      'ACTIVO',
    );
  });

  it('sin reloj explícito usa la hora actual', () => {
    const ayer = new Date(Date.now() - 86_400_000).toISOString();
    const manana = new Date(Date.now() + 86_400_000).toISOString();
    expect(estadoBloqueo({ levantadoEn: null, hasta: ayer })).toBe('CADUCADO');
    expect(estadoBloqueo({ levantadoEn: null, hasta: manana })).toBe('ACTIVO');
  });
});
