package com.vibora.websocket;

import org.springframework.messaging.MessagingException;

/**
 * Un frame STOMP rechazado. Es una MessagingException para que el canal la relance tal cual y el cliente reciba un
 * ERROR cuyo "message" es exactamente este texto (generico, sin detalles internos).
 */
class AccesoStompDenegadoException extends MessagingException {

	static final String NO_AUTENTICADO = "No autenticado";
	static final String ACCESO_DENEGADO = "Acceso denegado";

	AccesoStompDenegadoException(String mensaje) {
		super(mensaje);
	}
}
