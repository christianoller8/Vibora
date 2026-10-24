package com.vibora.common;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tope absoluto de las fechas de los filtros (puro, sin Spring). */
class LimitesFechaTests {

	@Test
	void nuloEsValido() {
		assertThatCode(() -> LimitesFecha.validar(null)).doesNotThrowAnyException();
	}

	@Test
	void losExtremosSonValidos() {
		assertThatCode(() -> LimitesFecha.validar(LocalDate.of(1970, 1, 1))).doesNotThrowAnyException();
		assertThatCode(() -> LimitesFecha.validar(LocalDate.of(2100, 12, 31))).doesNotThrowAnyException();
		assertThatCode(() -> LimitesFecha.validar(LocalDate.of(2030, 6, 15))).doesNotThrowAnyException();
	}

	@Test
	void unDiaFueraDeLosExtremosEsError() {
		assertThatThrownBy(() -> LimitesFecha.validar(LocalDate.of(1969, 12, 31))).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Las fechas deben estar entre 1970 y 2100");
		assertThatThrownBy(() -> LimitesFecha.validar(LocalDate.of(2101, 1, 1))).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Las fechas deben estar entre 1970 y 2100");
	}

	@Test
	void anioGigantescoOMinimoDelCalendarioSonError() {
		assertThatThrownBy(() -> LimitesFecha.validar(LocalDate.MAX)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> LimitesFecha.validar(LocalDate.MIN)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> LimitesFecha.validar(LocalDate.of(500000, 1, 1))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> LimitesFecha.validar(LocalDate.of(1, 1, 1))).isInstanceOf(IllegalArgumentException.class);
	}
}
