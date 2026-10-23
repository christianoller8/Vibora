import { FormControl, FormGroup } from '@angular/forms';
import { aHoraCorta, rangoHorasValidator } from './rango-horas';

function grupo(inicio: string, fin: string): FormGroup {
  return new FormGroup(
    { inicio: new FormControl(inicio), fin: new FormControl(fin) },
    { validators: rangoHorasValidator('inicio', 'fin') },
  );
}

describe('rango de horas', () => {
  describe('aHoraCorta', () => {
    it('recorta los segundos del LocalTime del backend', () => {
      expect(aHoraCorta('09:00:00')).toBe('09:00');
    });

    it('deja intacta una hora que ya es HH:mm', () => {
      expect(aHoraCorta('22:30')).toBe('22:30');
    });
  });

  describe('rangoHorasValidator', () => {
    it('acepta un fin posterior al inicio', () => {
      expect(grupo('09:00', '22:00').errors).toBeNull();
    });

    it('rechaza un fin igual al inicio', () => {
      expect(grupo('09:00', '09:00').hasError('rangoHoras')).toBe(true);
    });

    it('rechaza un fin anterior al inicio', () => {
      expect(grupo('22:00', '09:00').hasError('rangoHoras')).toBe(true);
    });

    it('compara horas con y sin segundos', () => {
      expect(grupo('09:00:00', '09:00').hasError('rangoHoras')).toBe(true);
      expect(grupo('09:00:00', '09:30').errors).toBeNull();
    });

    it('no se queja si falta alguna hora (eso lo dice "obligatorio")', () => {
      expect(grupo('', '09:00').errors).toBeNull();
      expect(grupo('09:00', '').errors).toBeNull();
    });
  });
});
