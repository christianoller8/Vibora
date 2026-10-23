package com.vibora.partido;

import com.vibora.cargo.Cargo;
import com.vibora.cargo.EstadoCargo;
import com.vibora.club.Club;
import com.vibora.notificacion.Notificacion;
import com.vibora.notificacion.NotificacionCreada;
import com.vibora.notificacion.TipoNotificacion;
import com.vibora.pista.Pista;
import com.vibora.RelojTestConfig;
import com.vibora.TestData;
import com.vibora.reserva.EstadoReserva;
import com.vibora.usuario.Rol;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RecordApplicationEvents
class PartidoControllerTests extends PartidosApiTestBase {

	@Autowired ApplicationEvents eventos;

	Jug olga; // organizadora
	Jug beto;
	Jug carla;
	Jug dani;
	Jug eva;

	@BeforeEach
	void preparar() throws Exception {
		tarifaPlana("30.00"); // 90 min = 45.00 => cuota 11.25
		olga = jugador("olga@vibora.com", "Olga", "Ortega");
		beto = jugador("beto@vibora.com", "Beto", "Bravo");
		carla = jugador("carla@vibora.com", "Carla", "Cano");
		dani = jugador("dani@vibora.com", "Dani", "Duran");
		eva = jugador("eva@vibora.com", "Eva", "Estrada");
	}

	// ---- crear reserva: el organizador es el participante 1 ----

	@Test
	void crearReserva_insertaAlOrganizadorComoPuestoUno_yLaRespuestaTraeLosCamposNuevos() throws Exception {
		reservar(olga, pista, lunes(11, 0))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.abierta").value(false))
				.andExpect(jsonPath("$.jugadores").value(1))
				.andExpect(jsonPath("$.cuotaPorJugador").value(11.25))
				.andExpect(jsonPath("$.precioTotal").value(45.0)); // campos previos intactos

		refrescar();
		assertThat(participantes.findAll()).singleElement().satisfies(p -> {
			assertThat(p.getPuesto()).isEqualTo((short) 1);
			assertThat(p.getUsuario().getId()).isEqualTo(olga.id());
		});
	}

	@Test
	void misReservas_incluyenAbiertaJugadoresYCuota() throws Exception {
		long id = partidoAbierto(olga, lunes(11, 0));
		apuntarse(beto, id).andExpect(status().isCreated());

		mvc.perform(get("/api/reservas/mias").header("Authorization", olga.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].abierta").value(true))
				.andExpect(jsonPath("$[0].jugadores").value(2))
				.andExpect(jsonPath("$[0].cuotaPorJugador").value(11.25));
	}

	// ---- abrir / cerrar ----

	@Test
	void organizadorAbreYCierra_200() throws Exception {
		long id = reservaCreada(olga, lunes(11, 0));

		abrir(olga, id, true).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.abierta").value(true))
				.andExpect(jsonPath("$.jugadores").value(1));
		refrescar();
		assertThat(reservas.findById(id)).get().satisfies(r -> assertThat(r.isAbierta()).isTrue());

		abrir(olga, id, false).andExpect(status().isOk()).andExpect(jsonPath("$.abierta").value(false));
		refrescar();
		assertThat(reservas.findById(id)).get().satisfies(r -> assertThat(r.isAbierta()).isFalse());
	}

	@Test
	void cerrarElPartido_conservaALosParticipantes_peroNadieMasSePuedeApuntar() throws Exception {
		long id = partidoAbierto(olga, lunes(11, 0));
		apuntarse(beto, id).andExpect(status().isCreated());

		abrir(olga, id, false).andExpect(status().isOk()).andExpect(jsonPath("$.jugadores").value(2));

		apuntarse(carla, id).andExpect(status().isConflict());
		refrescar();
		assertThat(participantes.findAll()).hasSize(2);
	}

	@Test
	void abrir_otroUsuario_403_inexistente_404_sinToken_401_bodyInvalido_400() throws Exception {
		long id = reservaCreada(olga, lunes(11, 0));

		abrir(beto, id, true).andExpect(status().isForbidden());
		abrir(olga, 999999, true).andExpect(status().isNotFound());
		mvc.perform(put("/api/reservas/" + id + "/abierta").contentType("application/json").content("{\"abierta\":true}"))
				.andExpect(status().isUnauthorized());
		for (String body : new String[] { "{}", "{\"abierta\":null}", "{\"abierta\":\"si\"}", "" }) {
			mvc.perform(put("/api/reservas/" + id + "/abierta").header("Authorization", olga.token())
					.contentType("application/json").content(body)).andExpect(status().isBadRequest());
		}
		refrescar();
		assertThat(reservas.findById(id)).get().satisfies(r -> assertThat(r.isAbierta()).isFalse());
	}

	@Test
	void abrir_reservaCanceladaOYaEmpezada_409() throws Exception {
		long cancelada = reservaCreada(olga, lunes(11, 0));
		cancelar(olga, cancelada).andExpect(status().isOk());
		abrir(olga, cancelada, true).andExpect(status().isConflict());

		long enCurso = reservaCreada(olga, lunes(14, 0));
		reloj.fijar(lunes(14, 0).plusSeconds(1));
		abrir(olga, enCurso, true).andExpect(status().isConflict());
		reloj.fijar(lunes(14, 0)); // el instante exacto de inicio ya cuenta como empezado
		abrir(olga, enCurso, true).andExpect(status().isConflict());
	}

	// ---- listado de partidos ----

	@Test
	void partidos_soloAbiertosConfirmadosFuturosConPlaza_ordenadosPorInicio() throws Exception {
		long tarde = partidoAbierto(olga, lunes(17, 0));
		long manana = partidoAbierto(olga, lunes(9, 30));
		reservaCreada(olga, lunes(12, 30)); // no abierta
		abrir(olga, partidoAbierto(olga, lunes(14, 0)), false).andExpect(status().isOk()); // cerrada
		cancelar(olga, partidoAbierto(olga, lunes(15, 30))).andExpect(status().isOk());
		partidoAbierto(olga, lunes(8, 0)); // pasada cuando avance el reloj
		long completa = partidoAbierto(olga, lunes(20, 0));
		apuntarse(beto, completa).andExpect(status().isCreated());
		apuntarse(carla, completa).andExpect(status().isCreated());
		apuntarse(dani, completa).andExpect(status().isCreated());
		reloj.fijar(lunes(8, 0).plusSeconds(1)); // el de las 08:00 ya ha empezado

		partidos(eva)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].reservaId").value(manana))
				.andExpect(jsonPath("$[1].reservaId").value(tarde));
	}

	@Test
	void partidos_campos_yNoFiltraEmailNiApellidosCompletos() throws Exception {
		long id = partidoAbierto(olga, lunes(11, 0));
		apuntarse(beto, id).andExpect(status().isCreated());

		String cuerpo = partidos(carla)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].reservaId").value(id))
				.andExpect(jsonPath("$[0].club.id").value(club.getId()))
				.andExpect(jsonPath("$[0].club.nombre").value("Club A"))
				.andExpect(jsonPath("$[0].pistaNombre").value("Pista 1"))
				.andExpect(jsonPath("$[0].inicio").value("2030-01-07T10:00:00Z"))
				.andExpect(jsonPath("$[0].fin").value("2030-01-07T11:30:00Z"))
				.andExpect(jsonPath("$[0].cuotaPorJugador").value(11.25))
				.andExpect(jsonPath("$[0].plazasLibres").value(2))
				.andExpect(jsonPath("$[0].jugadores.length()").value(2))
				.andExpect(jsonPath("$[0].jugadores[0].usuarioId").value(olga.id()))
				.andExpect(jsonPath("$[0].jugadores[0].nombre").value("Olga"))
				.andExpect(jsonPath("$[0].jugadores[0].inicialApellido").value("O"))
				.andExpect(jsonPath("$[0].jugadores[1].usuarioId").value(beto.id()))
				.andExpect(jsonPath("$[0].jugadores[1].inicialApellido").value("B"))
				.andExpect(jsonPath("$[0].jugadores[0].email").doesNotExist())
				.andExpect(jsonPath("$[0].jugadores[0].apellidos").doesNotExist())
				.andExpect(jsonPath("$[0].yaApuntado").value(false))
				.andExpect(jsonPath("$[0].esOrganizador").value(false))
				.andReturn().getResponse().getContentAsString();
		assertThat(cuerpo).doesNotContain("@vibora.com").doesNotContain("Ortega").doesNotContain("Bravo");
	}

	@Test
	void partidos_yaApuntadoYEsOrganizadorSegunQuienPregunta() throws Exception {
		long id = partidoAbierto(olga, lunes(11, 0));
		apuntarse(beto, id).andExpect(status().isCreated());

		partidos(olga).andExpect(jsonPath("$[0].esOrganizador").value(true))
				.andExpect(jsonPath("$[0].yaApuntado").value(true));
		partidos(beto).andExpect(jsonPath("$[0].esOrganizador").value(false))
				.andExpect(jsonPath("$[0].yaApuntado").value(true));
		partidos(carla).andExpect(jsonPath("$[0].esOrganizador").value(false))
				.andExpect(jsonPath("$[0].yaApuntado").value(false));
	}

	@Test
	void partidos_filtraPorClubYFecha() throws Exception {
		Club otroClub = crearClub("Club B");
		Pista pistaB = pistas.saveAndFlush(TestData.pista(otroClub, "Pista B"));
		tarifa(otroClub, 8, 23, "30.00");
		long enA = partidoAbierto(olga, lunes(11, 0));
		long enB = idDe(reservar(olga, pistaB, lunes(11, 0)).andExpect(status().isCreated()));
		abrir(olga, enB, true).andExpect(status().isOk());
		// otro dia: el lunes siguiente (la tarifa semanal del lunes tambien lo cubre)
		long otroDia = idDe(reservar(olga, pista, local(LUNES.plusDays(7), 11, 0)).andExpect(status().isCreated()));
		abrir(olga, otroDia, true).andExpect(status().isOk());

		partidos(carla).andExpect(jsonPath("$.length()").value(3));
		mvc.perform(get("/api/partidos").param("clubId", club.getId().toString()).header("Authorization", carla.token()))
				.andExpect(jsonPath("$.length()").value(2));
		mvc.perform(get("/api/partidos").param("clubId", otroClub.getId().toString()).header("Authorization", carla.token()))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].reservaId").value(enB));
		mvc.perform(get("/api/partidos").param("fecha", "2030-01-07").header("Authorization", carla.token()))
				.andExpect(jsonPath("$.length()").value(2));
		mvc.perform(get("/api/partidos").param("fecha", "2030-01-14").header("Authorization", carla.token()))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].reservaId").value(otroDia));
		mvc.perform(get("/api/partidos").param("clubId", club.getId().toString()).param("fecha", "2030-01-07")
				.header("Authorization", carla.token()))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].reservaId").value(enA));
		mvc.perform(get("/api/partidos").param("fecha", "2030-02-01").header("Authorization", carla.token()))
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void partidos_fechaInvalida400_sinToken401() throws Exception {
		mvc.perform(get("/api/partidos").param("fecha", "no-es-fecha").header("Authorization", beto.token()))
				.andExpect(status().isBadRequest());
		mvc.perform(get("/api/partidos").param("clubId", "abc").header("Authorization", beto.token()))
				.andExpect(status().isBadRequest());
		mvc.perform(get("/api/partidos")).andExpect(status().isUnauthorized());
	}

	@Test
	void partidos_noHaceUnaConsultaPorPartido() throws Exception {
		for (int hora : new int[] { 9, 11, 14, 17, 20 }) {
			long id = partidoAbierto(olga, lunes(hora, hora == 9 ? 30 : 0));
			apuntarse(beto, id).andExpect(status().isCreated());
			apuntarse(carla, id).andExpect(status().isCreated());
		}
		refrescar();

		Statistics estadisticas = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
		estadisticas.setStatisticsEnabled(true);
		estadisticas.clear();
		partidos(dani).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(5))
				.andExpect(jsonPath("$[0].jugadores.length()").value(3));
		long consultas = estadisticas.getPrepareStatementCount();
		estadisticas.setStatisticsEnabled(false);

		// 1 (usuario autenticado) + 1 (partidos con pista, club, organizador y jugadores)
		assertThat(consultas).isLessThanOrEqualTo(2);
	}

	// ---- apuntarse ----

	@Test
	void apuntarse_caminoFeliz_201_cargoPendiente_yNotificaciones() throws Exception {
		long id = partidoAbierto(olga, lunes(11, 0));

		apuntarse(beto, id)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.reservaId").value(id))
				.andExpect(jsonPath("$.plazasLibres").value(2))
				.andExpect(jsonPath("$.jugadores.length()").value(2))
				.andExpect(jsonPath("$.yaApuntado").value(true))
				.andExpect(jsonPath("$.esOrganizador").value(false))
				.andExpect(jsonPath("$.cuotaPorJugador").value(11.25));

		refrescar();
		assertThat(participantes.findAll()).hasSize(2)
				.anySatisfy(p -> {
					assertThat(p.getUsuario().getId()).isEqualTo(beto.id());
					assertThat(p.getPuesto()).isEqualTo((short) 2);
				});
		Cargo cargo = cargoDe(id, beto);
		assertThat(cargo.getEstado()).isEqualTo(EstadoCargo.PENDIENTE);
		assertThat(cargo.getImporte()).isEqualByComparingTo("11.25");
		assertThat(cargo.getAcreedor().getId()).isEqualTo(olga.id());

		List<Notificacion> alOrganizador = notifsDe(olga, TipoNotificacion.JUGADOR_SE_APUNTO);
		assertThat(alOrganizador).singleElement().satisfies(n -> {
			assertThat(n.getMensaje()).contains("Beto Bravo");
			assertThat(n.getReserva().getId()).isEqualTo(id);
			assertThat(n.isLeida()).isFalse();
		});
		assertThat(notifsDe(beto, TipoNotificacion.CARGO_NUEVO)).singleElement().satisfies(n -> {
			assertThat(n.getMensaje()).contains("11,25").contains("Olga Ortega");
			assertThat(n.getReserva().getId()).isEqualTo(id);
		});
		assertThat(notifsDe(olga, TipoNotificacion.PARTIDO_COMPLETO)).isEmpty();
		assertThat(notifsDe(beto, TipoNotificacion.JUGADOR_SE_APUNTO)).isEmpty();
		assertThat(notifsDe(olga, TipoNotificacion.CARGO_NUEVO)).isEmpty();
	}

	@Test
	void apuntarse_asignaElPuestoLibreMasBajo() throws Exception {
		long id = partidoAbierto(olga, lunes(11, 0));
		apuntarse(beto, id).andExpect(status().isCreated());  // 2
		apuntarse(carla, id).andExpect(status().isCreated()); // 3
		salir(beto, id).andExpect(status().isOk());           // libera el 2

		apuntarse(dani, id).andExpect(status().isCreated());

		refrescar();
		assertThat(participantes.findAll()).extracting(p -> p.getUsuario().getId() + ":" + p.getPuesto())
				.containsExactlyInAnyOrder(olga.id() + ":1", dani.id() + ":2", carla.id() + ":3");
	}

	@Test
	void apuntarse_elCuartoJugadorCompletaElPartido_yElQuintoRecibe409() throws Exception {
		long id = partidoAbierto(olga, lunes(11, 0));
		apuntarse(beto, id).andExpect(status().isCreated());
		apuntarse(carla, id).andExpect(status().isCreated());
		assertThat(notifsDe(olga, TipoNotificacion.PARTIDO_COMPLETO)).isEmpty();

		apuntarse(dani, id).andExpect(status().isCreated()).andExpect(jsonPath("$.plazasLibres").value(0));

		assertThat(notifsDe(olga, TipoNotificacion.PARTIDO_COMPLETO)).singleElement().satisfies(n -> {
			assertThat(n.getReserva().getId()).isEqualTo(id);
			assertThat(n.getMensaje()).isNotBlank();
		});
		assertThat(notifsDe(olga, TipoNotificacion.JUGADOR_SE_APUNTO)).hasSize(3);

		apuntarse(eva, id).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("El partido está completo"));
		assertThat(notifsDe(olga, TipoNotificacion.PARTIDO_COMPLETO)).hasSize(1);
		assertThat(cargosDe(id)).hasSize(3);
		refrescar();
		assertThat(participantes.findAll()).hasSize(4);
	}

	@Test
	void apuntarse_conflictos_noAbiertoCanceladoEmpezadoYaApuntado() throws Exception {
		long cerrado = reservaCreada(olga, lunes(11, 0));
		apuntarse(beto, cerrado).andExpect(status().isConflict());

		long cancelado = partidoAbierto(olga, lunes(14, 0));
		cancelar(olga, cancelado).andExpect(status().isOk());
		apuntarse(beto, cancelado).andExpect(status().isConflict());

		long empezado = partidoAbierto(olga, lunes(17, 0));
		reloj.fijar(lunes(17, 0));
		apuntarse(beto, empezado).andExpect(status().isConflict());
		reloj.fijar(RelojTestConfig.AHORA_INICIAL);

		long abierto = partidoAbierto(olga, lunes(20, 0));
		apuntarse(beto, abierto).andExpect(status().isCreated());
		apuntarse(beto, abierto).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Ya estás apuntado a este partido"));
		apuntarse(olga, abierto).andExpect(status().isConflict()); // el organizador ya juega

		assertThat(cargosDe(cerrado)).isEmpty();
		assertThat(cargosDe(cancelado)).isEmpty();
		assertThat(cargosDe(empezado)).isEmpty();
		assertThat(cargosDe(abierto)).hasSize(1);
	}

	@Test
	void apuntarse_inexistente404_sinToken401() throws Exception {
		apuntarse(beto, 999999).andExpect(status().isNotFound());
		mvc.perform(post("/api/reservas/1/apuntarse")).andExpect(status().isUnauthorized());
	}

	@Test
	void apuntarse_cuotaRedondeadaHaciaAbajo_10con01() throws Exception {
		Club otroClub = crearClub("Club C");
		Pista otra = pistas.saveAndFlush(TestData.pista(otroClub, "Pista C"));
		tarifa(otroClub, 8, 23, "6.67"); // 1.5 h => 10.005 -> 10.01 (HALF_UP)
		long id = idDe(reservar(olga, otra, lunes(11, 0)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.precioTotal").value(10.01)).andExpect(jsonPath("$.cuotaPorJugador").value(2.5)));
		abrir(olga, id, true).andExpect(status().isOk());

		apuntarse(beto, id).andExpect(status().isCreated()).andExpect(jsonPath("$.cuotaPorJugador").value(2.5));

		assertThat(cargoDe(id, beto).getImporte()).isEqualByComparingTo("2.50");
	}

	@Test
	void apuntarse_conCuotaCero_entraSinCargoNiNotificacionDeCargo() throws Exception {
		Club otroClub = crearClub("Club Gratis");
		Pista otra = pistas.saveAndFlush(TestData.pista(otroClub, "Pista G"));
		tarifa(otroClub, 8, 23, "0.00");
		long id = idDe(reservar(olga, otra, lunes(11, 0)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.cuotaPorJugador").value(0.0)));
		abrir(olga, id, true).andExpect(status().isOk());

		apuntarse(beto, id).andExpect(status().isCreated()).andExpect(jsonPath("$.yaApuntado").value(true));

		assertThat(cargosDe(id)).isEmpty();
		assertThat(notifsDe(beto, TipoNotificacion.CARGO_NUEVO)).isEmpty();
		assertThat(notifsDe(olga, TipoNotificacion.JUGADOR_SE_APUNTO)).hasSize(1);
		salir(beto, id).andExpect(status().isOk()); // sin cargo se puede salir
	}

	@Test
	void apuntarse_publicaUnEventoPorNotificacion() throws Exception {
		long id = partidoAbierto(olga, lunes(11, 0));
		apuntarse(beto, id).andExpect(status().isCreated());
		apuntarse(carla, id).andExpect(status().isCreated());
		apuntarse(dani, id).andExpect(status().isCreated()); // completa: tambien PARTIDO_COMPLETO

		refrescar();
		List<Notificacion> guardadas = notificaciones.findAll();
		List<NotificacionCreada> publicados = eventos.stream(NotificacionCreada.class).toList();

		assertThat(guardadas).hasSize(3 + 3 + 1); // SE_APUNTO x3, CARGO_NUEVO x3, COMPLETO x1
		assertThat(publicados).hasSameSizeAs(guardadas);
		assertThat(publicados).extracting(NotificacionCreada::id).doesNotHaveDuplicates()
				.containsExactlyInAnyOrderElementsOf(guardadas.stream().map(Notificacion::getId).toList());
		for (NotificacionCreada e : publicados) {
			Notificacion n = guardadas.stream().filter(g -> g.getId().equals(e.id())).findFirst().orElseThrow();
			assertThat(e.usuarioId()).isEqualTo(n.getUsuario().getId());
			assertThat(e.tipo()).isEqualTo(n.getTipo());
			assertThat(e.mensaje()).isEqualTo(n.getMensaje());
			assertThat(e.reservaId()).isEqualTo(id);
			assertThat(e.creadaEn()).isEqualTo(n.getCreadaEn()).isNotNull();
		}
	}

	// ---- salir ----

	@Test
	void salir_conCargoPendiente_anulaElCargo_liberaElPuesto_yAvisaAlOrganizador() throws Exception {
		long id = partidoAbierto(olga, lunes(11, 0));
		apuntarse(beto, id).andExpect(status().isCreated());

		salir(beto, id).andExpect(status().isOk())
				.andExpect(jsonPath("$.reservaId").value(id))
				.andExpect(jsonPath("$.yaApuntado").value(false))
				.andExpect(jsonPath("$.plazasLibres").value(3));

		assertThat(cargoDe(id, beto).getEstado()).isEqualTo(EstadoCargo.ANULADO);
		refrescar();
		assertThat(participantes.findAll()).singleElement()
				.satisfies(p -> assertThat(p.getUsuario().getId()).isEqualTo(olga.id()));
		assertThat(notifsDe(olga, TipoNotificacion.JUGADOR_SALIO)).singleElement()
				.satisfies(n -> assertThat(n.getMensaje()).contains("Beto Bravo"));
	}

	@Test
	void salirYVolverAApuntarse_creaUnCargoNuevo_yElAnuladoSeConserva() throws Exception {
		long id = partidoAbierto(olga, lunes(11, 0));
		apuntarse(beto, id).andExpect(status().isCreated());
		salir(beto, id).andExpect(status().isOk());

		apuntarse(beto, id).andExpect(status().isCreated());

		List<Cargo> deBeto = cargosDe(id).stream().filter(c -> c.getDeudor().getId().equals(beto.id())).toList();
		assertThat(deBeto).extracting(Cargo::getEstado).containsExactly(EstadoCargo.ANULADO, EstadoCargo.PENDIENTE);
	}

	@Test
	void salir_conCargoPagado_409_yNadaCambia() throws Exception {
		long id = partidoAbierto(olga, lunes(11, 0));
		apuntarse(beto, id).andExpect(status().isCreated());
		pagar(beto, cargoDe(id, beto).getId()).andExpect(status().isOk());

		salir(beto, id).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Ya has pagado tu parte"));

		assertThat(cargoDe(id, beto).getEstado()).isEqualTo(EstadoCargo.PAGADO);
		refrescar();
		assertThat(participantes.findAll()).hasSize(2);
		assertThat(notifsDe(olga, TipoNotificacion.JUGADOR_SALIO)).isEmpty();
	}

	@Test
	void salir_organizador409_noParticipante409_empezado409_inexistente404() throws Exception {
		long id = partidoAbierto(olga, lunes(11, 0));
		apuntarse(beto, id).andExpect(status().isCreated());

		salir(olga, id).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("El organizador debe cancelar la reserva"));
		salir(carla, id).andExpect(status().isConflict());
		salir(beto, 999999).andExpect(status().isNotFound());
		mvc.perform(post("/api/reservas/" + id + "/salir")).andExpect(status().isUnauthorized());

		reloj.fijar(lunes(11, 0));
		salir(beto, id).andExpect(status().isConflict());
		assertThat(cargoDe(id, beto).getEstado()).isEqualTo(EstadoCargo.PENDIENTE);
	}

	// ---- cancelar reserva compartida ----

	@Test
	void cancelar_anulaPendientes_reembolsaPagados_yAvisaALosDemasJugadores() throws Exception {
		long id = partidoAbierto(olga, lunes(11, 0));
		apuntarse(beto, id).andExpect(status().isCreated());
		apuntarse(carla, id).andExpect(status().isCreated());
		pagar(beto, cargoDe(id, beto).getId()).andExpect(status().isOk());

		cancelar(olga, id).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADA"));

		assertThat(cargoDe(id, beto).getEstado()).isEqualTo(EstadoCargo.REEMBOLSADO);
		assertThat(cargoDe(id, carla).getEstado()).isEqualTo(EstadoCargo.ANULADO);
		assertThat(pagos.count()).isEqualTo(1); // el pago simulado queda como traza del reembolso
		assertThat(notifsDe(beto, TipoNotificacion.RESERVA_CANCELADA)).singleElement()
				.satisfies(n -> assertThat(n.getReserva().getId()).isEqualTo(id));
		assertThat(notifsDe(carla, TipoNotificacion.RESERVA_CANCELADA)).hasSize(1);
		assertThat(notifsDe(olga, TipoNotificacion.RESERVA_CANCELADA)).isEmpty();
		assertThat(notifsDe(dani, TipoNotificacion.RESERVA_CANCELADA)).isEmpty();

		// idempotente: cancelar otra vez no duplica avisos
		cancelar(olga, id).andExpect(status().isOk());
		assertThat(notifsDe(beto, TipoNotificacion.RESERVA_CANCELADA)).hasSize(1);
	}

	@Test
	void cancelar_adminDelClub_avisaAlOrganizadorYAlResto_menosASiMismoSiJuega() throws Exception {
		Jug admin = new Jug(crearUsuario("admin@vibora.com", Rol.ADMIN_CLUB, club), bearer("admin@vibora.com"));
		long id = partidoAbierto(olga, lunes(11, 0));
		apuntarse(beto, id).andExpect(status().isCreated());
		apuntarse(admin, id).andExpect(status().isCreated());
		reloj.fijar(lunes(11, 0).minus(Duration.ofHours(1))); // fuera del plazo de un jugador, pero el admin puede

		cancelar(admin, id).andExpect(status().isOk());

		// el organizador tambien se entera cuando quien cancela es el club
		assertThat(notifsDe(olga, TipoNotificacion.RESERVA_CANCELADA)).singleElement().satisfies(n -> {
			assertThat(n.getMensaje()).startsWith("El club ha cancelado");
			assertThat(n.getReserva().getId()).isEqualTo(id);
		});
		assertThat(notifsDe(beto, TipoNotificacion.RESERVA_CANCELADA)).singleElement()
				.satisfies(n -> assertThat(n.getMensaje()).startsWith("El club ha cancelado"));
		assertThat(notifsDe(admin, TipoNotificacion.RESERVA_CANCELADA)).isEmpty(); // quien cancela no se avisa a si mismo
		assertThat(cargoDe(id, beto).getEstado()).isEqualTo(EstadoCargo.ANULADO);
		assertThat(cargoDe(id, admin).getEstado()).isEqualTo(EstadoCargo.ANULADO);
	}

	@Test
	void cancelar_adminDelClub_reservaSinMasJugadores_avisaAlOrganizadorUnaVez() throws Exception {
		Jug admin = new Jug(crearUsuario("admin@vibora.com", Rol.ADMIN_CLUB, club), bearer("admin@vibora.com"));
		long id = reservaCreada(olga, lunes(11, 0));

		cancelar(admin, id).andExpect(status().isOk());
		cancelar(admin, id).andExpect(status().isOk()); // idempotente: sin avisos nuevos

		assertThat(notifsDe(olga, TipoNotificacion.RESERVA_CANCELADA)).hasSize(1);
		assertThat(notifsDe(admin, TipoNotificacion.RESERVA_CANCELADA)).isEmpty();
	}

	@Test
	void cancelar_fueraDePlazo409_noToCaCargosNiNotificaciones() throws Exception {
		long id = partidoAbierto(olga, lunes(11, 0));
		apuntarse(beto, id).andExpect(status().isCreated());
		reloj.fijar(lunes(11, 0).minus(Duration.ofHours(2)));

		cancelar(olga, id).andExpect(status().isConflict());

		assertThat(cargoDe(id, beto).getEstado()).isEqualTo(EstadoCargo.PENDIENTE);
		assertThat(notifsDe(beto, TipoNotificacion.RESERVA_CANCELADA)).isEmpty();
		refrescar();
		assertThat(reservas.findById(id)).get().satisfies(r -> assertThat(r.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA));
	}
}
