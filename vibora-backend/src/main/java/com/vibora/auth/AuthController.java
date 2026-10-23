package com.vibora.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	@PostMapping("/registro")
	@ResponseStatus(HttpStatus.CREATED)
	public UsuarioResumen registro(@Valid @RequestBody RegistroRequest peticion) {
		return authService.registrar(peticion);
	}

	@PostMapping("/login")
	public TokenResponse login(@Valid @RequestBody LoginRequest peticion) {
		return authService.login(peticion);
	}

	/** El nombre de la autenticacion es el subject del JWT (el email). */
	@GetMapping("/me")
	public UsuarioResumen me(Authentication autenticacion) {
		return authService.perfil(autenticacion.getName());
	}
}
