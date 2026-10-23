import { TipoNotificacion } from '../../shared/models/notificacion.model';

/** Pantalla a la que lleva cada tipo de notificación. */
export function destinoNotificacion(tipo: TipoNotificacion): string {
  switch (tipo) {
    case 'CARGO_NUEVO':
    case 'PAGO_RECIBIDO':
      return '/deudas';
    case 'JUGADOR_SE_APUNTO':
    case 'JUGADOR_SALIO':
    case 'PARTIDO_COMPLETO':
    case 'RESERVA_CANCELADA':
      return '/mis-reservas';
  }
}
