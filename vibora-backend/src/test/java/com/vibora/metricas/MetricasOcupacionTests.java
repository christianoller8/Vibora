package com.vibora.metricas;

import com.vibora.pista.Pista;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET .../metricas/ocupacion: ranuras ofrecidas (rejilla menos bloqueos) y ocupadas (reservas confirmadas) por pista. */
class MetricasOcupacionTests extends MetricasApiTestBase {

	private static final java.time.LocalDate LUNES = d(2030, 1, 7);

	Pista p1;
	Pista p2;

	@BeforeEach
	void pistasDelClub() {
		p1 = pista(club, "P1");
		p2 = pista(club, "P2");
	}

	private com.jayway.jsonpath.DocumentContext ocupacion(String... params) throws Exception {
		return json(comoAdmin("ocupacion", params).andExpect(status().isOk()));
	}

	private static Map<String, Object> fila(com.jayway.jsonpath.DocumentContext j, String nombre) {
		List<Map<String, Object>> pistas = j.read("$.pistas");
		return pistas.stream().filter(p -> nombre.equals(p.get("pistaNombre"))).findFirst()
				.orElseThrow(() -> new AssertionError("no hay fila para " + nombre + " en " + pistas));
	}

	private static void assertFila(Map<String, Object> f, int ofrecidas, int ocupadas, String ocupacion, String ingresos) {
		assertThat(f.get("ranurasOfrecidas")).as("ofrecidas").isEqualTo(ofrecidas);
		assertThat(f.get("ranurasOcupadas")).as("ocupadas").isEqualTo(ocupadas);
		if (ocupacion == null) {
			assertThat(f.get("ocupacion")).isNull();
		} else {
			assertThat(num(f.get("ocupacion"))).as("ocupacion").isEqualByComparingTo(ocupacion);
		}
		assertThat(num(f.get("ingresos"))).as("ingresos").isEqualByComparingTo(ingresos);
	}

	@Test
	void unBloqueoQuitaRanurasOfrecidasYElRestoSeCuentaPorPista() throws Exception {
		confirmada(p1, LUNES, "08:00", "30.00");
		confirmada(p1, LUNES, "09:30", "30.00");
		cancelada(p1, LUNES, "14:00", "30.00"); // una cancelada no ocupa
		bloqueo(p1, local(LUNES, "11:00"), local(LUNES, "12:30"), null);

		var j = ocupacion(rango(LUNES, LUNES));

		assertFila(fila(j, "P1"), 9, 2, "0.2222", "60.00");
		assertFila(fila(j, "P2"), 10, 0, "0.0000", "0.00");
		assertThat(j.read("$.ranurasOfrecidas", Integer.class)).isEqualTo(19);
		assertThat(j.read("$.ranurasOcupadas", Integer.class)).isEqualTo(2);
		assertThat(num(j.read("$.ocupacionGlobal"))).isEqualByComparingTo("0.1053");
		assertThat(j.read("$.pistas.length()", Integer.class)).isEqualTo(2);
	}

	@Test
	void lasPistasSalenOrdenadasPorNombre() throws Exception {
		pista(club, "A0");

		List<Map<String, Object>> filas = ocupacion(rango(LUNES, LUNES)).read("$.pistas");

		assertThat(filas).extracting(f -> f.get("pistaNombre")).containsExactly("A0", "P1", "P2");
	}

	@Test
	void unBloqueoLevantadoSoloQuitaLasRanurasHastaQueSeLevanto() throws Exception {
		bloqueo(p1, local(LUNES, "08:00"), null, local(LUNES, "12:30"));

		// quedan 12:30, 14:00, 15:30, 17:00, 18:30, 20:00 y 21:30
		assertFila(fila(ocupacion(rango(LUNES, LUNES)), "P1"), 7, 0, "0.0000", "0.00");
	}

	@Test
	void unBloqueoLevantadoAntesDeEmpezarNoQuitaNada() throws Exception {
		bloqueo(p1, local(LUNES, "11:00"), local(LUNES, "20:00"), local(LUNES, "09:00"));

		assertFila(fila(ocupacion(rango(LUNES, LUNES)), "P1"), 10, 0, "0.0000", "0.00");
	}

	@Test
	void unBloqueoSinFinQuitaTodoDesdeSuInicioEnLosDiasSiguientes() throws Exception {
		bloqueo(p1, local(LUNES, "17:00"), null, null);

		// el lunes quedan 6 (08:00..15:30) y el martes ninguna
		assertFila(fila(ocupacion(rango(LUNES, LUNES.plusDays(1))), "P1"), 6, 0, "0.0000", "0.00");
		assertFila(fila(ocupacion(rango(LUNES, LUNES.plusDays(1))), "P2"), 20, 0, "0.0000", "0.00");
	}

	@Test
	void unBloqueoCaducadoEnElPasadoSigueQuitandoLasRanurasDeEsosDias() throws Exception {
		// el reloj esta en 2030-01-01: este bloqueo ya caduco, pero ese dia la pista no se pudo reservar
		var dia = d(2029, 12, 10);
		bloqueo(p1, local(dia, "08:00"), local(dia, "14:00"), null);

		// 08:00, 09:30, 11:00 y 12:30 (acaba a las 14:00) quedan tapadas; 14:00 empieza cuando acaba el bloqueo
		assertFila(fila(ocupacion(rango(d(2029, 12, 9), d(2029, 12, 11))), "P1"), 26, 0, "0.0000", "0.00");
	}

	@Test
	void unBloqueoQueSoloRozaPorDentroYLosAdyacentes() throws Exception {
		bloqueo(p1, local(LUNES, "10:00"), local(LUNES, "10:10"), null); // roza la casilla 09:30-11:00
		bloqueo(p1, local(LUNES, "14:00"), local(LUNES, "15:30"), null); // justo la casilla 14:00; vecinas intactas

		assertFila(fila(ocupacion(rango(LUNES, LUNES)), "P1"), 8, 0, "0.0000", "0.00");
	}

	@Test
	void unaReservaAnteriorAlBloqueoSigueContandoComoOfrecidaYOcupada() throws Exception {
		confirmada(p1, LUNES, "11:00", "30.00");
		bloqueo(p1, local(LUNES, "11:00"), local(LUNES, "12:30"), null);

		// la casilla bloqueada con reserva cuenta: ofrecidas 10 (no 9) y ocupadas 1, asi ocupadas <= ofrecidas
		assertFila(fila(ocupacion(rango(LUNES, LUNES)), "P1"), 10, 1, "0.1000", "30.00");
	}

	@Test
	void todoBloqueadoPeroSinReservasNoOfreceNadaYLaOcupacionEsNula() throws Exception {
		bloqueo(p1, local(LUNES, "00:00"), null, null);
		bloqueo(p2, local(LUNES, "00:00"), null, null);

		var j = ocupacion(rango(LUNES, LUNES));

		assertFila(fila(j, "P1"), 0, 0, null, "0.00");
		assertThat(j.read("$.ranurasOfrecidas", Integer.class)).isZero();
		assertThat(j.read("$.ocupacionGlobal", Object.class)).isNull();
	}

	@Test
	void losBloqueosYLasPistasDeOtroClubNoCuentan() throws Exception {
		var otro = crearClub("Club B");
		var b1 = pista(otro, "B1");
		bloqueo(b1, local(LUNES, "00:00"), null, null);
		confirmada(b1, LUNES, "08:00", "99.00");

		var j = ocupacion(rango(LUNES, LUNES));

		assertThat(j.read("$.pistas.length()", Integer.class)).isEqualTo(2);
		assertFila(fila(j, "P1"), 10, 0, "0.0000", "0.00");
		assertThat(j.read("$.ranurasOcupadas", Integer.class)).isZero();
	}

	@Test
	void unaPistaInactivaSinReservasEnElRangoNoSale() throws Exception {
		pistaInactiva(club, "Vieja");

		List<Map<String, Object>> filas = ocupacion(rango(LUNES, LUNES)).read("$.pistas");

		assertThat(filas).extracting(f -> f.get("pistaNombre")).containsExactly("P1", "P2");
	}

	@Test
	void unaPistaInactivaConReservasEnElRangoSaleYSeLeAplicaLaRejillaActual() throws Exception {
		Pista inactiva = pistaInactiva(club, "Cerrada");
		confirmada(inactiva, LUNES, "08:00", "20.00");
		Pista soloCancelada = pistaInactiva(club, "Cerrada2");
		cancelada(soloCancelada, LUNES, "08:00", "20.00");

		var j = ocupacion(rango(LUNES, LUNES));

		assertFila(fila(j, "Cerrada"), 10, 1, "0.1000", "20.00");
		assertFila(fila(j, "Cerrada2"), 10, 0, "0.0000", "0.00");
		assertThat(j.read("$.pistas.length()", Integer.class)).isEqualTo(4);
	}

	@Test
	void unaReservaFueraDeLaRejillaCuentaComoIngresoPeroNoOcupaRanura() throws Exception {
		confirmada(p1, LUNES, "10:00", "20.00"); // la rejilla es 08:00, 09:30, 11:00...

		assertFila(fila(ocupacion(rango(LUNES, LUNES)), "P1"), 10, 0, "0.0000", "20.00");
	}

	@Test
	void variasPistasYVariosDiasSuman() throws Exception {
		var p3 = pista(club, "P3");
		confirmada(p1, LUNES, "08:00", "10.00");
		confirmada(p1, LUNES.plusDays(2), "21:30", "11.00");
		confirmada(p2, LUNES.plusDays(1), "14:00", "12.00");
		confirmada(p3, LUNES, "08:00", "13.00");
		confirmada(p3, LUNES.plusDays(1), "08:00", "14.00");
		confirmada(p3, LUNES.plusDays(2), "08:00", "15.00");
		confirmada(p3, LUNES.plusDays(3), "08:00", "999.00"); // fuera del rango

		var j = ocupacion(rango(LUNES, LUNES.plusDays(2)));

		assertFila(fila(j, "P1"), 30, 2, "0.0667", "21.00");
		assertFila(fila(j, "P2"), 30, 1, "0.0333", "12.00");
		assertFila(fila(j, "P3"), 30, 3, "0.1000", "42.00");
		assertThat(j.read("$.ranurasOfrecidas", Integer.class)).isEqualTo(90);
		assertThat(j.read("$.ranurasOcupadas", Integer.class)).isEqualTo(6);
		assertThat(num(j.read("$.ocupacionGlobal"))).isEqualByComparingTo("0.0667");
	}

	@Test
	void elHorarioDelClubDefineLaRejilla() throws Exception {
		club.setHoraApertura(LocalTime.of(9, 0));
		club.setHoraCierre(LocalTime.of(21, 0));
		clubs.saveAndFlush(club);

		// 09:00..20:30 con casillas de 90 minutos: 8
		assertFila(fila(ocupacion(rango(LUNES, LUNES)), "P1"), 8, 0, "0.0000", "0.00");
	}

	@Test
	void elDomingoDelCambioDeHoraOfreceLasMismasDiezCasillas() throws Exception {
		var domingo = d(2026, 10, 25);
		confirmada(p1, domingo, "08:00", "5.00");
		confirmada(p1, domingo, "21:30", "6.00");

		assertFila(fila(ocupacion(rango(domingo, domingo)), "P1"), 10, 2, "0.2000", "11.00");

		var primavera = d(2027, 3, 28);
		confirmada(p2, primavera, "08:00", "5.00");
		assertFila(fila(ocupacion(rango(primavera, primavera)), "P2"), 10, 1, "0.1000", "5.00");
	}

	@Test
	void unClubSinPistasNoTieneOcupacion() throws Exception {
		var vacio = crearClub("Club vacio");
		String adminVacio = bearerAdminDe(vacio, "admin@vacio.com");

		var j = json(pedir(adminVacio, vacio, "ocupacion", rango(LUNES, LUNES)).andExpect(status().isOk()));

		assertThat(j.read("$.pistas", List.class)).isEmpty();
		assertThat(j.read("$.ranurasOfrecidas", Integer.class)).isZero();
		assertThat(j.read("$.ocupacionGlobal", Object.class)).isNull();
	}
}
