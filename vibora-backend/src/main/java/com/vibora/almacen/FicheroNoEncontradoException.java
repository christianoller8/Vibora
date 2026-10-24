package com.vibora.almacen;

/** El almacen no tiene ningun fichero con esa clave. */
public class FicheroNoEncontradoException extends RuntimeException {

	public FicheroNoEncontradoException(String clave) {
		super("No existe el fichero " + clave);
	}
}
