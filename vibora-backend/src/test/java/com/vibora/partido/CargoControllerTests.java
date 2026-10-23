package com.vibora.partido;

import com.vibora.cargo.Cargo;
import com.vibora.cargo.EstadoCargo;
import com.vibora.cargo.Pago;
import com.vibora.notificacion.TipoNotificacion;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tarifas: 08-18 a 30 EUR/h y 18-23 a 20 EUR/h. Un partido a las 11:00 cuesta 45.00 (cuota 11.25);
 * a las 20:00 cuesta 30.00 (cuota 7.50).
 */
class CargoControllerTests extends PartidosApiTestBase {

	Jug olga;
	Jug beto;
	Jug carla;

	@BeforeEach
	void preparar() throws Exception {
		tarifa(club, 8, 18, "30.00");
		tarifa(club, 18, 23, "20.00");
		olga = jugador("olga@vibora.com", "Olga", "Ortega");
		beto = jugador("beto@vibora.com", "Beto", "Bravo");
		carla = jugador("carla@vibora.com", "Carla", "Cano");
	}

	// ---- listados ----

	@Test
	void mios_listaMisDeudas_masNuevaPrimera_sinEmails() throws Exception {
		long m1 = partidoAbierto(olga, lunes(11, 0));
		long m2 = partidoAbierto(olga, lunes(14, 0));
		apuntarse(beto, m1).andExpect(status().isCreated());
		apuntarse(beto, m2).andExpect(status().isCreated());
		apuntarse(carla, m1).andExpect(status().isCreated());
		Cargo c1 = cargoDe(m1, beto);
		Cargo c2 = cargoDe(m2, beto);

		String cuerpo = mvc.perform(get("/api/cargos/mios").header("Authorization", beto.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].id").value(c2.getId()))
				.andExpect(jsonPath("$[1].id").value(c1.getId()))
				.andExpect(jsonPath("$[0].reservaId").value(m2))
				.andExpect(jsonPath("$[0].clubNombre").value("Club A"))
				.andExpect(jsonPath("$[0].pistaNombre").value("Pista 1"))
				.andExpect(jsonPath("$[0].inicio").value("2030-01-07T13:00:00Z"))
				.andExpect(jsonPath("$[0].importe").value(11.25))
				.andExpect(jsonPath("$[0].estado").value("PENDIENTE"))
				.andExpect(jsonPath("$[0].deudor.id").value(beto.id()))
				.andExpect(jsonPath("$[0].deudor.nombre").value("Beto"))
				.andExpect(jsonPath("$[0].deudor.apellidos").value("Bravo"))
				.andExpect(jsonPath("$[0].acreedor.id").value(olga.id()))
				.andExpect(jsonPath("$[0].acreedor.apellidos").value("Ortega"))
				.andExpect(jsonPath("$[0].pagadoEn").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		assertThat(cuerpo).doesNotContain("@vibora.com").doesNotContain("passwordHash");

		mvc.perform(get("/api/cargos/mios").header("Authorization", olga.token()))
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void porCobrar_listaLoQueMeDeben() throws Exception {
		long m1 = partidoAbierto(olga, lunes(11, 0));
		apuntarse(beto, m1).andExpect(status().isCreated());
		apuntarse(carla, m1).andExpect(status().isCreated());

		mvc.perform(get("/api/cargos/por-cobrar").header("Authorization", olga.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].deudor.id").value(carla.id())) // la mas nueva primero
				.andExpect(jsonPath("$[1].deudor.id").value(beto.id()))
				.andExpect(jsonPath("$[0].acreedor.id").value(olga.id()));
		mvc.perform(get("/api/cargos/por-cobrar").header("Authorization", beto.token()))
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void filtroPorEstado_yPagadoEn() throws Exception {
		long m1 = partidoAbierto(olga, lunes(11, 0));
		long m2 = partidoAbierto(olga, lunes(14, 0));
		apuntarse(beto, m1).andExpect(status().isCreated());
		apuntarse(beto, m2).andExpect(status().isCreated());
		Cargo c1 = cargoDe(m1, beto);
		reloj.fijar(lunes(9, 0));
		pagar(beto, c1.getId()).andExpect(status().isOk());

		mvc.perform(get("/api/cargos/mios").param("estado", "PENDIENTE").header("Authorization", beto.token()))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].reservaId").value(m2));
		mvc.perform(get("/api/cargos/mios").param("estado", "PAGADO").header("Authorization", beto.token()))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(c1.getId()))
				.andExpect(jsonPath("$[0].pagadoEn").value("2030-01-07T08:00:00Z"));
		mvc.perform(get("/api/cargos/por-cobrar").param("estado", "PAGADO").header("Authorization", olga.token()))
				.andExpect(jsonPath("$.length()").value(1));
		mvc.perform(get("/api/cargos/mios").param("estado", "ANULADO").header("Authorization", beto.token()))
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void listados_estadoInvalido400_sinToken401() throws Exception {
		mvc.perform(get("/api/cargos/mios").param("estado", "NOPE").header("Authorization", beto.token()))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").isNotEmpty());
		mvc.perform(get("/api/cargos/por-cobrar").param("estado", "NOPE").header("Authorization", beto.token()))
				.andExpect(status().isBadRequest());
		mvc.perform(get("/api/cargos/mios")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/cargos/por-cobrar")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/saldos")).andExpect(status().isUnauthorized());
	}

	// ---- pagar ----

	@Test
	void pagar_deudor_200_creaUnPagoSimulado_yAvisaAlAcreedor() throws Exception {
		long m1 = partidoAbierto(olga, lunes(11, 0));
		apuntarse(beto, m1).andExpect(status().isCreated());
		Cargo cargo = cargoDe(m1, beto);
		reloj.fijar(lunes(9, 0));

		pagar(beto, cargo.getId())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(cargo.getId()))
				.andExpect(jsonPath("$.estado").value("PAGADO"))
				.andExpect(jsonPath("$.pagadoEn").value("2030-01-07T08:00:00Z"));

		refrescar();
		assertThat(cargos.findById(cargo.getId())).get()
				.satisfies(c -> assertThat(c.getEstado()).isEqualTo(EstadoCargo.PAGADO));
		assertThat(pagos.findAll()).singleElement().satisfies((Pago p) -> {
			assertThat(p.getCargo().getId()).isEqualTo(cargo.getId());
			assertThat(p.getImporte()).isEqualByComparingTo("11.25");
			assertThat(p.getReferencia()).matches("SIM-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
		});
		assertThat(notifsDe(olga, TipoNotificacion.PAGO_RECIBIDO)).singleElement().satisfies(n -> {
			assertThat(n.getMensaje()).contains("Beto Bravo").contains("11,25");
			assertThat(n.getReserva().getId()).isEqualTo(m1);
		});
		assertThat(notifsDe(beto, TipoNotificacion.PAGO_RECIBIDO)).isEmpty();
	}

	@Test
	void pagar_dosVeces_409_yUnSoloPago() throws Exception {
		long m1 = partidoAbierto(olga, lunes(11, 0));
		apuntarse(beto, m1).andExpect(status().isCreated());
		long id = cargoDe(m1, beto).getId();

		pagar(beto, id).andExpect(status().isOk());
		pagar(beto, id).andExpect(status().isConflict()).andExpect(jsonPath("$.error").isNotEmpty());

		refrescar();
		assertThat(pagos.count()).isEqualTo(1);
		assertThat(notifsDe(olga, TipoNotificacion.PAGO_RECIBIDO)).hasSize(1);
	}

	@Test
	void pagar_cargoAnuladoOReembolsado_409() throws Exception {
		long m1 = partidoAbierto(olga, lunes(11, 0));
		long m2 = partidoAbierto(olga, lunes(14, 0));
		apuntarse(beto, m1).andExpect(status().isCreated());
		apuntarse(beto, m2).andExpect(status().isCreated());
		long anulado = cargoDe(m1, beto).getId();
		long reembolsado = cargoDe(m2, beto).getId();
		salir(beto, m1).andExpect(status().isOk());
		pagar(beto, reembolsado).andExpect(status().isOk());
		cancelar(olga, m2).andExpect(status().isOk());
		assertThat(cargoDe(m2, beto).getEstado()).isEqualTo(EstadoCargo.REEMBOLSADO);

		pagar(beto, anulado).andExpect(status().isConflict());
		pagar(beto, reembolsado).andExpect(status().isConflict());
		refrescar();
		assertThat(pagos.count()).isEqualTo(1); // el del cargo que se pago antes de cancelar
	}

	@Test
	void pagar_soloElDeudor_acreedor403_ajeno404_inexistente404_sinToken401() throws Exception {
		long m1 = partidoAbierto(olga, lunes(11, 0));
		apuntarse(beto, m1).andExpect(status().isCreated());
		long id = cargoDe(m1, beto).getId();

		pagar(olga, id).andExpect(status().isForbidden()); // parte implicada, pero no es el deudor
		pagar(carla, id).andExpect(status().isNotFound()); // ajeno: no se revela que existe
		pagar(beto, 999999).andExpect(status().isNotFound());
		mvc.perform(post("/api/cargos/" + id + "/pagar")).andExpect(status().isUnauthorized());

		assertThat(cargoDe(m1, beto).getEstado()).isEqualTo(EstadoCargo.PENDIENTE);
		refrescar();
		assertThat(pagos.count()).isZero();
	}

	// ---- saldos ----

	@Test
	void saldos_vacios() throws Exception {
		mvc.perform(get("/api/saldos").header("Authorization", carla.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.saldos.length()").value(0))
				.andExpect(jsonPath("$.totalDebo").value(0.0))
				.andExpect(jsonPath("$.totalMeDeben").value(0.0))
				.andExpect(jsonPath("$.totalNeto").value(0.0));
	}

	/**
	 * Ana (A), Beto (B), Carla (C).
	 * m1 (organiza B, 11:00, cuota 11.25): A y C se apuntan. m2 (organiza A, 20:00, cuota 7.50): B y C se apuntan.
	 * m3 (organiza C, 14:00, cuota 11.25): A se apunta. m4 (organiza A, 09:30, cuota 11.25): B (paga) y C se apuntan
	 * y A cancela. Ademas: A paga m1; C sale de m2.
	 * Vivos (PENDIENTE): A->C 11.25 (m3), C->B 11.25 (m1), B->A 7.50 (m2). Fuera: A->B pagado, C->A anulado (salio),
	 * m4 reembolsado/anulado.
	 */
	private Jug[] montarEscenarioDeSaldos() throws Exception {
		Jug a = jugador("Ana");
		Jug b = beto;
		Jug c = carla;
		long m1 = partidoAbierto(b, lunes(11, 0));
		long m2 = partidoAbierto(a, lunes(20, 0));
		long m3 = partidoAbierto(c, lunes(14, 0));
		long m4 = partidoAbierto(a, lunes(9, 30));
		apuntarse(a, m1).andExpect(status().isCreated());
		apuntarse(c, m1).andExpect(status().isCreated());
		apuntarse(b, m2).andExpect(status().isCreated());
		apuntarse(c, m2).andExpect(status().isCreated());
		apuntarse(a, m3).andExpect(status().isCreated());
		apuntarse(b, m4).andExpect(status().isCreated());
		apuntarse(c, m4).andExpect(status().isCreated());
		pagar(a, cargoDe(m1, a).getId()).andExpect(status().isOk());
		pagar(b, cargoDe(m4, b).getId()).andExpect(status().isOk());
		salir(c, m2).andExpect(status().isOk());
		cancelar(a, m4).andExpect(status().isOk());
		assertThat(cargoDe(m4, b).getEstado()).isEqualTo(EstadoCargo.REEMBOLSADO);
		assertThat(cargoDe(m4, c).getEstado()).isEqualTo(EstadoCargo.ANULADO);
		return new Jug[] { a, b, c };
	}

	@Test
	void saldos_soloCuentanLosCargosPendientes_porContraparte() throws Exception {
		Jug[] j = montarEscenarioDeSaldos();

		mvc.perform(get("/api/saldos").header("Authorization", j[0].token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.saldos.length()").value(2))
				.andExpect(jsonPath("$.saldos[0].usuarioId").value(j[1].id())) // Beto
				.andExpect(jsonPath("$.saldos[0].nombre").value("Beto"))
				.andExpect(jsonPath("$.saldos[0].apellidos").value("Bravo"))
				.andExpect(jsonPath("$.saldos[0].debo").value(0.0)) // lo de m1 ya esta pagado
				.andExpect(jsonPath("$.saldos[0].meDeben").value(7.5))
				.andExpect(jsonPath("$.saldos[0].neto").value(7.5))
				.andExpect(jsonPath("$.saldos[1].usuarioId").value(j[2].id())) // Carla
				.andExpect(jsonPath("$.saldos[1].debo").value(11.25))
				.andExpect(jsonPath("$.saldos[1].meDeben").value(0.0)) // salio de m2 y se anulo m4
				.andExpect(jsonPath("$.saldos[1].neto").value(-11.25))
				.andExpect(jsonPath("$.totalDebo").value(11.25))
				.andExpect(jsonPath("$.totalMeDeben").value(7.5))
				.andExpect(jsonPath("$.totalNeto").value(-3.75));
	}

	@Test
	void saldos_vistoPorOtroUsuario() throws Exception {
		Jug[] j = montarEscenarioDeSaldos();

		mvc.perform(get("/api/saldos").header("Authorization", j[1].token())) // Beto
				.andExpect(jsonPath("$.saldos.length()").value(2))
				.andExpect(jsonPath("$.saldos[0].usuarioId").value(j[0].id())) // Ana
				.andExpect(jsonPath("$.saldos[0].debo").value(7.5))
				.andExpect(jsonPath("$.saldos[0].meDeben").value(0.0))
				.andExpect(jsonPath("$.saldos[0].neto").value(-7.5))
				.andExpect(jsonPath("$.saldos[1].usuarioId").value(j[2].id())) // Carla
				.andExpect(jsonPath("$.saldos[1].debo").value(0.0))
				.andExpect(jsonPath("$.saldos[1].meDeben").value(11.25))
				.andExpect(jsonPath("$.saldos[1].neto").value(11.25))
				.andExpect(jsonPath("$.totalDebo").value(7.5))
				.andExpect(jsonPath("$.totalMeDeben").value(11.25))
				.andExpect(jsonPath("$.totalNeto").value(3.75));
	}

	@Test
	void saldos_seCalculanConUnaSolaConsultaAgregada() throws Exception {
		montarEscenarioDeSaldos();
		refrescar();

		Statistics estadisticas = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
		estadisticas.setStatisticsEnabled(true);
		estadisticas.clear();
		mvc.perform(get("/api/saldos").header("Authorization", beto.token()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.saldos.length()").value(2));
		long consultas = estadisticas.getPrepareStatementCount();
		estadisticas.setStatisticsEnabled(false);

		assertThat(consultas).isLessThanOrEqualTo(2); // usuario autenticado + la agregacion
	}
}
