package com.vibora.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.stereotype.Component;

/**
 * Ultima barrera del canal de salida hacia los clientes: un mensaje destinado a una sesion con el token caducado se
 * descarta (y la sesion se expulsa), pase lo que pase antes; y cuando se acaba de entregar el ERROR de una expulsion,
 * se cierra el socket (asi el cliente llega a leer el ERROR antes del cierre).
 */
@Component
@RequiredArgsConstructor
public class SalidaStompInterceptor implements ExecutorChannelInterceptor {

	private final SesionesWs sesiones;

	@Override
	public Message<?> preSend(Message<?> mensaje, MessageChannel canal) {
		if (SimpMessageHeaderAccessor.getMessageType(mensaje.getHeaders()) == SimpMessageType.MESSAGE) {
			String idSesion = SimpMessageHeaderAccessor.getSessionId(mensaje.getHeaders());
			if (idSesion != null && sesiones.estaCaducada(idSesion)) {
				sesiones.expulsar(idSesion);
				return null;
			}
		}
		return mensaje;
	}

	@Override
	public void afterMessageHandled(Message<?> mensaje, MessageChannel canal, MessageHandler manejador, Exception ex) {
		if (StompHeaderAccessor.wrap(mensaje).getCommand() == StompCommand.ERROR) {
			String idSesion = SimpMessageHeaderAccessor.getSessionId(mensaje.getHeaders());
			if (idSesion != null) {
				sesiones.cerrarSiExpulsada(idSesion);
			}
		}
	}
}
