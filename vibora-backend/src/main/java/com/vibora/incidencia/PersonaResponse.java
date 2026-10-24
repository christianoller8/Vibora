package com.vibora.incidencia;

import com.vibora.usuario.Usuario;

/** Persona implicada en una incidencia: solo id y nombre (nunca email ni datos de cuenta). */
public record PersonaResponse(Long id, String nombre) {

	public static PersonaResponse de(Usuario usuario) {
		return usuario == null ? null : new PersonaResponse(usuario.getId(), usuario.nombreCompleto());
	}
}
