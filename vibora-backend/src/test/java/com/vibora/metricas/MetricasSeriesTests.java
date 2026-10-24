package com.vibora.metricas;

import com.vibora.pista.Pista;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Series temporales: GET .../metricas/ingresos y .../metricas/cancelaciones (granularidad DIA, SEMANA y MES). */
class MetricasSeriesTests extends MetricasApiTestBase {

	Pista p1;
	Pista p2;

	@BeforeEach
	void pistasDelClub() {
		p1 = pista(club, "P1");
		p2 = pista(club, "P2");
	}

	/** Un punto de la serie de ingresos: [periodo, ingresos, reservas]. */
	private List<List<Object>> ingresos(String... params) throws Exception {
		var j = json(comoAdmin("ingresos", params).andExpect(status().isOk()));
		List<Map<String, Object>> serie = j.read("$.serie");
		return serie.stream().map(p -> List.of(p.get("periodo"), num(p.get("ingresos")).setScale(2).toPlainString(), p.get("reservas"))).toList();
	}

	private static List<Object> punto(String periodo, String ingresos, int reservas) {
		return List.of(periodo, ingresos, reservas);
	}

	@Test
	void ingresosPorDiaRellenaTodosLosDiasConCeros() throws Exception {
		confirmada(p1, d(2030, 1, 7), "08:00", "30.00");
		confirmada(p1, d(2030, 1, 7), "09:30", "30.00");
		confirmada(p2, d(2030, 1, 8), "20:00", "45.50");
		cancelada(p2, d(2030, 1, 9), "08:00", "25.00"); // las canceladas no son ingresos
		confirmada(p2, d(2030, 1, 12), "21:30", "40.00");

		assertThat(ingresos(rango(d(2030, 1, 7), d(2030, 1, 13)))).containsExactly(
				punto("2030-01-07", "60.00", 2), punto("2030-01-08", "45.50", 1), punto("2030-01-09", "0.00", 0),
				punto("2030-01-10", "0.00", 0), punto("2030-01-11", "0.00", 0), punto("2030-01-12", "40.00", 1),
				punto("2030-01-13", "0.00", 0));
	}

	@Test
	void sinGranularidadEsPorDia() throws Exception {
		var j = json(comoAdmin("ingresos", rango(d(2030, 1, 7), d(2030, 1, 9))).andExpect(status().isOk()));

		assertThat(j.read("$.granularidad", String.class)).isEqualTo("DIA");
		assertThat(j.read("$.serie.length()", Integer.class)).isEqualTo(3);
	}

	@Test
	void unClubSinReservasDaLaSerieEnteraACeros() throws Exception {
		List<List<Object>> serie = ingresos();

		assertThat(serie).hasSize(30);
		assertThat(serie.get(0).get(0)).isEqualTo("2029-12-03");
		assertThat(serie.get(29).get(0)).isEqualTo("2030-01-01");
		assertThat(serie).allSatisfy(p -> assertThat(p.subList(1, 3)).containsExactly("0.00", 0));
	}

	@Test
	void ingresosPorSemanaEmpiezanElLunesYSoloContanLosDiasDelRango() throws Exception {
		// rango: miercoles 2-ene .. domingo 20-ene -> semanas del 31-dic, 7-ene y 14-ene
		confirmada(p1, d(2030, 1, 1), "08:00", "100.00"); // martes: de la semana del 31-dic pero ANTES del rango
		confirmada(p1, d(2030, 1, 2), "08:00", "10.00");
		confirmada(p1, d(2030, 1, 6), "21:30", "20.00"); // domingo, ultima hora de la primera semana
		confirmada(p1, d(2030, 1, 7), "08:00", "30.00"); // lunes: abre la segunda semana
		confirmada(p2, d(2030, 1, 13), "21:30", "5.00"); // domingo, cierra la segunda
		confirmada(p2, d(2030, 1, 21), "08:00", "999.00"); // fuera del rango

		assertThat(ingresos(rango(d(2030, 1, 2), d(2030, 1, 20), "granularidad", "SEMANA"))).containsExactly(
				punto("2029-12-31", "30.00", 2), punto("2030-01-07", "35.00", 2), punto("2030-01-14", "0.00", 0));
	}

	@Test
	void ingresosPorMesCambiaDeMesALaMedianocheLocalNoUtc() throws Exception {
		// 23:30 del 31-dic en Madrid (UTC+1) = 22:30Z -> diciembre; 00:30 del 1-ene = 23:30Z del 31 -> enero
		reserva(p1, local(d(2029, 12, 31), "23:30"), com.vibora.reserva.EstadoReserva.CONFIRMADA, "10.00");
		reserva(p2, local(d(2030, 1, 1), "00:30"), com.vibora.reserva.EstadoReserva.CONFIRMADA, "20.00");
		confirmada(p1, d(2030, 2, 10), "08:00", "7.00");
		confirmada(p1, d(2030, 2, 11), "08:00", "700.00"); // fuera del rango

		assertThat(ingresos(rango(d(2029, 12, 15), d(2030, 2, 10), "granularidad", "MES"))).containsExactly(
				punto("2029-12-01", "10.00", 1), punto("2030-01-01", "20.00", 1), punto("2030-02-01", "7.00", 1));
	}

	@Test
	void febreroBisiestoTieneSuDia29() throws Exception {
		confirmada(p1, d(2028, 2, 29), "08:00", "11.00");

		assertThat(ingresos(rango(d(2028, 2, 28), d(2028, 3, 1)))).containsExactly(
				punto("2028-02-28", "0.00", 0), punto("2028-02-29", "11.00", 1), punto("2028-03-01", "0.00", 0));
		assertThat(ingresos(rango(d(2028, 2, 1), d(2028, 3, 31), "granularidad", "MES"))).containsExactly(
				punto("2028-02-01", "11.00", 1), punto("2028-03-01", "0.00", 0));
	}

	@Test
	void elDomingoDelCambioDeHoraDeOtonoSigueSiendoUnSoloDia() throws Exception {
		// 25-oct-2026: dia de 25 horas. 08:00 local = 07:00Z; 21:30 local = 20:30Z
		confirmada(p1, d(2026, 10, 24), "21:30", "1.00");
		confirmada(p1, d(2026, 10, 25), "08:00", "2.00");
		confirmada(p2, d(2026, 10, 25), "21:30", "4.00");
		confirmada(p1, d(2026, 10, 26), "08:00", "8.00");

		assertThat(ingresos(rango(d(2026, 10, 24), d(2026, 10, 26)))).containsExactly(
				punto("2026-10-24", "1.00", 1), punto("2026-10-25", "6.00", 2), punto("2026-10-26", "8.00", 1));
		assertThat(ingresos(rango(d(2026, 10, 19), d(2026, 11, 1), "granularidad", "SEMANA"))).containsExactly(
				punto("2026-10-19", "7.00", 3), punto("2026-10-26", "8.00", 1));
	}

	@Test
	void elDomingoDelCambioDeHoraDePrimaveraSigueSiendoUnSoloDia() throws Exception {
		// 28-mar-2027: dia de 23 horas
		confirmada(p1, d(2027, 3, 27), "21:30", "1.00");
		confirmada(p1, d(2027, 3, 28), "08:00", "2.00");
		confirmada(p2, d(2027, 3, 28), "21:30", "4.00");
		confirmada(p1, d(2027, 3, 29), "08:00", "8.00");

		assertThat(ingresos(rango(d(2027, 3, 27), d(2027, 3, 29)))).containsExactly(
				punto("2027-03-27", "1.00", 1), punto("2027-03-28", "6.00", 2), punto("2027-03-29", "8.00", 1));
		assertThat(ingresos(rango(d(2027, 3, 22), d(2027, 3, 28), "granularidad", "SEMANA"))).containsExactly(
				punto("2027-03-22", "7.00", 3));
	}

	@Test
	void lasDemasSeriesDeOtroClubNoSeMezclan() throws Exception {
		confirmada(p1, d(2030, 1, 7), "08:00", "30.00");
		var otro = crearClub("Club B");
		confirmada(pista(otro, "B1"), d(2030, 1, 7), "08:00", "500.00");

		assertThat(ingresos(rango(d(2030, 1, 7), d(2030, 1, 7)))).containsExactly(punto("2030-01-07", "30.00", 1));
	}

	// ---- cancelaciones ----

	@Test
	void cancelacionesPorDiaConTasaYPorPista() throws Exception {
		confirmada(p1, d(2030, 1, 7), "08:00", "30.00");
		confirmada(p1, d(2030, 1, 7), "09:30", "30.00");
		cancelada(p1, d(2030, 1, 7), "11:00", "20.00");
		cancelada(p2, d(2030, 1, 8), "08:00", "25.00");
		cancelada(p2, d(2030, 1, 8), "09:30", "15.50");
		confirmada(p2, d(2030, 1, 10), "08:00", "40.00");

		var j = json(comoAdmin("cancelaciones", rango(d(2030, 1, 7), d(2030, 1, 10))).andExpect(status().isOk()));

		assertThat(j.read("$.granularidad", String.class)).isEqualTo("DIA");
		List<Map<String, Object>> serie = j.read("$.serie");
		assertThat(serie).hasSize(4);
		assertPunto(serie.get(0), "2030-01-07", 1, 2, "0.3333");
		assertPunto(serie.get(1), "2030-01-08", 2, 0, "1.0000");
		assertPunto(serie.get(2), "2030-01-09", 0, 0, null); // sin reservas: tasa nula, no 0 ni error
		assertPunto(serie.get(3), "2030-01-10", 0, 1, "0.0000");

		List<Map<String, Object>> porPista = j.read("$.porPista");
		assertThat(porPista).hasSize(2);
		assertThat(porPista.get(0).get("pistaNombre")).isEqualTo("P1");
		assertThat(((Number) porPista.get(0).get("pistaId")).longValue()).isEqualTo(p1.getId());
		assertThat(porPista.get(0).get("canceladas")).isEqualTo(1);
		assertThat(num(porPista.get(0).get("ingresosPerdidos"))).isEqualByComparingTo("20.00");
		assertThat(porPista.get(1).get("pistaNombre")).isEqualTo("P2");
		assertThat(porPista.get(1).get("canceladas")).isEqualTo(2);
		assertThat(num(porPista.get(1).get("ingresosPerdidos"))).isEqualByComparingTo("40.50");
	}

	private void assertPunto(Map<String, Object> punto, String periodo, int canceladas, int confirmadas, String tasa) {
		assertThat(punto.get("periodo")).isEqualTo(periodo);
		assertThat(punto.get("canceladas")).isEqualTo(canceladas);
		assertThat(punto.get("confirmadas")).isEqualTo(confirmadas);
		if (tasa == null) {
			assertThat(punto.get("tasa")).isNull();
		} else {
			assertThat(num(punto.get("tasa"))).isEqualByComparingTo(tasa);
		}
	}

	@Test
	void cancelacionesPorSemanaYMesEstanRellenasYUsanElDiaDeJuego() throws Exception {
		cancelada(p1, d(2030, 1, 6), "08:00", "10.00"); // domingo: semana del 31-dic
		cancelada(p1, d(2030, 1, 7), "08:00", "10.00"); // lunes: semana del 7
		confirmada(p2, d(2030, 1, 7), "08:00", "10.00");

		var semanas = json(comoAdmin("cancelaciones", rango(d(2030, 1, 2), d(2030, 1, 20), "granularidad", "SEMANA")));
		List<Map<String, Object>> serie = semanas.read("$.serie");
		assertThat(serie).hasSize(3);
		assertPunto(serie.get(0), "2029-12-31", 1, 0, "1.0000");
		assertPunto(serie.get(1), "2030-01-07", 1, 1, "0.5000");
		assertPunto(serie.get(2), "2030-01-14", 0, 0, null);

		var meses = json(comoAdmin("cancelaciones", rango(d(2029, 12, 15), d(2030, 2, 10), "granularidad", "MES")));
		List<Map<String, Object>> m = meses.read("$.serie");
		assertThat(m).hasSize(3);
		assertPunto(m.get(0), "2029-12-01", 0, 0, null);
		assertPunto(m.get(1), "2030-01-01", 2, 1, "0.6667");
		assertPunto(m.get(2), "2030-02-01", 0, 0, null);
	}

	@Test
	void cancelacionesDelDomingoDelCambioDeHoraVanAUnSoloDia() throws Exception {
		cancelada(p1, d(2026, 10, 25), "08:00", "1.00");
		cancelada(p2, d(2026, 10, 25), "21:30", "1.00");

		List<Map<String, Object>> serie = json(comoAdmin("cancelaciones", rango(d(2026, 10, 24), d(2026, 10, 26)))).read("$.serie");

		assertThat(serie).hasSize(3);
		assertPunto(serie.get(1), "2026-10-25", 2, 0, "1.0000");
	}

	@Test
	void cancelacionesPorPistaIncluyeLasPistasSinCancelacionesYNoLasDeOtroClub() throws Exception {
		cancelada(p1, d(2030, 1, 7), "08:00", "10.00");
		var otro = crearClub("Club B");
		cancelada(pista(otro, "B1"), d(2030, 1, 7), "08:00", "999.00");

		List<Map<String, Object>> porPista = json(comoAdmin("cancelaciones", rango(d(2030, 1, 7), d(2030, 1, 7)))).read("$.porPista");

		assertThat(porPista).extracting(x -> x.get("pistaNombre")).containsExactly("P1", "P2");
		assertThat(porPista.get(1).get("canceladas")).isEqualTo(0);
		assertThat(num(porPista.get(1).get("ingresosPerdidos"))).isEqualByComparingTo("0.00");
	}

	@Test
	void cancelacionesDeUnClubVacioDanCerosYTasasNulas() throws Exception {
		var vacio = crearClub("Club vacio");
		String adminVacio = bearerAdminDe(vacio, "admin@vacio.com");

		var j = json(pedir(adminVacio, vacio, "cancelaciones", rango(d(2030, 1, 7), d(2030, 1, 9))).andExpect(status().isOk()));

		List<Map<String, Object>> serie = j.read("$.serie");
		assertThat(serie).hasSize(3);
		assertThat(serie).allSatisfy(p -> {
			assertThat(p.get("canceladas")).isEqualTo(0);
			assertThat(p.get("tasa")).isNull();
		});
		assertThat(j.read("$.porPista", List.class)).isEmpty();
	}
}
