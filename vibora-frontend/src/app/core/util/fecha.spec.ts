import {
  fechaAString,
  formatearFecha,
  formatearFechaLarga,
  formatearHora,
  formatearPrecio,
  formatearRangoHoras,
  hoyEnMadrid,
} from './fecha';

describe('fecha (zona Europe/Madrid)', () => {
  describe('formatearHora', () => {
    it('muestra la hora de Madrid en verano (UTC+2)', () => {
      expect(formatearHora('2026-07-15T16:00:00Z')).toBe('18:00');
    });

    it('muestra la hora de Madrid en invierno (UTC+1)', () => {
      expect(formatearHora('2026-01-15T16:00:00Z')).toBe('17:00');
    });

    it('respeta el cambio de hora de marzo', () => {
      // 29/03/2026: a las 01:00 UTC se pasa de CET (+1) a CEST (+2)
      expect(formatearHora('2026-03-29T00:30:00Z')).toBe('01:30');
      expect(formatearHora('2026-03-29T01:30:00Z')).toBe('03:30');
    });
  });

  describe('formatearRangoHoras', () => {
    it('une inicio y fin con un guion largo', () => {
      expect(formatearRangoHoras('2026-07-15T16:00:00Z', '2026-07-15T17:30:00Z')).toBe(
        '18:00 – 19:30',
      );
    });
  });

  describe('formatearFechaLarga', () => {
    it('usa el día de Madrid, no el de UTC', () => {
      // 22:30 UTC del 15 es ya las 00:30 del 16 en Madrid (verano)
      expect(formatearFechaLarga('2026-07-15T22:30:00Z')).toContain('16 de julio de 2026');
    });

    it('incluye el día de la semana en español', () => {
      expect(formatearFechaLarga('2026-07-15T16:00:00Z')).toContain('miércoles');
    });
  });

  describe('formatearFecha', () => {
    it('da día, mes y año sin día de la semana, en Madrid', () => {
      expect(formatearFecha('2026-07-10T09:30:00Z')).toBe('10 de julio de 2026');
      expect(formatearFecha('2026-07-15T22:30:00Z')).toBe('16 de julio de 2026');
    });
  });

  describe('formatearPrecio', () => {
    it('formatea en euros con coma decimal', () => {
      expect(formatearPrecio(12.5)).toMatch(/^12,50\s€$/);
    });

    it('devuelve un guion cuando no hay precio', () => {
      expect(formatearPrecio(null)).toBe('—');
    });
  });

  describe('fechaAString', () => {
    it('usa las partes locales de la fecha (no toISOString)', () => {
      // 00:30 locales del 15 de marzo: en zonas por delante de UTC, toISOString() daría el 14
      const medianocheLocal = new Date(2026, 2, 15, 0, 30);
      expect(fechaAString(medianocheLocal)).toBe('2026-03-15');
    });

    it('rellena mes y día con ceros', () => {
      expect(fechaAString(new Date(2026, 0, 5, 12, 0))).toBe('2026-01-05');
    });

    it('conserva el último día del año a última hora', () => {
      expect(fechaAString(new Date(2026, 11, 31, 23, 59))).toBe('2026-12-31');
    });
  });

  describe('hoyEnMadrid', () => {
    it('devuelve la fecha de Madrid cuando UTC todavía está en el día anterior', () => {
      expect(hoyEnMadrid(new Date('2026-03-14T23:30:00Z'))).toBe('2026-03-15');
    });

    it('devuelve la fecha de Madrid cuando es verano y pasa de medianoche', () => {
      expect(hoyEnMadrid(new Date('2026-07-15T22:30:00Z'))).toBe('2026-07-16');
    });

    it('no adelanta el día antes de la medianoche de Madrid', () => {
      expect(hoyEnMadrid(new Date('2026-07-15T21:30:00Z'))).toBe('2026-07-15');
    });
  });
});
