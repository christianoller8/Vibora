package com.vibora.auth;

import java.time.Instant;

/** Resultado interno de firmar un JWT. */
public record TokenEmitido(String token, Instant expiraEn) {
}
