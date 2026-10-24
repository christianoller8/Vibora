package com.vibora.factura;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cancelar una reserva facturada emite, en la misma transaccion, la rectificativa que la anula. */
class FacturaRectificativaTests extends FacturaApiTestBase {

	private Jug ana;

	private long reservaFacturada(int hora, int minuto) throws Exception {
		long reserva = reservaEnElLunes(ana, hora, minuto);
		facturar(ana, reserva).andExpect(status().isCreated());
		return reserva;
	}

	private List<Map<String, Object>> facturasDe(long reservaId) {
		em.flush();
		return jdbc.queryForList("select * from factura where reserva_id = ? order by id", reservaId);
	}

	private List<Map<String, Object>> avisosDeRectificativa(Jug j) {
		em.flush();
		return jdbc.queryForList("select * from notificacion where usuario_id = ? and tipo = 'FACTURA_RECTIFICATIVA' order by id",
				j.id());
	}

	@BeforeEach
	void preparar() throws Exception {
		configurarDatosFiscales(admin, club);
		ana = ana("ana@vibora.com");
	}

	// ---- quien cancela ----

	@Test
	void alCancelarLaReservaElOrganizador_seEmiteLaRectificativaConLosImportesEnNegativo() throws Exception {
		long reserva = reservaFacturada(11, 0);
		long original = jdbc.queryForObject("select id from factura where reserva_id = ?", Long.class, reserva);
		reloj.fijar(Instant.parse("2030-01-02T09:00:00Z"));

		cancelar(ana, reserva).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADA"));

		facturasDeReserva(ana, reserva).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].id").value(original))
				.andExpect(jsonPath("$[0].tipo").value("ORIGINAL"))
				.andExpect(jsonPath("$[0].total").value(45.0))
				.andExpect(jsonPath("$[1].tipo").value("RECTIFICATIVA"))
				.andExpect(jsonPath("$[1].serie").value("F2030"))
				.andExpect(jsonPath("$[1].numero").value(2))
				.andExpect(jsonPath("$[1].numeroCompleto").value("F2030-0002"))
				.andExpect(jsonPath("$[1].rectificaA").value(original))
				.andExpect(jsonPath("$[1].reservaId").value(reserva))
				.andExpect(jsonPath("$[1].emitidaEn").value("2030-01-02T09:00:00Z"))
				.andExpect(jsonPath("$[1].base").value(-37.19))
				.andExpect(jsonPath("$[1].tipoIva").value(21.0))
				.andExpect(jsonPath("$[1].cuotaIva").value(-7.81))
				.andExpect(jsonPath("$[1].total").value(-45.0));

		// copia exacta de los datos de la original (emisor, receptor y concepto)
		Map<String, Object> o = facturasDe(reserva).get(0);
		Map<String, Object> r = facturasDe(reserva).get(1);
		for (String columna : List.of("club_id", "receptor_usuario_id", "emisor_razon_social", "emisor_nif", "emisor_direccion",
				"receptor_nombre", "concepto", "serie")) {
			assertThat(r.get(columna)).as(columna).isEqualTo(o.get(columna));
		}
		assertThat(r.get("receptor_nif")).isEqualTo(o.get("receptor_nif"));
		assertThat(contador(club, 2030)).isEqualTo(2);
	}

	@Test
	void elReceptorRecibeElAvisoFacturaRectificativa_conLosNumerosEnElTexto() throws Exception {
		long reserva = reservaFacturada(11, 0);

		cancelar(ana, reserva).andExpect(status().isOk());

		String cuerpo = notificaciones(ana).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString(StandardCharsets.UTF_8);
		assertThat(cuerpo).contains("\"tipo\":\"FACTURA_RECTIFICATIVA\"");
		List<Map<String, Object>> avisos = avisosDeRectificativa(ana);
		assertThat(avisos).hasSize(1);
		assertThat((String) avisos.get(0).get("mensaje")).contains("F2030-0002", "F2030-0001", "-45,00 €", "Pista 1 de Club A");
		assertThat(avisos.get(0).get("reserva_id")).isEqualTo(reserva);
		assertThat(avisos.get(0).get("leida")).isEqualTo(false);
	}

	@Test
	void siCancelaElAdministrador_laRectificativaYElAvisoVanParaElOrganizador() throws Exception {
		long reserva = reservaFacturada(11, 0);

		cancelar(admin, reserva).andExpect(status().isOk());

		facturasDeReserva(admin, reserva).andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[1].tipo").value("RECTIFICATIVA"))
				.andExpect(jsonPath("$[1].total").value(-45.0))
				.andExpect(jsonPath("$[1].receptor.nombre").value("Ana García"));
		assertThat(avisosDeRectificativa(ana)).hasSize(1);
		assertThat(avisosDeRectificativa(admin)).isEmpty();
		// la rectificativa la ve el receptor
		misFacturas(ana).andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].tipo").value("RECTIFICATIVA"));
	}

	@Test
	void alCancelarLasAfectadasPorUnBloqueo_cadaReservaFacturadaGeneraSuRectificativa_yLaNoFacturadaNo() throws Exception {
		Jug beto = jugador("beto@vibora.com", "Beto", "Ruiz");
		long facturadaAna = reservaFacturada(8, 0);
		long sinFacturar = reservaEnElLunes(beto, 9, 30);
		long facturadaBeto = reservaEnElLunes(beto, 11, 0);
		facturar(beto, facturadaBeto).andExpect(status().isCreated());
		assertThat(numerosEmitidos(club)).containsExactly("F2030-0001", "F2030-0002");

		String bloqueo = mvc.perform(post("/api/clubes/" + club.getId() + "/pistas/" + pista.getId() + "/bloqueos")
				.header("Authorization", admin.token()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"motivo\":\"Reparar el cristal\",\"desde\":\"2030-01-07T00:00:00Z\",\"hasta\":\"2030-01-08T00:00:00Z\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		mvc.perform(post("/api/clubes/" + club.getId() + "/pistas/" + pista.getId() + "/bloqueos/" + idDe(bloqueo)
				+ "/cancelar-afectadas").header("Authorization", admin.token())).andExpect(status().isOk())
				.andExpect(jsonPath("$.canceladas").value(3));

		assertThat(facturasDe(facturadaAna)).extracting(f -> f.get("tipo")).containsExactly("ORIGINAL", "RECTIFICATIVA");
		assertThat(facturasDe(facturadaBeto)).extracting(f -> f.get("tipo")).containsExactly("ORIGINAL", "RECTIFICATIVA");
		assertThat(facturasDe(sinFacturar)).isEmpty();
		assertThat(numerosEmitidos(club)).hasSize(4).containsSubsequence("F2030-0001", "F2030-0002")
				.contains("F2030-0003", "F2030-0004"); // las dos rectificativas siguen la misma serie
		assertThat(contador(club, 2030)).isEqualTo(4);
		assertThat(jdbc.queryForObject("select sum(total) from factura", BigDecimal.class)).isEqualByComparingTo("0");
		assertThat(avisosDeRectificativa(ana)).hasSize(1);
		assertThat(avisosDeRectificativa(beto)).hasSize(1); // y no por la no facturada
		// cada rectificativa apunta a la original de SU reserva
		assertThat(jdbc.queryForObject("select count(*) from factura r join factura o on o.id = r.rectifica_a "
				+ "where r.tipo = 'RECTIFICATIVA' and o.tipo = 'ORIGINAL' and o.reserva_id = r.reserva_id", Long.class)).isEqualTo(2);
	}

	// ---- cuando no se emite ----

	@Test
	void unaReservaNuncaFacturada_alCancelarse_noGeneraNingunaFactura() throws Exception {
		long reserva = reservaEnElLunes(ana, 11, 0);

		cancelar(ana, reserva).andExpect(status().isOk());

		assertThat(contarFacturas()).isZero();
		assertThat(contador(club, 2030)).isZero();
		assertThat(avisosDeRectificativa(ana)).isEmpty();
	}

	@Test
	void cancelarDosVeces_noEmiteUnaSegundaRectificativa() throws Exception {
		long reserva = reservaFacturada(11, 0);

		cancelar(ana, reserva).andExpect(status().isOk());
		cancelar(ana, reserva).andExpect(status().isOk()); // cancelar ya es idempotente
		cancelar(admin, reserva).andExpect(status().isOk());

		assertThat(facturasDe(reserva)).hasSize(2);
		assertThat(contador(club, 2030)).isEqualTo(2);
		assertThat(avisosDeRectificativa(ana)).hasSize(1);
	}

	@Test
	void unaCancelacionRechazada_noEmiteNada() throws Exception {
		long reserva = reservaFacturada(11, 0);
		Jug beto = jugador("beto@vibora.com", "Beto", "Ruiz");

		cancelar(beto, reserva).andExpect(status().isForbidden()); // no es suyo
		reloj.fijar(lunes(10, 0)); // el plazo de 24 h ya paso
		cancelar(ana, reserva).andExpect(status().isConflict());

		assertThat(facturasDe(reserva)).hasSize(1);
		assertThat(contador(club, 2030)).isEqualTo(1);
		assertThat(avisosDeRectificativa(ana)).isEmpty();
	}

	@Test
	void despuesDeCancelarYRectificar_yaNoSePuedeVolverAFacturarEsaReserva() throws Exception {
		long reserva = reservaFacturada(11, 0);
		cancelar(ana, reserva).andExpect(status().isOk());

		facturar(ana, reserva).andExpect(status().isConflict());
		facturar(admin, reserva).andExpect(status().isConflict());

		assertThat(facturasDe(reserva)).hasSize(2);
	}

	@Test
	void laRectificativaTomaElAnioDeSuPropiaEmision() throws Exception {
		reloj.fijar(Instant.parse("2030-12-31T10:00:00Z"));
		long reserva = idDe(reservar(ana, pista, local(LocalDate.of(2031, 1, 6), 11, 0)).andExpect(status().isCreated()));
		facturar(ana, reserva).andExpect(jsonPath("$.numeroCompleto").value("F2030-0001"));

		reloj.fijar(Instant.parse("2031-01-02T10:00:00Z"));
		cancelar(ana, reserva).andExpect(status().isOk());

		facturasDeReserva(ana, reserva).andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[1].numeroCompleto").value("F2031-0001"))
				.andExpect(jsonPath("$[1].serie").value("F2031"))
				.andExpect(jsonPath("$[1].emitidaEn").value("2031-01-02T10:00:00Z"))
				.andExpect(jsonPath("$[1].rectificaA").value(jdbc.queryForObject("select id from factura where tipo = 'ORIGINAL'",
						Long.class)));
		assertThat(contador(club, 2030)).isEqualTo(1);
		assertThat(contador(club, 2031)).isEqualTo(1);
	}

	@Test
	void laRectificativaNoDependeDeQueLosDatosFiscalesCambien() throws Exception {
		long reserva = reservaFacturada(11, 0);
		datosFiscales(admin, club, """
				{"razonSocial":"Otra Razón S.A.","nif":"A58818501","direccionFiscal":"Otra calle 1","tipoIva":10}
				""").andExpect(status().isOk());

		cancelar(ana, reserva).andExpect(status().isOk());

		facturasDeReserva(ana, reserva).andExpect(jsonPath("$[1].emisor.razonSocial").value("Pádel Víbora S.L.")) // el de la original
				.andExpect(jsonPath("$[1].emisor.nif").value("B12345674"))
				.andExpect(jsonPath("$[1].tipoIva").value(21.0))
				.andExpect(jsonPath("$[1].total").value(-45.0));
	}

	@Test
	void cancelarUnaReservaFacturadaTambienReembolsaYAvisaComoAntes() throws Exception {
		Jug beto = jugador("beto@vibora.com", "Beto", "Ruiz");
		long reserva = reservaFacturada(11, 0);
		mvc.perform(put("/api/reservas/" + reserva + "/abierta")
				.header("Authorization", ana.token()).contentType(MediaType.APPLICATION_JSON).content("{\"abierta\":true}"))
				.andExpect(status().isOk());
		mvc.perform(post("/api/reservas/" + reserva + "/apuntarse").header("Authorization", beto.token())).andExpect(status().isCreated());

		cancelar(ana, reserva).andExpect(status().isOk());

		notificaciones(beto).andExpect(jsonPath("$[0].tipo").value("RESERVA_CANCELADA"))
				.andExpect(jsonPath("$[0].mensaje").value(containsString("Se ha cancelado la reserva")));
		assertThat(avisosDeRectificativa(beto)).isEmpty(); // la rectificativa solo va al receptor
		em.flush();
		assertThat(jdbc.queryForObject("select estado from cargo", String.class)).isEqualTo("ANULADO");
	}
}
