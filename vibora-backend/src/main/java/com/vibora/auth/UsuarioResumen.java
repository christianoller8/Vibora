package com.vibora.auth;

import com.vibora.usuario.Rol;
import com.vibora.usuario.Usuario;

/** Vista publica de un usuario: nunca incluye el hash de la contraseña. */
public record UsuarioResumen(Long id, String email, String nombre, String apellidos, Rol rol) {

	public static UsuarioResumen de(Usuario u) {
		return new UsuarioResumen(u.getId(), u.getEmail(), u.getNombre(), u.getApellidos(), u.getRol());
	}
}
