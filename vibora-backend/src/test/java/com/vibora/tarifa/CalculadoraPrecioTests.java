package com.vibora.tarifa;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests unitarios (sin Spring ni BD) del calculo del precio de un tramo horario. */
class CalculadoraPrecioTests {

	private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
	private static final LocalDate LUNES = LocalDate.of(2030, 1, 7);
	private static final String SIN_TARIFA = "No hay tarifa definida para esa franja horaria";

	private final CalculadoraPrecio calculadora = new CalculadoraPrecio();

	private static TarifaFranja franja(DayOfWeek dia, int horaIni, int horaFin, String precio) {
		TarifaFranja t = new TarifaFranja();
		t.setDiaSemana(dia);
		t.setHoraInicio(LocalTime.of(horaIni, 0));
		t.setHoraFin(LocalTime.of(horaFin, 0));
		t.setPrecioHora(new BigDecimal(precio));
		t.setTipo(TipoTarifa.NORMAL);
		return t;
	}

	private static Instant local(LocalDate fecha, int hora, int minuto) {
		return fecha.atTime(hora, minuto).atZone(MADRID).toInstant();
	}

	@Test
	void unaFranja_precioHoraPorDuracion() {
		var tarifas = List.of(franja(DayOfWeek.MONDAY, 8, 23, "12.00"));

		BigDecimal precio = calculadora.calcular(tarifas, local(LUNES, 11, 0), local(LUNES, 12, 30), MADRID);

		assertThat(precio).isEqualByComparingTo("18.00");
		assertThat(precio.scale()).isEqualTo(2);
	}

	@Test
	void tramoQueCruzaDosFranjas_seProrrateaCadaParte() {
		var tarifas = List.of(
				franja(DayOfWeek.MONDAY, 8, 18, "12.00"),
				franja(DayOfWeek.MONDAY, 18, 23, "24.00"));

		// 17:00-18:00 a 12 EUR/h + 18:00-18:30 a 24 EUR/h = 12 + 12
		BigDecimal precio = calculadora.calcular(tarifas, local(LUNES, 17, 0), local(LUNES, 18, 30), MADRID);

		assertThat(precio).isEqualByComparingTo("24.00");
	}

	@Test
	void huecoSinTarifa_lanzaIllegalArgument() {
		var tarifas = List.of(franja(DayOfWeek.MONDAY, 8, 18, "12.00"));

		assertThatThrownBy(() -> calculadora.calcular(tarifas, local(LUNES, 17, 0), local(LUNES, 18, 30), MADRID))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage(SIN_TARIFA);
	}

	@Test
	void sinFranjasParaEseDia_lanzaIllegalArgument() {
		var tarifas = List.of(franja(DayOfWeek.TUESDAY, 8, 23, "12.00"));

		assertThatThrownBy(() -> calculadora.calcular(tarifas, local(LUNES, 11, 0), local(LUNES, 12, 30), MADRID))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage(SIN_TARIFA);
	}

	@Test
	void sinNingunaTarifa_lanzaIllegalArgument() {
		assertThatThrownBy(() -> calculadora.calcular(List.of(), local(LUNES, 11, 0), local(LUNES, 12, 30), MADRID))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage(SIN_TARIFA);
	}

	@Test
	void redondeoHalfUp_aDosDecimales() {
		// 10.01 EUR/h * 90 min = 15.015 -> 15.02 (HALF_UP; HALF_EVEN daria 15.02 tambien, asi que
		// se comprueba ademas el caso 10.05 * 90 / 60 = 15.075 -> 15.08, donde HALF_EVEN daria 15.08 y DOWN 15.07)
		var a = List.of(franja(DayOfWeek.MONDAY, 8, 23, "10.01"));
		var b = List.of(franja(DayOfWeek.MONDAY, 8, 23, "10.05"));

		assertThat(calculadora.calcular(a, local(LUNES, 11, 0), local(LUNES, 12, 30), MADRID)).isEqualByComparingTo("15.02");
		assertThat(calculadora.calcular(b, local(LUNES, 11, 0), local(LUNES, 12, 30), MADRID)).isEqualByComparingTo("15.08");
	}

	@Test
	void redondeoHalfUp_noEsBancario() {
		// 10.03 EUR/h * 90 / 60 = 15.045 -> HALF_UP = 15.05 (HALF_EVEN daria 15.04)
		var tarifas = List.of(franja(DayOfWeek.MONDAY, 8, 23, "10.03"));

		assertThat(calculadora.calcular(tarifas, local(LUNES, 11, 0), local(LUNES, 12, 30), MADRID))
				.isEqualByComparingTo("15.05");
	}

	@Test
	void franjaGratuita_precioCero() {
		var tarifas = List.of(franja(DayOfWeek.MONDAY, 8, 23, "0.00"));

		assertThat(calculadora.calcular(tarifas, local(LUNES, 11, 0), local(LUNES, 12, 30), MADRID))
				.isEqualByComparingTo("0.00");
	}

	@Test
	void usaLaHoraLocalDelClub_tambienEnVerano() {
		// Julio en Madrid es UTC+2: las 11:00 locales son las 09:00Z. La franja 8-18 debe cubrirlo.
		LocalDate lunesVerano = LocalDate.of(2030, 7, 8);
		assertThat(lunesVerano.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
		var tarifas = List.of(franja(DayOfWeek.MONDAY, 8, 18, "12.00"));

		BigDecimal precio = calculadora.calcular(tarifas, local(lunesVerano, 11, 0), local(lunesVerano, 12, 30), MADRID);

		assertThat(precio).isEqualByComparingTo("18.00");
	}

	@Test
	void usaElDiaLocal_noElDiaUtc() {
		// Lunes 00:30-02:00 hora de Madrid es todavia domingo en UTC: debe usarse la franja del lunes
		var tarifas = List.of(franja(DayOfWeek.MONDAY, 0, 3, "12.00"));

		BigDecimal precio = calculadora.calcular(tarifas, local(LUNES, 0, 30), local(LUNES, 2, 0), MADRID);

		assertThat(precio).isEqualByComparingTo("18.00");
	}

	@Test
	void tramoQueCruzaMedianoche_lanzaIllegalArgument() {
		var tarifas = List.of(
				franja(DayOfWeek.MONDAY, 8, 23, "12.00"),
				franja(DayOfWeek.TUESDAY, 0, 3, "12.00"));
		Instant inicio = local(LUNES, 23, 30);
		Instant fin = local(LUNES.plusDays(1), 1, 0);

		assertThatThrownBy(() -> calculadora.calcular(tarifas, inicio, fin, MADRID))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
