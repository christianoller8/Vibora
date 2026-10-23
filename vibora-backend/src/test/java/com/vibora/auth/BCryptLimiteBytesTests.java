package com.vibora.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Documenta, con la version de Spring Security del proyecto, que hace BCrypt con mas de 72 BYTES
 * (no caracteres). De esto depende por que la API debe rechazarlo ella misma.
 */
class BCryptLimiteBytesTests {

	private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);

	@Test
	void conMasDe72Bytes_encodeLanzaExcepcion() {
		String ochentaBytes = "ñ".repeat(40); // 40 caracteres, 80 bytes en UTF-8
		Throwable error = catchThrowable(() -> encoder.encode(ochentaBytes));

		System.out.println("BCRYPT >72 bytes en encode: " + error);
		assertThat(error).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void conMasDe72Bytes_matchesNoLanzaPeroIgnoraElExceso() {
		String hash = encoder.encode("a".repeat(72));

		boolean coincide = encoder.matches("a".repeat(72) + "distinto", hash);

		System.out.println("BCRYPT >72 bytes en matches: coincide=" + coincide);
		// matches trunca en silencio a 72 bytes: por eso el login debe rechazar por si mismo lo que supere el limite
		assertThat(coincide).isTrue();
	}

	@Test
	void conExactamente72Bytes_funciona() {
		String setentaYDos = "ñ".repeat(36);
		assertThat(encoder.matches(setentaYDos, encoder.encode(setentaYDos))).isTrue();
	}
}
