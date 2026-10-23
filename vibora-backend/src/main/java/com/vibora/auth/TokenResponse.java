package com.vibora.auth;

import java.time.Instant;

public record TokenResponse(String token, String tipo, Instant expiraEn) {

	public static TokenResponse bearer(TokenEmitido emitido) {
		return new TokenResponse(emitido.token(), "Bearer", emitido.expiraEn());
	}
}
