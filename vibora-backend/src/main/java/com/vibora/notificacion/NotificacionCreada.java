package com.vibora.notificacion;

import java.time.Instant;

/**
 * Evento de aplicacion publicado UNA vez por cada notificacion guardada (dentro de la transaccion que la crea).
 * Quien quiera enviarla en tiempo real debe escuchar con
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}, para no avisar de algo que luego se revierte.
 * {@code usuarioEmail} es el nombre con el que el destinatario se autentica (subject del JWT).
 */
public record NotificacionCreada(Long id, Long usuarioId, String usuarioEmail, TipoNotificacion tipo, String mensaje,
		Long reservaId, Instant creadaEn) {
}
