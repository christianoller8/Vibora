package com.vibora.common.exception;

/** El recurso entra en conflicto con el estado actual (ej. email ya registrado). */
public class ConflictoException extends RuntimeException {

	public ConflictoException(String mensaje) {
		super(mensaje);
	}
}
