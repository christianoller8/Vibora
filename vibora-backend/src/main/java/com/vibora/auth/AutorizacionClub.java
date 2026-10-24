package com.vibora.auth;

import com.vibora.common.exception.CredencialesInvalidasException;
import com.vibora.usuario.Rol;
import com.vibora.usuario.Usuario;
import com.vibora.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Autorizacion "a nivel de club". El JWT solo lleva el rol, asi que saber si alguien es
 * administrador de ESTE club se comprueba contra la BD (usuario actual + su club), nunca contra el token.
 */
@Component
@RequiredArgsConstructor
public class AutorizacionClub {

	private final UsuarioRepository usuarios;

	/** Usuario actual segun el subject del JWT (email). 401 si ya no existe o esta dado de baja. */
	@Transactional(readOnly = true)
	public Usuario usuarioActual() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion == null || !autenticacion.isAuthenticated()) {
			throw new CredencialesInvalidasException();
		}
		return usuarios.findByEmail(autenticacion.getName())
				.filter(Usuario::isActivo)
				.orElseThrow(CredencialesInvalidasException::new);
	}

	/** true si el usuario actual es ADMIN_CLUB del club indicado. */
	@Transactional(readOnly = true)
	public boolean esAdminDe(Long clubId) {
		return esAdminDe(usuarioActual(), clubId);
	}

	/** 403 salvo que el usuario actual sea ADMIN_CLUB del club indicado. */
	@Transactional(readOnly = true)
	public Usuario exigirAdminDe(Long clubId) {
		Usuario usuario = usuarioActual();
		if (!esAdminDe(usuario, clubId)) {
			throw new AccessDeniedException("Acceso denegado");
		}
		return usuario;
	}

	public static boolean esAdminDe(Usuario usuario, Long clubId) {
		return usuario.getRol() == Rol.ADMIN_CLUB
				&& usuario.getClub() != null
				&& usuario.getClub().getId().equals(clubId);
	}

	/** true si el usuario es personal (monitor, mantenimiento o administrador) del club indicado. */
	public static boolean esPersonalDe(Usuario usuario, Long clubId) {
		return usuario.getRol() != Rol.JUGADOR
				&& usuario.getClub() != null
				&& usuario.getClub().getId().equals(clubId);
	}

	/** true si el usuario es de mantenimiento de ESE club. */
	public static boolean esMantenimientoDe(Usuario usuario, Long clubId) {
		return usuario.getRol() == Rol.MANTENIMIENTO
				&& usuario.getClub() != null
				&& usuario.getClub().getId().equals(clubId);
	}
}
