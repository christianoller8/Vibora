import { TipoNotificacion } from '../../shared/models/notificacion.model';
import { destinoNotificacion } from './notificacion-destino';

const TIPOS_PERSONAL_E_INCIDENCIAS = [
  'PERSONAL_ANADIDO',
  'PERSONAL_QUITADO',
  'INCIDENCIA_NUEVA',
  'INCIDENCIA_ASIGNADA',
  'INCIDENCIA_ESTADO',
  'INCIDENCIA_COMENTARIO',
] as TipoNotificacion[];

const TODOS_LOS_TIPOS = [
  'JUGADOR_SE_APUNTO',
  'JUGADOR_SALIO',
  'PARTIDO_COMPLETO',
  'CARGO_NUEVO',
  'PAGO_RECIBIDO',
  'RESERVA_CANCELADA',
  ...TIPOS_PERSONAL_E_INCIDENCIAS,
] as TipoNotificacion[];

describe('destinoNotificacion', () => {
  it.each<[TipoNotificacion, string]>([
    ['JUGADOR_SE_APUNTO', '/mis-reservas'],
    ['JUGADOR_SALIO', '/mis-reservas'],
    ['PARTIDO_COMPLETO', '/mis-reservas'],
    ['RESERVA_CANCELADA', '/mis-reservas'],
    ['CARGO_NUEVO', '/deudas'],
    ['PAGO_RECIBIDO', '/deudas'],
  ])('%s lleva a %s', (tipo, ruta) => {
    expect(destinoNotificacion(tipo)).toBe(ruta);
  });

  it('cubre los 12 tipos conocidos', () => {
    expect(new Set(TODOS_LOS_TIPOS).size).toBe(12);
  });

  it.each(TODOS_LOS_TIPOS)('%s resuelve a una ruta no vacía', (tipo) => {
    const ruta = destinoNotificacion(tipo);

    expect(typeof ruta).toBe('string');
    expect(ruta.startsWith('/')).toBe(true);
  });

  it.each(TIPOS_PERSONAL_E_INCIDENCIAS)('%s lleva a /agenda', (tipo) => {
    expect(destinoNotificacion(tipo)).toBe('/agenda');
  });

  it('un tipo desconocido de un backend más nuevo cae en /agenda', () => {
    expect(destinoNotificacion('TIPO_DEL_FUTURO' as TipoNotificacion)).toBe('/agenda');
  });
});
