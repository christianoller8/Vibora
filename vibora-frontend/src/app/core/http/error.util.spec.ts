import { HttpErrorResponse } from '@angular/common/http';
import { mensajeDeError } from './error.util';

describe('mensajeDeError', () => {
  it('devuelve el mensaje del backend ({"error": "..."})', () => {
    const err = new HttpErrorResponse({ status: 409, error: { error: 'Email ya registrado' } });
    expect(mensajeDeError(err, 'Fallo')).toBe('Email ya registrado');
  });

  it('avisa de que no hay conexión cuando el status es 0', () => {
    const err = new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') });
    expect(mensajeDeError(err, 'Fallo')).toBe(
      'No se pudo conectar con el servidor. Inténtalo de nuevo.',
    );
  });

  it('usa el texto por defecto si el cuerpo no trae mensaje', () => {
    const err = new HttpErrorResponse({ status: 500, error: '<html>boom</html>' });
    expect(mensajeDeError(err, 'Algo falló')).toBe('Algo falló');
  });

  it('usa el texto por defecto con errores que no son HTTP', () => {
    expect(mensajeDeError(new Error('x'), 'Algo falló')).toBe('Algo falló');
    expect(mensajeDeError(undefined, 'Algo falló')).toBe('Algo falló');
  });
});
