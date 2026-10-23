package com.vibora.websocket;

import com.vibora.notificacion.NotificacionCreada;
import com.vibora.notificacion.NotificacionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Empuja cada notificacion nueva a su destinatario por /user/queue/notificaciones, SOLO tras el commit (lo que se
 * revierte no se avisa). La notificacion guardada sigue siendo la fuente de verdad (REST): el push es un extra, asi
 * que un fallo al enviarlo se registra y nunca rompe la peticion que lo origino.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class NotificacionPush {

	static final String DESTINO = "/queue/notificaciones";

	private final SimpMessagingTemplate plantilla;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void alCrearse(NotificacionCreada evento) {
		try {
			// recien creada: nunca esta leida. Mismo JSON que GET /api/notificaciones
			NotificacionResponse cuerpo = new NotificacionResponse(evento.id(), evento.tipo(), evento.mensaje(),
					evento.reservaId(), false, evento.creadaEn());
			plantilla.convertAndSendToUser(evento.usuarioEmail(), DESTINO, cuerpo);
		} catch (RuntimeException ex) {
			log.warn("No se pudo empujar por WebSocket la notificación {}: {}", evento.id(), ex.toString());
		}
	}
}
