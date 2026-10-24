package com.vibora.metricas;

import com.vibora.club.Club;
import com.vibora.factura.Factura;
import com.vibora.factura.FacturaRepository;
import com.vibora.factura.TipoFactura;
import com.vibora.pista.Pista;
import com.vibora.reserva.EstadoReserva;
import com.vibora.reserva.Reserva;
import com.vibora.tarifa.TarifaFranja;
import com.vibora.tarifa.TarifaFranjaRepository;
import com.vibora.tarifa.TipoTarifa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/clubes/{id}/metricas/resumen. */
class MetricasResumenTests extends MetricasApiTestBase {

	@Autowired FacturaRepository facturas;
	@Autowired TarifaFranjaRepository tarifas;

	private static final LocalDate LUNES = d(2030, 1, 7);
	private static final LocalDate DOMINGO = d(2030, 1, 13);

	Pista p1;
	Pista p2;

	@BeforeEach
	void pistasDelClub() {
		p1 = pista(club, "P1");
		p2 = pista(club, "P2");
	}

	/** Semana mixta del lunes 7 al domingo 13 de enero de 2030 (mas dos reservas justo fuera del rango). */
	private void semanaMixta(Club c, Pista a, Pista b) {
		confirmada(a, LUNES, "08:00", "30.00");
		confirmada(a, LUNES, "09:30", "30.00");
		confirmada(b, d(2030, 1, 8), "20:00", "45.50");
		confirmada(b, d(2030, 1, 12), "21:30", "40.00");
		cancelada(a, d(2030, 1, 13), "12:30", "30.00");
		cancelada(b, d(2030, 1, 9), "08:00", "25.00");
		confirmada(a, d(2030, 1, 6), "21:30", "99.00"); // el domingo anterior
		confirmada(a, d(2030, 1, 14), "08:00", "77.00"); // el lunes siguiente
	}

	@Test
	void resumenDeUnaSemanaMixta() throws Exception {
		semanaMixta(club, p1, p2);

		var r = comoAdmin("resumen", rango(LUNES, DOMINGO)).andExpect(status().isOk());

		var j = json(r);
		assertThat(j.read("$.desde", String.class)).isEqualTo("2030-01-07");
		assertThat(j.read("$.hasta", String.class)).isEqualTo("2030-01-13");
		assertThat(num(j.read("$.ingresos"))).isEqualByComparingTo("145.50");
		assertThat(j.read("$.reservasConfirmadas", Integer.class)).isEqualTo(4);
		assertThat(j.read("$.reservasCanceladas", Integer.class)).isEqualTo(2);
		assertThat(num(j.read("$.tasaCancelacion"))).isEqualByComparingTo("0.3333");
		assertThat(num(j.read("$.ticketMedio"))).isEqualByComparingTo("36.38"); // 145.50 / 4 = 36.375 -> 36.38
		assertThat(num(j.read("$.ingresosPerdidosPorCancelacion"))).isEqualByComparingTo("55.00");
		// 4 ocupadas de 2 pistas x 7 dias x 10 casillas = 140
		assertThat(num(j.read("$.ocupacionGlobal"))).isEqualByComparingTo("0.0286");
		assertThat(num(j.read("$.facturado.total"))).isEqualByComparingTo("0.00");
		assertThat(j.read("$.facturado.numeroFacturas", Integer.class)).isZero();
		assertThat(j.read("$.facturado.numeroRectificativas", Integer.class)).isZero();
		// el dinero siempre sale con 2 decimales, tambien en la cadena JSON
		assertThat(cuerpo(r)).contains("\"ingresos\":145.50").contains("\"ingresosPerdidosPorCancelacion\":55.00");
	}

	@Test
	void unClubConPistasPeroSinReservasDevuelveCerosYNulosSinDividirPorCero() throws Exception {
		comoAdmin("resumen", rango(LUNES, DOMINGO)).andExpect(status().isOk())
				.andExpect(jsonPath("$.ingresos").value(0.0)).andExpect(jsonPath("$.reservasConfirmadas").value(0))
				.andExpect(jsonPath("$.reservasCanceladas").value(0)).andExpect(jsonPath("$.tasaCancelacion").value((Object) null))
				.andExpect(jsonPath("$.ticketMedio").value((Object) null))
				.andExpect(jsonPath("$.ingresosPerdidosPorCancelacion").value(0.0))
				.andExpect(jsonPath("$.ocupacionGlobal").value(0.0))
				.andExpect(jsonPath("$.facturado.base").value(0.0)).andExpect(jsonPath("$.facturado.cuotaIva").value(0.0))
				.andExpect(jsonPath("$.facturado.total").value(0.0)).andExpect(jsonPath("$.facturado.numeroFacturas").value(0))
				.andExpect(jsonPath("$.facturado.numeroRectificativas").value(0));
	}

	@Test
	void unClubSinPistasNiReservasTienePorcentajesNulos() throws Exception {
		Club vacio = crearClub("Club vacio");
		String adminVacio = bearerAdminDe(vacio, "admin@vacio.com");

		pedir(adminVacio, vacio, "resumen", rango(LUNES, DOMINGO)).andExpect(status().isOk())
				.andExpect(jsonPath("$.ingresos").value(0.0)).andExpect(jsonPath("$.ocupacionGlobal").value((Object) null))
				.andExpect(jsonPath("$.tasaCancelacion").value((Object) null)).andExpect(jsonPath("$.ticketMedio").value((Object) null));
	}

	@Test
	void soloHayCanceladasLaTasaEsUnoYNoHayTicketMedio() throws Exception {
		cancelada(p1, LUNES, "08:00", "30.00");

		comoAdmin("resumen", rango(LUNES, DOMINGO)).andExpect(jsonPath("$.tasaCancelacion").value(1.0))
				.andExpect(jsonPath("$.ticketMedio").value((Object) null)).andExpect(jsonPath("$.ingresos").value(0.0))
				.andExpect(jsonPath("$.ingresosPerdidosPorCancelacion").value(30.0));
	}

	@Test
	void elTicketMedioSeRedondeaAMedioHaciaArriba() throws Exception {
		confirmada(p1, LUNES, "08:00", "10.00");
		confirmada(p1, LUNES, "09:30", "10.01");

		comoAdmin("resumen", rango(LUNES, LUNES)).andExpect(jsonPath("$.ticketMedio").value(10.01)); // 20.01 / 2 = 10.005
	}

	@Test
	void otroClubNuncaCambiaLosNumeros() throws Exception {
		semanaMixta(club, p1, p2);
		String antes = cuerpo(comoAdmin("resumen", rango(LUNES, DOMINGO)));

		Club otro = crearClub("Club B");
		Pista b1 = pista(otro, "B1");
		confirmada(b1, LUNES, "08:00", "500.00");
		cancelada(b1, LUNES, "09:30", "400.00");
		bloqueo(b1, local(LUNES, "08:00"), null, null);

		assertThat(cuerpo(comoAdmin("resumen", rango(LUNES, DOMINGO)))).isEqualTo(antes);
	}

	@Test
	void unaReservaPerteneceAlDiaLocalDeSuInicioNoAlDiaUtc() throws Exception {
		// 23:30 del 24-oct-2026 en Madrid (CEST) = 21:30Z; 00:30 del 25 = 22:30Z del 24 en UTC
		reserva(p1, local(d(2026, 10, 24), "23:30"), EstadoReserva.CONFIRMADA, "10.00");
		reserva(p2, local(d(2026, 10, 25), "00:30"), EstadoReserva.CONFIRMADA, "20.00");

		comoAdmin("resumen", rango(d(2026, 10, 25), d(2026, 10, 25))).andExpect(jsonPath("$.ingresos").value(20.0))
				.andExpect(jsonPath("$.reservasConfirmadas").value(1));
		comoAdmin("resumen", rango(d(2026, 10, 24), d(2026, 10, 24))).andExpect(jsonPath("$.ingresos").value(10.0))
				.andExpect(jsonPath("$.reservasConfirmadas").value(1));
	}

	@Test
	void hastaEsInclusivoHastaElUltimoMinutoDelDia() throws Exception {
		reserva(p1, local(LUNES, "23:30"), EstadoReserva.CONFIRMADA, "10.00"); // ultimo minuto de hasta
		reserva(p2, local(LUNES.plusDays(1), "00:00"), EstadoReserva.CONFIRMADA, "20.00"); // ya es el dia siguiente
		reserva(p1, local(LUNES, "00:00"), EstadoReserva.CONFIRMADA, "5.00"); // primer instante de desde

		comoAdmin("resumen", rango(LUNES, LUNES)).andExpect(jsonPath("$.ingresos").value(15.0))
				.andExpect(jsonPath("$.reservasConfirmadas").value(2));
	}

	// ---- facturado ----

	private Factura factura(Reserva reserva, TipoFactura tipo, Factura rectifica, Instant emitida, int numero, String base,
			String cuota, String total) {
		Factura f = new Factura();
		f.setClub(reserva.getPista().getClub());
		f.setReserva(reserva);
		f.setReceptorUsuario(organizador);
		f.setSerie("F2030");
		f.setNumero(numero);
		f.setTipo(tipo);
		f.setRectificaA(rectifica);
		f.setEmitidaEn(emitida);
		f.setEmisorRazonSocial("Club S.L.");
		f.setEmisorNif("B12345674");
		f.setEmisorDireccion("Calle 1");
		f.setReceptorNombre("Ana");
		f.setConcepto("Alquiler");
		f.setBase(new BigDecimal(base));
		f.setTipoIva(new BigDecimal("21.00"));
		f.setCuotaIva(new BigDecimal(cuota));
		f.setTotal(new BigDecimal(total));
		return facturas.saveAndFlush(f);
	}

	@Test
	void lasFacturasEmitidasEnElRangoSeSumanNetasDeRectificativas() throws Exception {
		Reserva r1 = confirmada(p1, LUNES, "08:00", "45.00");
		Reserva r2 = confirmada(p1, LUNES, "09:30", "121.00");
		Reserva r3 = confirmada(p1, LUNES, "11:00", "10.00");
		Factura o1 = factura(r1, TipoFactura.ORIGINAL, null, Instant.parse("2030-01-08T10:00:00Z"), 1, "37.19", "7.81", "45.00");
		// 23:30Z del 9 = 00:30 del 10 en Madrid: dentro del rango (que empieza el 7 y acaba el 13)
		factura(r2, TipoFactura.ORIGINAL, null, Instant.parse("2030-01-09T23:30:00Z"), 2, "100.00", "21.00", "121.00");
		factura(r1, TipoFactura.RECTIFICATIVA, o1, Instant.parse("2030-01-10T09:00:00Z"), 3, "-37.19", "-7.81", "-45.00");
		// 23:30Z del 13 = 00:30 del 14 en Madrid: FUERA del rango
		factura(r3, TipoFactura.ORIGINAL, null, Instant.parse("2030-01-13T23:30:00Z"), 4, "8.26", "1.74", "10.00");
		// 22:59Z del 6 = 23:59 del 6 en Madrid: tambien fuera
		Reserva r4 = confirmada(p2, LUNES, "08:00", "10.00");
		factura(r4, TipoFactura.ORIGINAL, null, Instant.parse("2030-01-06T22:59:00Z"), 5, "8.26", "1.74", "10.00");

		var j = json(comoAdmin("resumen", rango(LUNES, DOMINGO)).andExpect(status().isOk()));

		assertThat(num(j.read("$.facturado.base"))).isEqualByComparingTo("100.00");
		assertThat(num(j.read("$.facturado.cuotaIva"))).isEqualByComparingTo("21.00");
		assertThat(num(j.read("$.facturado.total"))).isEqualByComparingTo("121.00");
		assertThat(j.read("$.facturado.numeroFacturas", Integer.class)).isEqualTo(2);
		assertThat(j.read("$.facturado.numeroRectificativas", Integer.class)).isEqualTo(1);
	}

	@Test
	void lasFacturasDeOtroClubNoCuentan() throws Exception {
		Club otro = crearClub("Club B");
		Pista b1 = pista(otro, "B1");
		Reserva rb = confirmada(b1, LUNES, "08:00", "45.00");
		factura(rb, TipoFactura.ORIGINAL, null, Instant.parse("2030-01-08T10:00:00Z"), 1, "37.19", "7.81", "45.00");

		comoAdmin("resumen", rango(LUNES, DOMINGO)).andExpect(jsonPath("$.facturado.numeroFacturas").value(0))
				.andExpect(jsonPath("$.facturado.total").value(0.0));
	}

	@Test
	void elCicloRealEmitirYCancelarSeVeComoFacturaMasRectificativaConTotalNeto() throws Exception {
		tarifaPlana(club, "30.00");
		long reservaId = reservarPorApi(p1, local(LUNES, "09:30"));
		jdbc.update("update club set razon_social = 'Padel Vibora S.L.', nif = 'B12345674', direccion_fiscal = 'Calle 5' where id = ?",
				club.getId());
		em.clear();
		mvc.perform(post("/api/reservas/" + reservaId + "/factura").header("Authorization", bearer("org@a.com")))
				.andExpect(status().isCreated());
		mvc.perform(post("/api/reservas/" + reservaId + "/cancelar").header("Authorization", admin)).andExpect(status().isOk());

		// reloj en 2030-01-01T00:00Z = 01:00 del 1 de enero en Madrid: ambas facturas se emitieron "hoy"
		var j = json(comoAdmin("resumen", rango(d(2030, 1, 1), d(2030, 1, 1))).andExpect(status().isOk()));
		assertThat(j.read("$.facturado.numeroFacturas", Integer.class)).isEqualTo(1);
		assertThat(j.read("$.facturado.numeroRectificativas", Integer.class)).isEqualTo(1);
		assertThat(num(j.read("$.facturado.total"))).isEqualByComparingTo("0.00");
		assertThat(num(j.read("$.facturado.base"))).isEqualByComparingTo("0.00");
		assertThat(num(j.read("$.facturado.cuotaIva"))).isEqualByComparingTo("0.00");
		// y la reserva, cancelada, cuenta como cancelada el dia de juego (7 de enero), no el de la cancelacion
		var semana = json(comoAdmin("resumen", rango(LUNES, DOMINGO)));
		assertThat(semana.read("$.reservasCanceladas", Integer.class)).isEqualTo(1);
		assertThat(num(semana.read("$.ingresosPerdidosPorCancelacion"))).isEqualByComparingTo("45.00");
		assertThat(num(semana.read("$.ingresos"))).isEqualByComparingTo("0.00");
	}

	private void tarifaPlana(Club c, String precioHora) {
		TarifaFranja t = com.vibora.TestData.tarifa(c, DayOfWeek.MONDAY, TipoTarifa.NORMAL);
		t.setHoraInicio(LocalTime.of(8, 0));
		t.setHoraFin(LocalTime.of(23, 0));
		t.setPrecioHora(new BigDecimal(precioHora));
		tarifas.saveAndFlush(t);
	}

	private long reservarPorApi(Pista p, Instant inicio) throws Exception {
		String cuerpo = mvc.perform(post("/api/clubes/" + club.getId() + "/pistas/" + p.getId() + "/reservas")
				.header("Authorization", bearer("org@a.com")).contentType(MediaType.APPLICATION_JSON)
				.content("{\"inicio\":\"" + inicio + "\"}")).andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString();
		return ((Number) com.jayway.jsonpath.JsonPath.read(cuerpo, "$.id")).longValue();
	}

	@Test
	void elEndpointNoDevuelveDatosDeJugadores() throws Exception {
		confirmada(p1, LUNES, "08:00", "30.00");

		for (String recurso : RECURSOS) {
			String cuerpo = cuerpo(comoAdmin(recurso, rango(LUNES, DOMINGO)));
			assertThat(cuerpo).as(recurso).doesNotContain("org@a.com").doesNotContain("Ana").doesNotContain("García")
					.doesNotContain("email").doesNotContain("usuario");
		}
	}
}
