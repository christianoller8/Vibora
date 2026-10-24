package com.vibora.common.exception;

/** Un fichero subido supera el tamano permitido: el manejador global lo traduce a 413. */
public class ArchivoDemasiadoGrandeException extends RuntimeException {

	public ArchivoDemasiadoGrandeException(String mensaje) {
		super(mensaje);
	}
}
