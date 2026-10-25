import {
  fechaAString,
  formatearFecha,
  formatearFechaHora,
  formatearFechaLarga,
  formatearHora,
  formatearPrecio,
  formatearRangoHoras,
  hoyEnMadrid,
  madridAInstante,
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

  describe('formatearFechaHora', () => {
    it('une fecha y hora de Madrid ("15 de julio de 2026, 18:00")', () => {
      expect(formatearFechaHora('2026-07-15T16:00:00Z')).toBe('15 de julio de 2026, 18:00');
    });

    it('usa el día de Madrid, no el de UTC', () => {
      expect(formatearFechaHora('2026-07-15T22:30:00Z')).toBe('16 de julio de 2026, 00:30');
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

  describe('madridAInstante', () => {
    it('convierte la hora de reloj de Madrid de verano (UTC+2) a su instante UTC', () => {
      expect(madridAInstante('2026-07-15T18:00')).toBe('2026-07-15T16:00:00.000Z');
    });

    it('convierte la de invierno (UTC+1)', () => {
      expect(madridAInstante('2026-01-15T18:00')).toBe('2026-01-15T17:00:00.000Z');
    });

    it('respeta el salto de marzo: antes y después de las 02:00 locales', () => {
      expect(madridAInstante('2026-03-29T01:30')).toBe('2026-03-29T00:30:00.000Z'); // CET
      expect(madridAInstante('2026-03-29T03:30')).toBe('2026-03-29T01:30:00.000Z'); // CEST
    });

    it('una hora que no existe en Madrid (02:30 del salto de marzo) no es válida', () => {
      expect(madridAInstante('2026-03-29T02:30')).toBeNull();
    });

    it('en la hora repetida de octubre elige una de las dos, siempre la misma', () => {
      const instante = madridAInstante('2026-10-25T02:30');
      expect(instante).toBe('2026-10-25T01:30:00.000Z');
      expect(formatearHora(instante!)).toBe('02:30');
    });

    it('pasa de medianoche local al día siguiente en UTC solo cuando toca', () => {
      expect(madridAInstante('2026-07-16T00:30')).toBe('2026-07-15T22:30:00.000Z');
    });

    it.each(['', 'abc', '2026-07-15', '2026-07-15 18:00', '2026-02-30T10:00', '2026-13-01T10:00'])(
      'rechaza el texto no válido %j',
      (texto) => {
        expect(madridAInstante(texto)).toBeNull();
      },
    );

    it('no depende de la zona del navegador: ida y vuelta con el formateador de Madrid', () => {
      const instante = madridAInstante('2026-12-31T23:59')!;
      expect(formatearFechaHora(instante)).toBe('31 de diciembre de 2026, 23:59');
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
