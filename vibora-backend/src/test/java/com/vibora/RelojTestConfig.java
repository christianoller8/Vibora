package com.vibora;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Sustituye el Clock de produccion por uno mutable (@Primary). Arranca en 2030-01-01T00:00Z:
 * en el futuro respecto al reloj real, de modo que los JWT emitidos con este reloj siguen siendo
 * validos para el decodificador (que valida contra la hora real).
 */
@TestConfiguration
public class RelojTestConfig {

	public static final Instant AHORA_INICIAL = Instant.parse("2030-01-01T00:00:00Z");

	@Bean
	@Primary
	RelojMutable relojMutable() {
		return new RelojMutable(AHORA_INICIAL, ZoneOffset.UTC);
	}
}
