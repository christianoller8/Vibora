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

const TODOS_LOS_TIPOS: TipoNotificacion[] = [
  'JUGADOR_SE_APUNTO',
  'JUGADOR_SALIO',
  'PARTIDO_COMPLETO',
  'CARGO_NUEVO',
  'PAGO_RECIBIDO',
  'RESERVA_CANCELADA',
  'FACTURA_RECTIFICATIVA',
  ...TIPOS_PERSONAL_E_INCIDENCIAS,
];

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

  it('cubre los 13 tipos conocidos', () => {
    expect(new Set(TODOS_LOS_TIPOS).size).toBe(13);
  });

  it('FACTURA_RECTIFICATIVA lleva a /agenda hasta que exista la pantalla de facturas', () => {
    expect(destinoNotificacion('FACTURA_RECTIFICATIVA')).toBe('/agenda');
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
