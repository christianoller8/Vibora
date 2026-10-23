package com.vibora.notificacion;

import java.time.Instant;

public record NotificacionResponse(Long id, TipoNotificacion tipo, String mensaje, Long reservaId, boolean leida,
		Instant creadaEn) {

	public static NotificacionResponse de(Notificacion n) {
		// getReserva().getId() no inicializa el proxy perezoso: solo lee el identificador
		return new NotificacionResponse(n.getId(), n.getTipo(), n.getMensaje(),
				n.getReserva() == null ? null : n.getReserva().getId(), n.isLeida(), n.getCreadaEn());
	}
}
