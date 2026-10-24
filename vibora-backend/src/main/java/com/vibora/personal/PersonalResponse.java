package com.vibora.personal;

import com.vibora.usuario.Rol;
import com.vibora.usuario.Usuario;

public record PersonalResponse(Long usuarioId, String nombre, String apellidos, String email, Rol rol) {

	public static PersonalResponse de(Usuario usuario) {
		return new PersonalResponse(usuario.getId(), usuario.getNombre(), usuario.getApellidos(), usuario.getEmail(),
				usuario.getRol());
	}
}
