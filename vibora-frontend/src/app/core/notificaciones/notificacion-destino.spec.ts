import { TipoNotificacion } from '../../shared/models/notificacion.model';
import { destinoNotificacion } from './notificacion-destino';

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
});
