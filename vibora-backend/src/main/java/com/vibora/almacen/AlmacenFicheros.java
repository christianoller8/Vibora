package com.vibora.almacen;

import java.io.InputStream;

/**
 * Almacen de ficheros por clave. Las claves las genera el servidor (UUID aleatorios), nunca salen de lo que escribe
 * quien sube el fichero; aun asi, cada implementacion debe rechazar claves que no sean un nombre simple.
 */
public interface AlmacenFicheros {

	/** Guarda el contenido bajo la clave (si ya existia lo sustituye). Un fallo no deja un fichero a medias. */
	void guardar(String clave, byte[] contenido);

	/** Abre el fichero para leerlo; el llamador cierra el flujo. {@link FicheroNoEncontradoException} si no existe. */
	InputStream abrir(String clave);

	/** Borra el fichero; si no existe no hace nada. */
	void borrar(String clave);

	boolean existe(String clave);
}
