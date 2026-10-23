package com.vibora.common.exception;

/** Login fallido o token de un usuario que ya no puede autenticarse. Mensaje generico a proposito. */
public class CredencialesInvalidasException extends RuntimeException {

	public static final String MENSAJE = "Credenciales inválidas";

	public CredencialesInvalidasException() {
		super(MENSAJE);
	}
}
