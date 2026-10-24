package com.vibora.common;

import java.time.LocalDate;

/**
 * Fechas aceptadas en los filtros de las peticiones. Sin un tope absoluto, un anio gigantesco desborda al sumar un dia
 * ({@code +999999999-12-31}) o lo rechaza la base de datos como instante, y el cliente recibiria un 500 en vez de un 400.
 */
public final class LimitesFecha {

	public static final LocalDate MINIMA = LocalDate.of(1970, 1, 1);
	public static final LocalDate MAXIMA = LocalDate.of(2100, 12, 31);
	public static final String MENSAJE = "Las fechas deben estar entre 1970 y 2100";

	private LimitesFecha() {
	}

	/** Lanza {@link IllegalArgumentException} (400) si la fecha no es nula y cae fuera de [1970-01-01, 2100-12-31]. */
	public static void validar(LocalDate fecha) {
		if (fecha != null && (fecha.isBefore(MINIMA) || fecha.isAfter(MAXIMA))) {
			throw new IllegalArgumentException(MENSAJE);
		}
	}
}
