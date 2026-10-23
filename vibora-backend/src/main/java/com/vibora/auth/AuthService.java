package com.vibora.auth;

import com.vibora.common.exception.ConflictoException;
import com.vibora.common.exception.CredencialesInvalidasException;
import com.vibora.usuario.Rol;
import com.vibora.usuario.Usuario;
import com.vibora.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final UsuarioRepository usuarios;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	@Transactional
	public UsuarioResumen registrar(RegistroRequest peticion) {
		String email = normalizar(peticion.email());
		if (usuarios.existsByEmail(email)) {
			throw emailYaRegistrado();
		}
		Usuario usuario = new Usuario();
		usuario.setEmail(email);
		usuario.setPasswordHash(passwordEncoder.encode(PoliticaPassword.normalizar(peticion.password())));
		usuario.setNombre(peticion.nombre());
		usuario.setApellidos(peticion.apellidos());
		// El cliente nunca elige rol ni club: el auto-registro siempre crea un jugador
		usuario.setRol(Rol.JUGADOR);
		usuario.setActivo(true);
		try {
			return UsuarioResumen.de(usuarios.saveAndFlush(usuario));
		} catch (DataIntegrityViolationException ex) {
			// Carrera: otro registro con el mismo email entre el exists y el insert
			throw emailYaRegistrado();
		}
	}

	@Transactional(readOnly = true)
	public TokenResponse login(LoginRequest peticion) {
		Usuario usuario = usuarios.findByEmail(normalizar(peticion.email())).orElse(null);
		// Se comprueba siempre un hash (aunque el usuario no exista) para no filtrar por tiempos
		String hash = usuario != null ? usuario.getPasswordHash() : hashFicticio();
		String password = PoliticaPassword.normalizar(peticion.password());
		// BCrypt truncaria en silencio a 72 bytes: nada que supere el limite puede ser una contraseña valida
		boolean passwordOk = !PoliticaPassword.excedeLimite(password) && passwordEncoder.matches(password, hash);
		if (usuario == null || !passwordOk || !usuario.isActivo()) {
			throw new CredencialesInvalidasException();
		}
		return TokenResponse.bearer(jwtService.generar(usuario));
	}

	@Transactional(readOnly = true)
	public UsuarioResumen perfil(String email) {
		return usuarios.findConClubByEmail(email)
				.filter(Usuario::isActivo)
				.map(UsuarioResumen::de)
				.orElseThrow(CredencialesInvalidasException::new);
	}

	private static String normalizar(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private static ConflictoException emailYaRegistrado() {
		return new ConflictoException("El email ya está registrado");
	}

	private String hashFicticio() {
		return hashFicticio == null ? (hashFicticio = passwordEncoder.encode("ficticio-no-usar")) : hashFicticio;
	}

	private volatile String hashFicticio;
}
