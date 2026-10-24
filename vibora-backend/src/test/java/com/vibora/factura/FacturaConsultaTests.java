package com.vibora.factura;

import com.vibora.club.Club;
import com.vibora.usuario.Rol;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Listados y consulta de facturas: filtros, paginas, totales, visibilidad y que nunca se filtren emails. */
class FacturaConsultaTests extends FacturaApiTestBase {

	private Jug ana;
	private Jug beto;

	@BeforeEach
	void preparar() throws Exception {
		configurarDatosFiscales(admin, club);
		ana = ana("ana@vibora.com");
		beto = jugador("beto@vibora.com", "Beto", "Ruiz");
	}

	private String cuerpo(org.springframework.test.web.servlet.ResultActions r) throws Exception {
		return r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	/**
	 * Siembra n facturas ORIGINALES del club para el usuario, una por reserva, directamente en la BD (la API no deja
	 * emitir tantas a la vez). Sus numeros son F2032-0001..n y se emiten un minuto despues cada una.
	 */
	private void sembrarFacturas(Jug receptor, int n) {
		sembrarFacturas(receptor, n, 0);
	}

	/** Igual, pero las reservas y los numeros empiezan despues de los primeros {@code desplazamiento}. */
	private void sembrarFacturas(Jug receptor, int n, int desplazamiento) {
		jdbc.update("""
				insert into reserva (pista_id, usuario_id, inicio, fin, estado, precio_total)
				select ?, ?, timestamptz '2032-01-01 00:00+00' + g * interval '2 hours',
				       timestamptz '2032-01-01 00:00+00' + g * interval '2 hours' + interval '90 minutes', 'CONFIRMADA', 45
				from generate_series(?::int + 1, ?::int + ?::int) g
				""", pista.getId(), receptor.id(), desplazamiento, desplazamiento, n);
		jdbc.update("""
				insert into factura (club_id, reserva_id, receptor_usuario_id, serie, numero, tipo, emitida_en,
				    emisor_razon_social, emisor_nif, emisor_direccion, receptor_nombre, concepto, base, tipo_iva, cuota_iva, total)
				select ?, r.id, r.usuario_id, 'F2032', (?::int + row_number() over (order by r.id))::int, 'ORIGINAL',
				       timestamptz '2032-06-01 00:00+00' + (?::int + row_number() over (order by r.id)) * interval '1 minute',
				       'Club', 'B12345674', 'Calle', 'Receptor', 'Concepto', 37.19, 21.00, 7.81, 45.00
				from reserva r where r.inicio >= timestamptz '2032-01-01 00:00+00' and r.usuario_id = ?
				""", club.getId(), desplazamiento, desplazamiento, receptor.id());
	}

	// ---- facturas de una reserva ----

	@Test
	void lasFacturasDeUnaReserva_laVenElOrganizadorYElAdmin_yEstaVaciaSiNoSeFacturo() throws Exception {
		long reserva = reservaEnElLunes(ana, 11, 0);

		facturasDeReserva(ana, reserva).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
		facturasDeReserva(admin, reserva).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

		long id = facturaEmitida(ana, reserva);
		facturasDeReserva(ana, reserva).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(id));
		facturasDeReserva(admin, reserva).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].tipo").value("ORIGINAL"));
	}

	@Test
	void lasFacturasDeUnaReserva_otrosUsuarios403_sinToken401_inexistente404() throws Exception {
		long reserva = reservaEnElLunes(ana, 11, 0);
		facturaEmitida(ana, reserva);
		Club otro = crearClub("Club B");
		Jug adminOtro = administrador(otro, "admin-b@vibora.com");

		facturasDeReserva(beto, reserva).andExpect(status().isForbidden());
		facturasDeReserva(adminOtro, reserva).andExpect(status().isForbidden());
		mvc.perform(get("/api/reservas/" + reserva + "/facturas")).andExpect(status().isUnauthorized());
		facturasDeReserva(ana, 999999).andExpect(status().isNotFound());
	}

	// ---- mis facturas ----

	@Test
	void misFacturas_soloLasMias_lasMasRecientesPrimero() throws Exception {
		long r1 = reservaEnElLunes(ana, 8, 0);
		long r2 = reservaEnElLunes(ana, 9, 30);
		long r3 = reservaEnElLunes(beto, 11, 0);
		long r4 = reservaEnElLunes(ana, 12, 30);
		facturaEmitida(ana, r1);
		reloj.fijar(Instant.parse("2030-01-02T10:00:00Z"));
		facturaEmitida(ana, r2);
		facturaEmitida(beto, r3);
		reloj.fijar(Instant.parse("2030-01-03T10:00:00Z"));
		facturaEmitida(admin, r4); // la emite el admin, pero es de Ana

		misFacturas(ana).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].numeroCompleto").value("F2030-0004"))
				.andExpect(jsonPath("$[1].numeroCompleto").value("F2030-0002"))
				.andExpect(jsonPath("$[2].numeroCompleto").value("F2030-0001"));
		misFacturas(beto).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].numeroCompleto").value("F2030-0003"));
		misFacturas(admin).andExpect(jsonPath("$.length()").value(0)); // el admin no es receptor de ninguna
		mvc.perform(get("/api/facturas")).andExpect(status().isUnauthorized());
	}

	@Test
	void misFacturas_conMismaFechaDesempataPorIdDescendente() throws Exception {
		facturaEmitida(ana, reservaEnElLunes(ana, 8, 0));
		facturaEmitida(ana, reservaEnElLunes(ana, 9, 30));
		facturaEmitida(ana, reservaEnElLunes(ana, 11, 0));

		misFacturas(ana).andExpect(jsonPath("$[0].numero").value(3)).andExpect(jsonPath("$[1].numero").value(2))
				.andExpect(jsonPath("$[2].numero").value(1));
	}

	@Test
	void misFacturas_paginaYLimite() throws Exception {
		sembrarFacturas(ana, 7);

		mvc.perform(get("/api/facturas").param("page", "0").param("limit", "3").header("Authorization", ana.token()))
				.andExpect(jsonPath("$.length()").value(3)).andExpect(jsonPath("$[0].numero").value(7))
				.andExpect(jsonPath("$[2].numero").value(5));
		mvc.perform(get("/api/facturas").param("page", "1").param("limit", "3").header("Authorization", ana.token()))
				.andExpect(jsonPath("$.length()").value(3)).andExpect(jsonPath("$[0].numero").value(4));
		mvc.perform(get("/api/facturas").param("page", "2").param("limit", "3").header("Authorization", ana.token()))
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].numero").value(1));
		mvc.perform(get("/api/facturas").param("page", "3").param("limit", "3").header("Authorization", ana.token()))
				.andExpect(jsonPath("$.length()").value(0));
		misFacturas(ana).andExpect(jsonPath("$.length()").value(7)); // sin parametros: pagina 0, limite por defecto
	}

	@Test
	void misFacturas_elLimiteTieneUnTopeDe100_yElPorDefectoEsDe20() throws Exception {
		sembrarFacturas(ana, 105);

		misFacturas(ana).andExpect(jsonPath("$.length()").value(20));
		mvc.perform(get("/api/facturas").param("limit", "1000").header("Authorization", ana.token()))
				.andExpect(jsonPath("$.length()").value(100));
		mvc.perform(get("/api/facturas").param("limit", "100").param("page", "1").header("Authorization", ana.token()))
				.andExpect(jsonPath("$.length()").value(5));
	}

	@ParameterizedTest
	@ValueSource(strings = { "page=-1", "limit=0", "limit=-5", "page=abc", "limit=x" })
	void misFacturas_parametrosImposibles_400(String consulta) throws Exception {
		String[] par = consulta.split("=");
		mvc.perform(get("/api/facturas").param(par[0], par[1]).header("Authorization", ana.token()))
				.andExpect(status().isBadRequest());
	}

	// ---- facturas del club ----

	/** A (1-ene 11:00 Madrid), B (2-ene 00:30 Madrid, aunque aun es 1-ene en UTC), C (3-ene) y la rectificativa D de C (4-ene). */
	private void escenarioDelClub() throws Exception {
		long ra = reservaEnElLunes(ana, 8, 0);
		long rb = reservaEnElLunes(beto, 9, 30);
		long rc = reservaEnElLunes(ana, 11, 0);
		reloj.fijar(Instant.parse("2030-01-01T10:00:00Z"));
		facturaEmitida(ana, ra);
		reloj.fijar(Instant.parse("2030-01-01T23:30:00Z"));
		facturaEmitida(beto, rb);
		reloj.fijar(Instant.parse("2030-01-03T10:00:00Z"));
		facturaEmitida(ana, rc);
		reloj.fijar(Instant.parse("2030-01-04T10:00:00Z"));
		cancelar(ana, rc).andExpect(status().isOk());
	}

	@Test
	void elClubVeTodasSusFacturas_recientesPrimero_conLosTotalesDelConjunto() throws Exception {
		escenarioDelClub();

		facturasDelClub(admin, club).andExpect(status().isOk())
				.andExpect(jsonPath("$.facturas.length()").value(4))
				.andExpect(jsonPath("$.facturas[0].numeroCompleto").value("F2030-0004"))
				.andExpect(jsonPath("$.facturas[0].tipo").value("RECTIFICATIVA"))
				.andExpect(jsonPath("$.facturas[1].numeroCompleto").value("F2030-0003"))
				.andExpect(jsonPath("$.facturas[3].numeroCompleto").value("F2030-0001"))
				.andExpect(jsonPath("$.totales.cantidad").value(4))
				.andExpect(jsonPath("$.totales.base").value(74.38)) // 3 x 37.19 - 37.19
				.andExpect(jsonPath("$.totales.cuotaIva").value(15.62))
				.andExpect(jsonPath("$.totales.total").value(90.0))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.limit").value(20));
	}

	@Test
	void filtrarPorFechas_esInclusivoYEnHoraDeMadrid() throws Exception {
		escenarioDelClub();

		// B se emitio a las 23:30Z del 1-ene = 00:30 del 2-ene en Madrid
		facturasDelClub(admin, club, "hasta", "2030-01-01").andExpect(jsonPath("$.facturas.length()").value(1))
				.andExpect(jsonPath("$.facturas[0].numeroCompleto").value("F2030-0001")).andExpect(jsonPath("$.totales.cantidad").value(1));
		facturasDelClub(admin, club, "desde", "2030-01-02", "hasta", "2030-01-02")
				.andExpect(jsonPath("$.facturas.length()").value(1)).andExpect(jsonPath("$.facturas[0].numeroCompleto").value("F2030-0002"));
		facturasDelClub(admin, club, "desde", "2030-01-02").andExpect(jsonPath("$.facturas.length()").value(3))
				.andExpect(jsonPath("$.totales.cantidad").value(3));
		facturasDelClub(admin, club, "hasta", "2030-01-02").andExpect(jsonPath("$.facturas.length()").value(2));
		facturasDelClub(admin, club, "desde", "2030-01-01", "hasta", "2030-01-04")
				.andExpect(jsonPath("$.facturas.length()").value(4)); // el ultimo dia entra entero
		facturasDelClub(admin, club, "desde", "2030-01-05").andExpect(jsonPath("$.facturas.length()").value(0));
	}

	@Test
	void filtrarPorTipo_ylosTotalesSonDelConjuntoFiltrado() throws Exception {
		escenarioDelClub();

		facturasDelClub(admin, club, "tipo", "RECTIFICATIVA").andExpect(jsonPath("$.facturas.length()").value(1))
				.andExpect(jsonPath("$.totales.cantidad").value(1)).andExpect(jsonPath("$.totales.base").value(-37.19))
				.andExpect(jsonPath("$.totales.cuotaIva").value(-7.81)).andExpect(jsonPath("$.totales.total").value(-45.0));
		facturasDelClub(admin, club, "tipo", "ORIGINAL").andExpect(jsonPath("$.facturas.length()").value(3))
				.andExpect(jsonPath("$.totales.cantidad").value(3)).andExpect(jsonPath("$.totales.base").value(111.57))
				.andExpect(jsonPath("$.totales.cuotaIva").value(23.43)).andExpect(jsonPath("$.totales.total").value(135.0));
	}

	@Test
	void laPaginacionNoCambiaLosTotales() throws Exception {
		escenarioDelClub();

		facturasDelClub(admin, club, "page", "0", "limit", "3").andExpect(jsonPath("$.facturas.length()").value(3))
				.andExpect(jsonPath("$.totales.cantidad").value(4)).andExpect(jsonPath("$.totales.total").value(90.0))
				.andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.limit").value(3));
		facturasDelClub(admin, club, "page", "1", "limit", "3").andExpect(jsonPath("$.facturas.length()").value(1))
				.andExpect(jsonPath("$.facturas[0].numeroCompleto").value("F2030-0001"))
				.andExpect(jsonPath("$.totales.cantidad").value(4)).andExpect(jsonPath("$.page").value(1));
		facturasDelClub(admin, club, "page", "5", "limit", "3").andExpect(jsonPath("$.facturas.length()").value(0))
				.andExpect(jsonPath("$.totales.cantidad").value(4));
	}

	@Test
	void elLimiteDelListadoDelClubTieneTopeDe100() throws Exception {
		sembrarFacturas(ana, 105);

		facturasDelClub(admin, club, "limit", "5000").andExpect(jsonPath("$.facturas.length()").value(100))
				.andExpect(jsonPath("$.limit").value(100)).andExpect(jsonPath("$.totales.cantidad").value(105));
	}

	@Test
	void sinFacturasLosTotalesSonCero() throws Exception {
		facturasDelClub(admin, club).andExpect(status().isOk()).andExpect(jsonPath("$.facturas.length()").value(0))
				.andExpect(jsonPath("$.totales.cantidad").value(0)).andExpect(jsonPath("$.totales.base").value(0.0))
				.andExpect(jsonPath("$.totales.cuotaIva").value(0.0)).andExpect(jsonPath("$.totales.total").value(0.0));
	}

	@Test
	void elListadoDelClubNoIncluyeLasFacturasDeOtrosClubes() throws Exception {
		Club otro = crearClub("Club B");
		var pistaB = pistas.saveAndFlush(com.vibora.TestData.pista(otro, "Pista B"));
		tarifaPlanaLosLunes(otro, "20.00");
		Jug adminB = administrador(otro, "admin-b@vibora.com");
		configurarDatosFiscales(adminB, otro);
		facturaEmitida(ana, reservaEnElLunes(ana, 11, 0));
		long deB = idDe(reservar(beto, pistaB, lunes(11, 0)).andExpect(status().isCreated()));
		facturaEmitida(beto, deB);

		facturasDelClub(admin, club).andExpect(jsonPath("$.facturas.length()").value(1)).andExpect(jsonPath("$.totales.total").value(45.0));
		facturasDelClub(adminB, otro).andExpect(jsonPath("$.facturas.length()").value(1)).andExpect(jsonPath("$.totales.total").value(30.0));
	}

	@ParameterizedTest
	@ValueSource(strings = { "desde=no-es-fecha", "hasta=2030-13-45", "desde=2030-01-05&hasta=2030-01-01", "tipo=OTRA", "page=-1",
			"limit=0", "limit=abc" })
	void parametrosDelListadoDelClubImposibles_400(String consulta) throws Exception {
		var peticion = get("/api/clubes/" + club.getId() + "/facturas").header("Authorization", admin.token());
		for (String par : consulta.split("&")) {
			peticion.param(par.split("=")[0], par.split("=")[1]);
		}
		mvc.perform(peticion).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
	}

	@Test
	void fechasFueraDeLosLimitesAbsolutosDelListadoDelClub_400_noUn500() throws Exception {
		String mensaje = "Las fechas deben estar entre 1970 y 2100";
		facturasDelClub(admin, club, "hasta", "+999999999-12-31").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value(mensaje));
		facturasDelClub(admin, club, "desde", "0001-01-01").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value(mensaje));
		facturasDelClub(admin, club, "desde", "+500000-01-01").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value(mensaje));
		facturasDelClub(admin, club, "hasta", "+500000-01-01").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value(mensaje));
		facturasDelClub(admin, club, "desde", "1969-12-31").andExpect(status().isBadRequest());
		facturasDelClub(admin, club, "hasta", "2101-01-01").andExpect(status().isBadRequest());
	}

	@Test
	void fechasEnLosExtremosAbsolutosDelListadoDelClub_200() throws Exception {
		facturasDelClub(admin, club, "desde", "1970-01-01", "hasta", "1970-01-01").andExpect(status().isOk());
		facturasDelClub(admin, club, "desde", "2100-12-31", "hasta", "2100-12-31").andExpect(status().isOk());
		facturasDelClub(admin, club, "desde", "1970-01-01", "hasta", "2100-12-31").andExpect(status().isOk());
	}

	@Test
	void elListadoDelClubSoloLoVeSuAdministrador() throws Exception {
		facturaEmitida(ana, reservaEnElLunes(ana, 11, 0));
		Club otro = crearClub("Club B");
		Jug adminOtro = administrador(otro, "admin-b@vibora.com");
		Jug monitor = personal(club, "monitor@vibora.com", Rol.MONITOR);

		for (Jug intruso : new Jug[] { ana, beto, adminOtro, monitor }) {
			facturasDelClub(intruso, club).andExpect(status().isForbidden());
		}
		mvc.perform(get("/api/clubes/" + club.getId() + "/facturas")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/clubes/999999/facturas").header("Authorization", admin.token())).andExpect(status().isNotFound());
	}

	// ---- una factura ----

	@Test
	void verUnaFactura_elReceptorYElAdminDelClub() throws Exception {
		long id = facturaEmitida(ana, reservaEnElLunes(ana, 11, 0));

		factura(ana, id).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.numeroCompleto").value("F2030-0001")).andExpect(jsonPath("$.total").value(45.0));
		factura(admin, id).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
	}

	@Test
	void verUnaFactura_cualquierOtroRecibe404_igualQueUnaInexistente() throws Exception {
		long id = facturaEmitida(ana, reservaEnElLunes(ana, 11, 0));
		Club otro = crearClub("Club B");
		Jug adminOtro = administrador(otro, "admin-b@vibora.com");
		Jug monitor = personal(club, "monitor@vibora.com", Rol.MONITOR);

		for (Jug intruso : new Jug[] { beto, adminOtro, monitor }) {
			factura(intruso, id).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Factura no encontrada"));
		}
		factura(ana, 999999).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Factura no encontrada"));
		mvc.perform(get("/api/facturas/" + id)).andExpect(status().isUnauthorized());
	}

	@Test
	void ningunListadoNiDetalleDejaVerEmailsDeNadie() throws Exception {
		long reserva = reservaEnElLunes(ana, 11, 0);
		long id = facturaEmitida(ana, reserva);
		cancelar(ana, reserva).andExpect(status().isOk());

		assertThat(cuerpo(factura(ana, id))).doesNotContain("@");
		assertThat(cuerpo(misFacturas(ana))).doesNotContain("@");
		assertThat(cuerpo(facturasDeReserva(ana, reserva))).doesNotContain("@");
		assertThat(cuerpo(facturasDeReserva(admin, reserva))).doesNotContain("@");
		assertThat(cuerpo(facturasDelClub(admin, club))).doesNotContain("@").doesNotContain("email");
	}

	// ---- eficiencia ----

	@Test
	void elListadoDelClubUsaUnNumeroFijoDeConsultas_sinImportarCuantasFacturasHay() throws Exception {
		sembrarFacturas(ana, 2);
		long con2 = consultasDelListadoDelClub();
		sembrarFacturas(beto, 6, 2);
		refrescar();
		long con8 = consultasDelListadoDelClub();

		assertThat(con8).as("con 8 facturas hace las mismas consultas que con 2 (sin N+1)").isEqualTo(con2);
		assertThat(con8).isLessThanOrEqualTo(5); // usuario autenticado, club, el listado y los totales
	}

	private long consultasDelListadoDelClub() throws Exception {
		refrescar();
		Statistics estadisticas = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
		estadisticas.setStatisticsEnabled(true);
		estadisticas.clear();
		facturasDelClub(admin, club).andExpect(status().isOk());
		long consultas = estadisticas.getPrepareStatementCount();
		estadisticas.setStatisticsEnabled(false);
		return consultas;
	}
}
