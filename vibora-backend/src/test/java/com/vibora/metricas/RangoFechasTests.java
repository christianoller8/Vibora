package com.vibora.metricas;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Resolucion y validacion del rango de fechas de las metricas (pura, sin Spring). */
class RangoFechasTests {

	private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

	private static Clock relojEn(String instante) {
		return Clock.fixed(Instant.parse(instante), ZoneOffset.UTC);
	}

	@Test
	void sinParametrosSonLosUltimos30DiasTerminandoHoy() {
		RangoFechas r = RangoFechas.resolver(null, null, relojEn("2030-01-15T12:00:00Z"), MADRID);

		assertThat(r.hasta()).isEqualTo(LocalDate.of(2030, 1, 15));
		assertThat(r.desde()).isEqualTo(LocalDate.of(2029, 12, 17));
		assertThat(r.dias()).isEqualTo(30);
	}

	@Test
	void hoyEsElDiaLocalDeMadridNoElDiaUtc() {
		// 22:30Z del 24-oct-2026 ya es el 25 en Madrid (CEST, +02:00)
		RangoFechas r = RangoFechas.resolver(null, null, relojEn("2026-10-24T22:30:00Z"), MADRID);

		assertThat(r.hasta()).isEqualTo(LocalDate.of(2026, 10, 25));
	}

	@Test
	void soloHastaDaLos30DiasAnteriores() {
		RangoFechas r = RangoFechas.resolver(null, LocalDate.of(2030, 3, 31), relojEn("2030-01-15T12:00:00Z"), MADRID);

		assertThat(r.desde()).isEqualTo(LocalDate.of(2030, 3, 2));
		assertThat(r.hasta()).isEqualTo(LocalDate.of(2030, 3, 31));
	}

	@Test
	void soloDesdeTerminaHoy() {
		RangoFechas r = RangoFechas.resolver(LocalDate.of(2030, 1, 1), null, relojEn("2030-01-15T12:00:00Z"), MADRID);

		assertThat(r.desde()).isEqualTo(LocalDate.of(2030, 1, 1));
		assertThat(r.hasta()).isEqualTo(LocalDate.of(2030, 1, 15));
		assertThat(r.dias()).isEqualTo(15);
	}

	@Test
	void desdePosteriorAHastaEsError() {
		assertThatThrownBy(() -> RangoFechas.resolver(LocalDate.of(2030, 1, 10), LocalDate.of(2030, 1, 9),
				relojEn("2030-01-15T12:00:00Z"), MADRID)).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("La fecha inicial no puede ser posterior a la final");
	}

	@Test
	void soloDesdeEnElFuturoEsError() {
		assertThatThrownBy(() -> RangoFechas.resolver(LocalDate.of(2030, 2, 1), null, relojEn("2030-01-15T12:00:00Z"), MADRID))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void unSoloDiaEsValido() {
		LocalDate dia = LocalDate.of(2030, 1, 7);
		RangoFechas r = RangoFechas.resolver(dia, dia, relojEn("2030-01-15T12:00:00Z"), MADRID);

		assertThat(r.dias()).isEqualTo(1);
		assertThat(r.fechas()).containsExactly(dia);
	}

	@Test
	void comoMaximo366Dias() {
		LocalDate desde = LocalDate.of(2029, 1, 1);
		// 2029-01-01 .. 2029-12-31 son 365 dias; hasta 2030-01-01 son 366 (limite exacto)
		RangoFechas limite = RangoFechas.resolver(desde, LocalDate.of(2030, 1, 1), relojEn("2030-06-01T00:00:00Z"), MADRID);
		assertThat(limite.dias()).isEqualTo(366);
		assertThat(limite.fechas()).hasSize(366);

		assertThatThrownBy(() -> RangoFechas.resolver(desde, LocalDate.of(2030, 1, 2), relojEn("2030-06-01T00:00:00Z"), MADRID))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("El rango no puede superar los 366 días");
	}

	@Test
	void fechasFueraDeLosLimitesAbsolutosSonErrorAunqueElRangoSeaCorto() {
		Clock reloj = relojEn("2030-01-15T12:00:00Z");

		// plusDays(1) desbordaria (DateTimeException) si no se rechazara antes
		assertThatThrownBy(() -> RangoFechas.resolver(null, LocalDate.of(999_999_999, 12, 31), reloj, MADRID))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("Las fechas deben estar entre 1970 y 2100");
		assertThatThrownBy(() -> RangoFechas.resolver(LocalDate.of(500000, 1, 1), LocalDate.of(500000, 1, 2), reloj, MADRID))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("Las fechas deben estar entre 1970 y 2100");
		assertThatThrownBy(() -> RangoFechas.resolver(LocalDate.of(1, 1, 1), null, reloj, MADRID))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("Las fechas deben estar entre 1970 y 2100");
		assertThatThrownBy(() -> RangoFechas.resolver(LocalDate.of(1969, 12, 31), LocalDate.of(1970, 1, 2), reloj, MADRID))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("Las fechas deben estar entre 1970 y 2100");
		assertThatThrownBy(() -> RangoFechas.resolver(LocalDate.of(2100, 12, 31), LocalDate.of(2101, 1, 1), reloj, MADRID))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("Las fechas deben estar entre 1970 y 2100");
	}

	@Test
	void losExtremosAbsolutosSonValidosYSusInstantesSeCalculan() {
		Clock reloj = relojEn("2030-01-15T12:00:00Z");
		RangoFechas primero = RangoFechas.resolver(LocalDate.of(1970, 1, 1), LocalDate.of(1970, 1, 1), reloj, MADRID);
		RangoFechas ultimo = RangoFechas.resolver(LocalDate.of(2100, 12, 31), LocalDate.of(2100, 12, 31), reloj, MADRID);

		assertThat(primero.dias()).isEqualTo(1);
		assertThat(primero.fin(MADRID)).isEqualTo(Instant.parse("1970-01-01T23:00:00Z"));
		assertThat(ultimo.dias()).isEqualTo(1);
		assertThat(ultimo.fin(MADRID)).isEqualTo(Instant.parse("2100-12-31T23:00:00Z"));
	}

	@Test
	void fechasListaCadaDiaIncluyendoAmbosExtremos() {
		RangoFechas r = new RangoFechas(LocalDate.of(2028, 2, 27), LocalDate.of(2028, 3, 1));

		assertThat(r.fechas()).containsExactly(LocalDate.of(2028, 2, 27), LocalDate.of(2028, 2, 28),
				LocalDate.of(2028, 2, 29), LocalDate.of(2028, 3, 1));
	}

	@Test
	void instantesSonElPrincipioDelPrimerDiaYElPrincipioDelDiaSiguienteAlUltimo() {
		RangoFechas r = new RangoFechas(LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 1));

		// invierno: Madrid es UTC+1, asi que la medianoche local es 23:00Z del dia anterior
		assertThat(r.inicio(MADRID)).isEqualTo(Instant.parse("2029-12-31T23:00:00Z"));
		assertThat(r.fin(MADRID)).isEqualTo(Instant.parse("2030-01-01T23:00:00Z"));
	}

	@Test
	void losDomingosDelCambioDeHoraDuran25y23Horas() {
		RangoFechas otono = new RangoFechas(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 25));
		RangoFechas primavera = new RangoFechas(LocalDate.of(2027, 3, 28), LocalDate.of(2027, 3, 28));

		assertThat(Duration.between(otono.inicio(MADRID), otono.fin(MADRID))).isEqualTo(Duration.ofHours(25));
		assertThat(Duration.between(primavera.inicio(MADRID), primavera.fin(MADRID))).isEqualTo(Duration.ofHours(23));
	}
}
