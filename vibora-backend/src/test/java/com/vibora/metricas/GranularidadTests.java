package com.vibora.metricas;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Periodos de las series: DIA, SEMANA (empieza el lunes) y MES, sobre fechas locales (pura, sin Spring). */
class GranularidadTests {

	private static LocalDate d(int anio, int mes, int dia) {
		return LocalDate.of(anio, mes, dia);
	}

	private static RangoFechas rango(LocalDate desde, LocalDate hasta) {
		return new RangoFechas(desde, hasta);
	}

	@Test
	void diaListaTodosLosDias() {
		assertThat(Granularidad.DIA.periodos(rango(d(2030, 1, 30), d(2030, 2, 2))))
				.containsExactly(d(2030, 1, 30), d(2030, 1, 31), d(2030, 2, 1), d(2030, 2, 2));
	}

	@Test
	void inicioDeSemanaEsElLunes() {
		// 2030-01-01 es martes; 2030-01-06 es domingo; 2030-01-07 es lunes
		assertThat(Granularidad.SEMANA.inicioDe(d(2030, 1, 1))).isEqualTo(d(2029, 12, 31));
		assertThat(Granularidad.SEMANA.inicioDe(d(2030, 1, 6))).isEqualTo(d(2029, 12, 31));
		assertThat(Granularidad.SEMANA.inicioDe(d(2030, 1, 7))).isEqualTo(d(2030, 1, 7));
	}

	@Test
	void semanasQueEmpiezanAntesDelRangoSeIncluyenCompletas() {
		// miercoles 2 .. domingo 20: lunes 31-dic, 7-ene y 14-ene
		assertThat(Granularidad.SEMANA.periodos(rango(d(2030, 1, 2), d(2030, 1, 20))))
				.containsExactly(d(2029, 12, 31), d(2030, 1, 7), d(2030, 1, 14));
	}

	@Test
	void unaSemanaExactaDeLunesADomingoEsUnSoloPeriodo() {
		assertThat(Granularidad.SEMANA.periodos(rango(d(2030, 1, 7), d(2030, 1, 13)))).containsExactly(d(2030, 1, 7));
		// y el lunes siguiente abre otro
		assertThat(Granularidad.SEMANA.periodos(rango(d(2030, 1, 7), d(2030, 1, 14))))
				.containsExactly(d(2030, 1, 7), d(2030, 1, 14));
	}

	@Test
	void mesesIncluyenElDeCadaExtremo() {
		assertThat(Granularidad.MES.periodos(rango(d(2029, 11, 15), d(2030, 2, 10))))
				.containsExactly(d(2029, 11, 1), d(2029, 12, 1), d(2030, 1, 1), d(2030, 2, 1));
	}

	@Test
	void unMesCompletoEsUnSoloPeriodoYElUltimoDiaNoAbreElSiguiente() {
		assertThat(Granularidad.MES.periodos(rango(d(2030, 1, 1), d(2030, 1, 31)))).containsExactly(d(2030, 1, 1));
		assertThat(Granularidad.MES.periodos(rango(d(2030, 1, 31), d(2030, 2, 1)))).containsExactly(d(2030, 1, 1), d(2030, 2, 1));
	}

	@Test
	void cambioDeAnioEnMeses() {
		assertThat(Granularidad.MES.siguiente(d(2029, 12, 1))).isEqualTo(d(2030, 1, 1));
	}

	@Test
	void diaBisiesto() {
		// 2028-02-29 es martes
		assertThat(Granularidad.DIA.periodos(rango(d(2028, 2, 28), d(2028, 3, 1))))
				.containsExactly(d(2028, 2, 28), d(2028, 2, 29), d(2028, 3, 1));
		assertThat(Granularidad.SEMANA.inicioDe(d(2028, 2, 29))).isEqualTo(d(2028, 2, 28));
		assertThat(Granularidad.MES.inicioDe(d(2028, 2, 29))).isEqualTo(d(2028, 2, 1));
		assertThat(Granularidad.MES.periodos(rango(d(2028, 2, 1), d(2028, 2, 29)))).containsExactly(d(2028, 2, 1));
		// en un anio no bisiesto febrero acaba el 28 y el 1 de marzo es otro mes
		assertThat(Granularidad.MES.periodos(rango(d(2030, 2, 28), d(2030, 3, 1)))).containsExactly(d(2030, 2, 1), d(2030, 3, 1));
	}

	@Test
	void losDomingosDelCambioDeHoraSonDiasNormalesDeUnaFecha() {
		// 2026-10-25 (25 horas) y 2027-03-28 (23 horas): siguen siendo UN dia y caen en la semana del lunes anterior
		assertThat(Granularidad.DIA.periodos(rango(d(2026, 10, 24), d(2026, 10, 26))))
				.containsExactly(d(2026, 10, 24), d(2026, 10, 25), d(2026, 10, 26));
		assertThat(Granularidad.DIA.periodos(rango(d(2027, 3, 27), d(2027, 3, 29))))
				.containsExactly(d(2027, 3, 27), d(2027, 3, 28), d(2027, 3, 29));
		assertThat(Granularidad.SEMANA.inicioDe(d(2026, 10, 25))).isEqualTo(d(2026, 10, 19));
		assertThat(Granularidad.SEMANA.inicioDe(d(2027, 3, 28))).isEqualTo(d(2027, 3, 22));
		assertThat(Granularidad.SEMANA.periodos(rango(d(2026, 10, 19), d(2026, 10, 25)))).containsExactly(d(2026, 10, 19));
		assertThat(Granularidad.SEMANA.periodos(rango(d(2027, 3, 22), d(2027, 3, 28)))).containsExactly(d(2027, 3, 22));
	}

	@Test
	void unRangoDe366DiasDaComoMaximo366PeriodosDiarios() {
		assertThat(Granularidad.DIA.periodos(rango(d(2029, 1, 1), d(2030, 1, 1)))).hasSize(366);
		assertThat(Granularidad.SEMANA.periodos(rango(d(2029, 1, 1), d(2030, 1, 1)))).hasSize(53);
		assertThat(Granularidad.MES.periodos(rango(d(2029, 1, 1), d(2030, 1, 1)))).hasSize(13);
	}
}
