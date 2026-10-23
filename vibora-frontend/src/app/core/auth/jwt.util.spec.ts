import { crearJwt, segundosDesdeAhora } from '../../testing/test-utils';
import { expiraEnMs, tokenCaducado } from './jwt.util';

describe('jwt.util', () => {
  it('lee el exp del payload en milisegundos', () => {
    expect(expiraEnMs(crearJwt(1_800_000_000))).toBe(1_800_000_000_000);
  });

  it('devuelve null si el token está mal formado', () => {
    expect(expiraEnMs('no-es-un-jwt')).toBeNull();
    expect(expiraEnMs('a.%%%.c')).toBeNull();
  });

  it('devuelve null si el payload no trae exp', () => {
    const sinExp = `${btoa('{}')}.${btoa('{"sub":"x"}')}.f`;
    expect(expiraEnMs(sinExp)).toBeNull();
  });

  it('considera caducado un token con exp en el pasado', () => {
    expect(tokenCaducado(crearJwt(segundosDesdeAhora(-60)))).toBe(true);
  });

  it('considera vigente un token con exp en el futuro', () => {
    expect(tokenCaducado(crearJwt(segundosDesdeAhora(3600)))).toBe(false);
  });

  it('trata un token ilegible como caducado', () => {
    expect(tokenCaducado('basura')).toBe(true);
  });
});
