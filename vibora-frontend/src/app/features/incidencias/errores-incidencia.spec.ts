import { HttpErrorResponse } from '@angular/common/http';
import { esConflictoDeVersion, mensajeDeAccion } from './errores-incidencia';

const http = (status: number, error?: unknown): HttpErrorResponse =>
  new HttpErrorResponse({ status, error });

describe('esConflictoDeVersion', () => {
  it('es un 409 con el mensaje de bloqueo optimista del backend', () => {
    expect(
      esConflictoDeVersion(
        http(409, { error: 'La incidencia fue modificada por otra persona, recarga' }),
      ),
    ).toBe(true);
  });

  it('otro 409 (regla de negocio) no lo es', () => {
    expect(
      esConflictoDeVersion(http(409, { error: 'No se puede pasar de Abierta a Cerrada' })),
    ).toBe(false);
  });

  it('el mismo texto con otro código tampoco', () => {
    expect(esConflictoDeVersion(http(400, { error: 'modificada por otra persona' }))).toBe(false);
  });

  it('un 409 sin cuerpo, o algo que no es un error HTTP, no lo es', () => {
    expect(esConflictoDeVersion(http(409))).toBe(false);
    expect(esConflictoDeVersion(new Error('modificada por otra persona'))).toBe(false);
    expect(esConflictoDeVersion(null)).toBe(false);
  });
});

describe('mensajeDeAccion', () => {
  it('usa el mensaje del backend cuando lo hay', () => {
    expect(mensajeDeAccion(http(409, { error: 'No se puede pasar de A a B' }), 'defecto')).toBe(
      'No se puede pasar de A a B',
    );
  });

  it('un 403 sin mensaje dice que no tiene permiso', () => {
    expect(mensajeDeAccion(http(403), 'defecto')).toBe('No tienes permiso para hacer esto.');
  });

  it('un 403 CON mensaje del backend conserva el del backend', () => {
    expect(mensajeDeAccion(http(403, { error: 'Acceso denegado' }), 'defecto')).toBe(
      'Acceso denegado',
    );
  });

  it('un 404 sin mensaje explica que la incidencia ya no está disponible', () => {
    expect(mensajeDeAccion(http(404), 'defecto')).toBe(
      'La incidencia ya no existe o no tienes acceso a ella.',
    );
  });

  it('cae en el texto por defecto cuando no hay nada mejor', () => {
    expect(mensajeDeAccion(http(500), 'defecto')).toBe('defecto');
    expect(mensajeDeAccion(new Error('x'), 'defecto')).toBe('defecto');
  });
});
