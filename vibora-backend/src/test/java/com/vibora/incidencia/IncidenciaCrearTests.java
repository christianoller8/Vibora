package com.vibora.incidencia;

import com.vibora.notificacion.TipoNotificacion;
import com.vibora.reserva.EstadoReserva;
import com.vibora.reserva.Reserva;
import com.vibora.TestData;
import com.vibora.usuario.Rol;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /api/clubes/{id}/incidencias: quien puede reportar, limite anti-spam y validaciones. */
class IncidenciaCrearTests extends IncidenciaApiTestBase {

	@Test
	void reportar_quienHaReservadoEnElClub_201_abierta_prioridadMedia_conEventoCreada() throws Exception {
		reportar(jugador, club, "  Red rota  ", "RED", pista.getId())
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.clubId").value(club.getId()))
				.andExpect(jsonPath("$.pistaId").value(pista.getId()))
				.andExpect(jsonPath("$.pistaNombre").value("Pista 1"))
				.andExpect(jsonPath("$.titulo").value("Red rota")) // recortado
				.andExpect(jsonPath("$.descripcion").value(org.hamcrest.Matchers.startsWith("Descripcion de")))
				.andExpect(jsonPath("$.categoria").value("RED"))
				.andExpect(jsonPath("$.prioridad").value("MEDIA"))
				.andExpect(jsonPath("$.estado").value("ABIERTA"))
				.andExpect(jsonPath("$.reportadaPor.id").value(jugador.id()))
				.andExpect(jsonPath("$.reportadaPor.nombre").value("Juan Jugador"))
				.andExpect(jsonPath("$.asignadaA").doesNotExist())
				.andExpect(jsonPath("$.resueltaEn").doesNotExist())
				.andExpect(jsonPath("$.eventos.length()").value(1))
				.andExpect(jsonPath("$.eventos[0].tipo").value("CREADA"))
				.andExpect(jsonPath("$.eventos[0].estadoNuevo").value("ABIERTA"))
				.andExpect(jsonPath("$.eventos[0].usuario.id").value(jugador.id()));

		assertThat(incidencias.count()).isEqualTo(1);
		Incidencia i = incidencias.findAll().get(0);
		assertThat(i.getVersion()).isZero();
		assertThat(i.getCreadaEn()).isEqualTo(reloj.instant());
	}

	@Test
	void reportar_sinPista_201_conPistaNula() throws Exception {
		reportar(jugador, club, "Goteras", "OTRA", null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.pistaId").doesNotExist())
				.andExpect(jsonPath("$.pistaNombre").doesNotExist());
	}

	@Test
	void reportar_quienSoloParticipoEnUnPartidoDelClub_201() throws Exception {
		Persona invitado = persona("invitado@vibora.com", "Ines", "Invitada", Rol.JUGADOR, null);
		Reserva deJugador = reservas.findAll().get(0);
		participantes.saveAndFlush(TestData.participante(deJugador, invitado.usuario(), 2));

		reportar(invitado, club, "Cristal roto", "CRISTAL", pista.getId()).andExpect(status().isCreated());
	}

	@Test
	void reportar_tambienCuentaUnaReservaCancelada_esUnVinculoPasado() throws Exception {
		Persona cancelador = persona("cancelador@vibora.com", "Carlos", "Cancela", Rol.JUGADOR, null);
		Reserva r = reservaDe(cancelador, pista);
		r.setEstado(EstadoReserva.CANCELADA);
		reservas.saveAndFlush(r);

		reportar(cancelador, club, "Suelo resbaladizo", "SUELO", null).andExpect(status().isCreated());
	}

	@Test
	void reportar_quienSoloHaJugadoEnOtroClub_403() throws Exception {
		Persona deB = persona("deb@vibora.com", "Dani", "DeB", Rol.JUGADOR, null);
		reservaDe(deB, pistaB);

		reportar(deB, club, "Red rota", "RED", null).andExpect(status().isForbidden());
		reportar(deB, clubB, "Red rota", "RED", null).andExpect(status().isCreated());
		assertThat(incidencias.count()).isEqualTo(1);
	}

	@Test
	void reportar_quienNoTieneNingunaRelacionConElClub_403() throws Exception {
		reportar(otro, club, "Red rota", "RED", null).andExpect(status().isForbidden());
		assertThat(incidencias.count()).isZero();
		assertThat(totalNotifs()).isZero();
	}

	@Test
	void reportar_elPersonalYElAdministradorDelClub_201_sinNecesitarHaberJugado() throws Exception {
		for (Persona p : List.of(monitor, mant1, admin)) {
			reportar(p, club, "Aviso de " + p.usuario().getNombre(), "OTRA", null).andExpect(status().isCreated());
		}
		assertThat(incidencias.count()).isEqualTo(3);
	}

	@Test
	void reportar_elPersonalDeOtroClub_403() throws Exception {
		reportar(adminB, club, "Red rota", "RED", null).andExpect(status().isForbidden());
		reportar(mantB, club, "Red rota", "RED", null).andExpect(status().isForbidden());
	}

	@Test
	void reportar_avisaATodosLosAdministradoresDelClub_yATodosMenosAlQueReporta() throws Exception {
		Persona admin2 = persona("admin2@vibora.com", "Aitor", "Admin2", Rol.ADMIN_CLUB, club);

		long id = incidenciaDe(jugador, "Red rota");

		for (Persona destinatario : List.of(admin, admin2)) {
			assertThat(notifsDe(destinatario, TipoNotificacion.INCIDENCIA_NUEVA)).singleElement().satisfies(n -> {
				assertThat(n.getMensaje()).contains("Red rota").contains("Club A").contains("Pista 1");
				assertThat(n.isLeida()).isFalse();
			});
		}
		assertThat(totalNotifs()).isEqualTo(2); // ni el reportero, ni el personal, ni el administrador de otro club
		assertThat(id).isPositive();

		// si quien reporta es un administrador, no se avisa a si mismo pero si al otro
		reportar(admin2, club, "Otra", "OTRA", null).andExpect(status().isCreated());
		assertThat(notifsDe(admin2, TipoNotificacion.INCIDENCIA_NUEVA)).hasSize(1);
		assertThat(notifsDe(admin, TipoNotificacion.INCIDENCIA_NUEVA)).hasSize(2);
	}

	@Test
	void reportar_elReporteroNoPuedeFijarLaPrioridad_siempreEsMedia() throws Exception {
		mvc.perform(post("/api/clubes/" + club.getId() + "/incidencias").header("Authorization", jugador.token())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"titulo":"Urgente!","descripcion":"d","categoria":"RED","prioridad":"URGENTE","estado":"CERRADA"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.prioridad").value("MEDIA"))
				.andExpect(jsonPath("$.estado").value("ABIERTA"));
	}

	// ---- limite anti-spam ----

	@Test
	void reportar_maximoCincoSinTerminarPorPersonaYClub_laSexta409() throws Exception {
		for (int i = 1; i <= 5; i++) {
			incidenciaDe(jugador, "Aviso " + i);
		}

		reportar(jugador, club, "Aviso 6", "RED", null).andExpect(status().isConflict());

		assertThat(incidencias.count()).isEqualTo(5);
		// otra persona no se ve afectada
		reportar(monitor, club, "Aviso del monitor", "RED", null).andExpect(status().isCreated());
	}

	@Test
	void reportar_cerrarORechazarLiberaCupo_peroResueltaTodaviaNoLoLibera() throws Exception {
		long a = incidenciaDe(jugador, "A");
		long b = incidenciaDe(jugador, "B");
		long c = incidenciaDe(jugador, "C");
		incidenciaDe(jugador, "D");
		incidenciaDe(jugador, "E");
		reportar(jugador, club, "F", "RED", null).andExpect(status().isConflict());

		estado(admin, a, "RECHAZADA", "Duplicada").andExpect(status().isOk());
		reportar(jugador, club, "F", "RED", null).andExpect(status().isCreated()); // el rechazo libero un cupo
		reportar(jugador, club, "G", "RED", null).andExpect(status().isConflict());

		asignar(admin, b, mant1.id(), null).andExpect(status().isOk());
		estado(mant1, b, "EN_CURSO", null).andExpect(status().isOk());
		estado(mant1, b, "RESUELTA", null).andExpect(status().isOk());
		reportar(jugador, club, "G", "RED", null).andExpect(status().isConflict()); // resuelta aun no es terminal

		estado(jugador, b, "CERRADA", null).andExpect(status().isOk()); // la confirma quien reporto
		reportar(jugador, club, "G", "RED", null).andExpect(status().isCreated());
		assertThat(c).isPositive();
	}

	@Test
	void reportar_elLimiteEsPorClub() throws Exception {
		reservaDe(jugador, pistaB); // tambien ha jugado en el club B
		for (int i = 1; i <= 5; i++) {
			incidenciaDe(jugador, "Aviso " + i);
		}

		reportar(jugador, club, "Aviso 6", "RED", null).andExpect(status().isConflict());
		reportar(jugador, clubB, "En B", "RED", pistaB.getId()).andExpect(status().isCreated());
	}

	// ---- validacion ----

	@Test
	void reportar_datosInvalidos_400() throws Exception {
		reportar(jugador, club, "", "RED", null).andExpect(status().isBadRequest());
		reportar(jugador, club, "   ", "RED", null).andExpect(status().isBadRequest());
		reportar(jugador, club, "x".repeat(121), "RED", null).andExpect(status().isBadRequest());
		reportar(jugador, club, "Titulo", null, null).andExpect(status().isBadRequest());
		reportar(jugador, club, "Titulo", "PINTURA", null).andExpect(status().isBadRequest());
		mvc.perform(post("/api/clubes/" + club.getId() + "/incidencias").header("Authorization", jugador.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"t\",\"descripcion\":\"" + "d".repeat(2001) + "\",\"categoria\":\"RED\"}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/clubes/" + club.getId() + "/incidencias").header("Authorization", jugador.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"t\",\"descripcion\":\"  \",\"categoria\":\"RED\"}"))
				.andExpect(status().isBadRequest());

		assertThat(incidencias.count()).isZero();
		reportar(jugador, club, "x".repeat(120), "RED", null).andExpect(status().isCreated()); // el limite exacto cabe
	}

	@Test
	void reportar_laPistaDebePertenecerAlClub_404() throws Exception {
		reportar(jugador, club, "Red rota", "RED", pistaB.getId()).andExpect(status().isNotFound());
		reportar(jugador, club, "Red rota", "RED", 999_999L).andExpect(status().isNotFound());
		assertThat(incidencias.count()).isZero();
	}

	@Test
	void reportar_clubInexistente404_sinToken401() throws Exception {
		mvc.perform(post("/api/clubes/999999/incidencias").header("Authorization", jugador.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"t\",\"descripcion\":\"d\",\"categoria\":\"RED\"}"))
				.andExpect(status().isNotFound());
		mvc.perform(post("/api/clubes/" + club.getId() + "/incidencias").contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"t\",\"descripcion\":\"d\",\"categoria\":\"RED\"}"))
				.andExpect(status().isUnauthorized());
	}
}
