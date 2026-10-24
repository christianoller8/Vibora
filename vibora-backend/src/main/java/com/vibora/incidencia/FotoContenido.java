package com.vibora.incidencia;

/** Los bytes de una foto con el tipo VERIFICADO al subirla (el que se devuelve en Content-Type). */
public record FotoContenido(String tipoContenido, byte[] bytes) {
}
