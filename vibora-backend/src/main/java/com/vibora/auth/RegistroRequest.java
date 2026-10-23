package com.vibora.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body de registro. No incluye rol ni club: siempre se crea un JUGADOR. */
public record RegistroRequest(
		@NotBlank @Email @Size(max = 255) String email,
		// BCrypt admite 72 BYTES (no caracteres): ver PoliticaPassword
		@NotBlank @Size(min = 8) @PasswordMaxBytes String password,
		@Size(max = 255) String nombre,
		@NotBlank @Size(max = 255) String apellidos) {
}
