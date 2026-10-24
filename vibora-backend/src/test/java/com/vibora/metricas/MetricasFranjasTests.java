package com.vibora.metricas;

import com.jayway.jsonpath.DocumentContext;
import com.vibora.pista.Pista;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET .../metricas/franjas: rentabilidad por dia de la semana y hora local de inicio. */
class MetricasFranjasTests extends MetricasApiTestBase {

	/** Dos semanas completas: lunes 7 .. domingo 20 de enero de 2030 (cada dia de la semana sale dos veces). */
	private static final LocalDate DESDE = d(2030, 1, 7);
	private static final LocalDate HASTA = d(2030, 1, 20);

	Pista p1;
	Pista p2;

	@BeforeEach
	void pistasDelClub() {
		p1 = pista(club, "P1");
		p2 = pista(club, "P2");
	}

	private DocumentContext franjas(String... extra) throws Exception {
		return json(comoAdmin("franjas", rango(DESDE, HASTA, extra)).andExpect(status().isOk()));
	}

	private static void assertFranja(Map<String, Object> f, String dia, String hora, int reservas, String ingresos,
			int ofrecidas, String ocupacion) {
		assertThat(f.get("diaSemana")).as("dia").isEqualTo(dia);
		assertThat(f.get("hora")).as("hora").isEqualTo(hora);
		assertThat(f.get("reservas")).as("reservas").isEqualTo(reservas);
		assertThat(num(f.get("ingresos"))).as("ingresos").isEqualByComparingTo(ingresos);
		assertThat(f.get("ranurasOfrecidas")).as("ofrecidas").isEqualTo(ofrecidas);
		if (ocupacion == null) {
			assertThat(f.get("ocupacion")).isNull();
		} else {
			assertThat(num(f.get("ocupacion"))).as("ocupacion").isEqualByComparingTo(ocupacion);
		}
	}

	private void datosDeLaRentabilidad() {
		confirmada(p1, d(2030, 1, 7), "20:00", "50.00"); // lunes 20:00 x3 = 140
		confirmada(p1, d(2030, 1, 14), "20:00", "50.00");
		confirmada(p2, d(2030, 1, 7), "20:00", "40.00");
		confirmada(p2, d(2030, 1, 8), "08:00", "60.00"); // martes 08:00 = 60
		confirmada(p1, d(2030, 1, 10), "11:00", "15.00"); // jueves 11:00 x2 = 30
		confirmada(p2, d(2030, 1, 10), "11:00", "15.00");
		confirmada(p2, d(2030, 1, 9), "11:00", "30.00"); // miercoles 11:00 x1 = 30
		confirmada(p1, d(2030, 1, 12), "09:30", "30.00"); // sabado 09:30 x1 = 30
		cancelada(p1, d(2030, 1, 14), "21:30", "99.00"); // las canceladas no puntuan
	}

	@Test
	void masRentablesOrdenaPorIngresosLuegoReservasYLuegoDiaYHora() throws Exception {
		datosDeLaRentabilidad();

		List<Map<String, Object>> top = franjas().read("$.masRentables");

		assertThat(top).hasSize(5); // solo franjas con reservas confirmadas
		assertFranja(top.get(0), "MONDAY", "20:00", 3, "140.00", 4, "0.7500");
		assertFranja(top.get(1), "TUESDAY", "08:00", 1, "60.00", 4, "0.2500");
		// empate a 30.00: el jueves tiene 2 reservas y gana; miercoles y sabado empatan a 1 y manda el dia
		assertFranja(top.get(2), "THURSDAY", "11:00", 2, "30.00", 4, "0.5000");
		assertFranja(top.get(3), "WEDNESDAY", "11:00", 1, "30.00", 4, "0.2500");
		assertFranja(top.get(4), "SATURDAY", "09:30", 1, "30.00", 4, "0.2500");
	}

	@Test
	void limitRecortaElTopPeroNoLaMatriz() throws Exception {
		datosDeLaRentabilidad();

		var j = franjas("limit", "2");

		List<Map<String, Object>> top = j.read("$.masRentables");
		assertThat(top).hasSize(2);
		assertFranja(top.get(0), "MONDAY", "20:00", 3, "140.00", 4, "0.7500");
		assertFranja(top.get(1), "TUESDAY", "08:00", 1, "60.00", 4, "0.2500");
		assertThat(j.read("$.matriz.length()", Integer.class)).isEqualTo(70);
		assertThat(j.read("$.limit", Integer.class)).isEqualTo(2);
	}

	@Test
	void laMatrizTieneUnaCeldaPorCadaDiaYHoraOfrecidosOrdenadaPorDiaYHora() throws Exception {
		datosDeLaRentabilidad();

		List<Map<String, Object>> matriz = franjas().read("$.matriz");

		assertThat(matriz).hasSize(70); // 7 dias x 10 casillas
		assertFranja(matriz.get(0), "MONDAY", "08:00", 0, "0.00", 4, "0.0000");
		assertFranja(matriz.get(8), "MONDAY", "20:00", 3, "140.00", 4, "0.7500");
		assertFranja(matriz.get(9), "MONDAY", "21:30", 0, "0.00", 4, "0.0000"); // la cancelada no cuenta
		assertFranja(matriz.get(10), "TUESDAY", "08:00", 1, "60.00", 4, "0.2500");
		assertFranja(matriz.get(69), "SUNDAY", "21:30", 0, "0.00", 4, "0.0000");
	}

	@Test
	void unBloqueoReduceLasRanurasOfrecidasDeSuCelda() throws Exception {
		bloqueo(p1, local(d(2030, 1, 14), "14:00"), local(d(2030, 1, 14), "15:30"), null);

		List<Map<String, Object>> matriz = franjas().read("$.matriz");

		Map<String, Object> celda = matriz.stream().filter(f -> "MONDAY".equals(f.get("diaSemana")) && "14:00".equals(f.get("hora")))
				.findFirst().orElseThrow();
		assertFranja(celda, "MONDAY", "14:00", 0, "0.00", 3, "0.0000");
	}

	@Test
	void unBloqueoQueTapaTodoUnaCeldaLaSacaDeLaMatriz() throws Exception {
		// lunes 08:00 bloqueado en las dos pistas y los dos lunes
		for (Pista p : List.of(p1, p2)) {
			for (LocalDate lunes : List.of(d(2030, 1, 7), d(2030, 1, 14))) {
				bloqueo(p, local(lunes, "08:00"), local(lunes, "09:30"), null);
			}
		}

		List<Map<String, Object>> matriz = franjas().read("$.matriz");

		assertThat(matriz).hasSize(69);
		assertThat(matriz).noneMatch(f -> "MONDAY".equals(f.get("diaSemana")) && "08:00".equals(f.get("hora")));
	}

	@Test
	void sinReservasElTopEstaVacioYLaMatrizEstaEnCeros() throws Exception {
		var j = franjas();

		assertThat(j.read("$.masRentables", List.class)).isEmpty();
		assertThat(j.read("$.matriz.length()", Integer.class)).isEqualTo(70);
	}

	@Test
	void unClubSinPistasNoTieneMatriz() throws Exception {
		var vacio = crearClub("Club vacio");
		String adminVacio = bearerAdminDe(vacio, "admin@vacio.com");

		var j = json(pedir(adminVacio, vacio, "franjas", rango(DESDE, HASTA)).andExpect(status().isOk()));

		assertThat(j.read("$.masRentables", List.class)).isEmpty();
		assertThat(j.read("$.matriz", List.class)).isEmpty();
	}

	@Test
	void agrupaPorHoraLocalAunqueElOffsetUtcCambieEntreSemanas() throws Exception {
		// domingos 18 y 25 de octubre de 2026 a las 09:30 locales: 07:30Z el 18 (CEST) y 08:30Z el 25 (CET)
		confirmada(p1, d(2026, 10, 18), "09:30", "10.00");
		confirmada(p1, d(2026, 10, 25), "09:30", "12.00");

		var j = json(comoAdmin("franjas", rango(d(2026, 10, 12), d(2026, 10, 25))).andExpect(status().isOk()));

		List<Map<String, Object>> top = j.read("$.masRentables");
		assertThat(top).hasSize(1);
		assertFranja(top.get(0), "SUNDAY", "09:30", 2, "22.00", 4, "0.5000");
	}

	@Test
	void ignoraLasReservasYBloqueosDeOtroClub() throws Exception {
		datosDeLaRentabilidad();
		String antes = cuerpo(comoAdmin("franjas", rango(DESDE, HASTA)));

		var otro = crearClub("Club B");
		var b1 = pista(otro, "B1");
		confirmada(b1, d(2030, 1, 7), "20:00", "1000.00");
		bloqueo(b1, local(d(2030, 1, 7), "08:00"), null, null);

		assertThat(cuerpo(comoAdmin("franjas", rango(DESDE, HASTA)))).isEqualTo(antes);
	}
}
