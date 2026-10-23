package com.vibora.auth;

import com.vibora.club.Club;
import com.vibora.usuario.Rol;
import com.vibora.usuario.Usuario;

/**
 * Vista publica de un usuario: nunca incluye el hash de la contraseña. {@code clubId}/{@code clubNombre} son nulos
 * para los jugadores (no pertenecen a ningun club).
 */
public record UsuarioResumen(Long id, String email, String nombre, String apellidos, Rol rol, Long clubId,
		String clubNombre) {

	/** Si el usuario tiene club, este debe estar ya cargado (ver {@code UsuarioRepository#findConClubByEmail}). */
	public static UsuarioResumen de(Usuario u) {
		Club club = u.getClub();
		return new UsuarioResumen(u.getId(), u.getEmail(), u.getNombre(), u.getApellidos(), u.getRol(),
				club == null ? null : club.getId(), club == null ? null : club.getNombre());
	}
}
