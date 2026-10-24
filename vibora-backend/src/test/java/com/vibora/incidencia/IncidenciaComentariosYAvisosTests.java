package com.vibora.incidencia;

import com.vibora.notificacion.Notificacion;
import com.vibora.notificacion.TipoNotificacion;
import com.vibora.usuario.Rol;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /api/incidencias/{id}/comentarios y a quien (y a quien NO) se avisa en cada cambio. */
class IncidenciaComentariosYAvisosTests extends IncidenciaApiTestBase {

	// ---- comentarios ----

	@Test
	void comentar_elReportero_201_registraElEvento_yAvisaAlAsignadoYAlAdministradorPeroNoAEl() throws Exception {
		long id = incidenciaEn(EstadoIncidencia.EN_CURSO);
		long antes = totalNotifs();

		comentar(jugador, id, "Sigue sin ir").andExpect(status().isCreated())
				.andExpect(jsonPath("$.eventos[-1:].tipo").value(org.hamcrest.Matchers.contains("COMENTARIO")))
				.andExpect(jsonPath("$.eventos[-1:].texto").value(org.hamcrest.Matchers.contains("Sigue sin ir")))
				.andExpect(jsonPath("$.eventos[-1:].usuario.id").value(org.hamcrest.Matchers.contains((int) (long) jugador.id())));

		IncidenciaEvento e = historial(id).get(historial(id).size() - 1);
		assertThat(e.getTipo()).isEqualTo(TipoEventoIncidencia.COMENTARIO);
		assertThat(e.getTexto()).isEqualTo("Sigue sin ir");
		assertThat(e.getEstadoAnterior()).isNull();
		assertThat(e.getEstadoNuevo()).isNull();
		assertThat(e.getUsuario().getId()).isEqualTo(jugador.id());
		assertThat(totalNotifs()).isEqualTo(antes + 2);
		assertThat(notifsDe(mant1, TipoNotificacion.INCIDENCIA_COMENTARIO)).singleElement()
				.satisfies(n -> assertThat(n.getMensaje()).contains("Juan Jugador").contains("Sigue sin ir"));
		assertThat(notifsDe(admin, TipoNotificacion.INCIDENCIA_COMENTARIO)).hasSize(1);
		assertThat(notifsDe(jugador, TipoNotificacion.INCIDENCIA_COMENTARIO)).isEmpty(); // no al autor
		assertThat(notifsDe(mant2, TipoNotificacion.INCIDENCIA_COMENTARIO)).isEmpty();
	}

	@Test
	void comentar_elAsignado_avisaAlReporteroYAlAdministrador() throws Exception {
		long id = incidenciaEn(EstadoIncidencia.ASIGNADA);

		comentar(mant1, id, "Pido el repuesto").andExpect(status().isCreated());

		assertThat(notifsDe(jugador, TipoNotificacion.INCIDENCIA_COMENTARIO)).hasSize(1);
		assertThat(notifsDe(admin, TipoNotificacion.INCIDENCIA_COMENTARIO)).hasSize(1);
		assertThat(notifsDe(mant1, TipoNotificacion.INCIDENCIA_COMENTARIO)).isEmpty();
	}

	@Test
	void comentar_elAdministrador_avisaAlReporteroYAlAsignado_yUnaIncidenciaAbiertaSoloAlReportero() throws Exception {
		long abierta = incidenciaDe(jugador, "Red rota");
		comentar(admin, abierta, "La vemos esta semana").andExpect(status().isCreated());
		assertThat(notifsDe(jugador, TipoNotificacion.INCIDENCIA_COMENTARIO)).hasSize(1);
		assertThat(notifsDe(admin, TipoNotificacion.INCIDENCIA_COMENTARIO)).isEmpty();
		assertThat(notifsDe(mant1, TipoNotificacion.INCIDENCIA_COMENTARIO)).isEmpty();

		long asignada = incidenciaEn(EstadoIncidencia.ASIGNADA);
		comentar(admin, asignada, "Prioridad").andExpect(status().isCreated());
		assertThat(notifsDe(mant1, TipoNotificacion.INCIDENCIA_COMENTARIO)).hasSize(1);
	}

	@Test
	void comentar_quienSeaVariasCosasALaVez_recibeUnSoloAviso() throws Exception {
		// el administrador reporto la incidencia: es reportero Y administrador, pero un unico destinatario
		long id = incidenciaDe(admin, "La reporto el admin");
		asignar(admin, id, mant1.id(), null).andExpect(status().isOk());
		long antes = notifsDe(admin, null).size();

		comentar(mant1, id, "Hecho a medias").andExpect(status().isCreated());

		assertThat(notifsDe(admin, TipoNotificacion.INCIDENCIA_COMENTARIO)).hasSize(1);
		assertThat(notifsDe(admin, null)).hasSize((int) antes + 1);
	}

	@Test
	void comentar_quienNoParticipa_404_yNadaSeGuarda() throws Exception {
		long id = incidenciaEn(EstadoIncidencia.ASIGNADA);
		long eventos = historial(id).size();
		long notifs = totalNotifs();

		for (Persona p : List.of(otro, monitor, mant2, adminB, mantB)) {
			comentar(p, id, "Intruso").andExpect(status().isNotFound());
		}
		comentar(admin, 999_999L, "x").andExpect(status().isNotFound());
		mvc.perform(post("/api/incidencias/" + id + "/comentarios").contentType(MediaType.APPLICATION_JSON)
				.content("{\"texto\":\"x\"}")).andExpect(status().isUnauthorized());

		assertThat(historial(id)).hasSize((int) eventos);
		assertThat(totalNotifs()).isEqualTo(notifs);
	}

	@Test
	void comentar_enEstadoTerminal_409_perosiEnResuelta() throws Exception {
		long cerrada = incidenciaEn(EstadoIncidencia.CERRADA);
		long rechazada = incidenciaEn(EstadoIncidencia.RECHAZADA);
		long resuelta = incidenciaEn(EstadoIncidencia.RESUELTA);

		comentar(jugador, cerrada, "Gracias").andExpect(status().isConflict());
		comentar(admin, rechazada, "Mas info").andExpect(status().isConflict());
		comentar(jugador, resuelta, "Funciona, gracias").andExpect(status().isCreated());
	}

	@Test
	void comentar_textoVacioOLargo_400() throws Exception {
		long id = incidenciaDe(jugador, "Red rota");

		comentar(jugador, id, "").andExpect(status().isBadRequest());
		comentar(jugador, id, "   ").andExpect(status().isBadRequest());
		comentar(jugador, id, null).andExpect(status().isBadRequest());
		comentar(jugador, id, "x".repeat(2001)).andExpect(status().isBadRequest());
		comentar(jugador, id, "x".repeat(2000)).andExpect(status().isCreated());
	}

	@Test
	void comentar_noTocaLaIncidencia_laVersionNiLaFechaCambian() throws Exception {
		long id = incidenciaDe(jugador, "Red rota");
		Long version = incidencia(id).getVersion();
		reloj.fijar(reloj.instant().plusSeconds(300));

		comentar(jugador, id, "Nota").andExpect(status().isCreated());

		assertThat(incidencia(id).getVersion()).isEqualTo(version);
		assertThat(incidencia(id).getActualizadaEn()).isBefore(reloj.instant());
	}

	// ---- avisos de cambios de estado ----

	@Test
	void estado_laPersonaAsignadaCambiaElEstado_avisaAlReporteroYAlAdministrador() throws Exception {
		long id = incidenciaEn(EstadoIncidencia.ASIGNADA);
		long reporteroAntes = notifsDe(jugador, TipoNotificacion.INCIDENCIA_ESTADO).size();
		long adminAntes = notifsDe(admin, TipoNotificacion.INCIDENCIA_ESTADO).size();

		estado(mant1, id, "EN_CURSO", null).andExpect(status().isOk());

		List<Notificacion> alReportero = notifsDe(jugador, TipoNotificacion.INCIDENCIA_ESTADO);
		assertThat(alReportero).hasSize((int) reporteroAntes + 1);
		assertThat(alReportero.get(alReportero.size() - 1).getMensaje()).contains("Red rota").contains("En curso");
		assertThat(notifsDe(admin, TipoNotificacion.INCIDENCIA_ESTADO)).hasSize((int) adminAntes + 1);
		assertThat(notifsDe(mant1, TipoNotificacion.INCIDENCIA_ESTADO)).isEmpty(); // el actor no se avisa
		assertThat(notifsDe(mant2, null)).isEmpty();
		assertThat(notifsDe(monitor, null)).isEmpty();
		assertThat(notifsDe(otro, null)).isEmpty();
		assertThat(notifsDe(adminB, null)).isEmpty();
	}

	@Test
	void estado_elAdministradorCambiaElEstado_avisaAlReporteroYAlAsignado_peroNoAEl() throws Exception {
		long id = incidenciaEn(EstadoIncidencia.EN_CURSO);
		long adminAntes = notifsDe(admin, null).size();
		long mantAntes = notifsDe(mant1, TipoNotificacion.INCIDENCIA_ESTADO).size();
		long jugadorAntes = notifsDe(jugador, TipoNotificacion.INCIDENCIA_ESTADO).size();

		estado(admin, id, "RESUELTA", null).andExpect(status().isOk());

		assertThat(notifsDe(jugador, TipoNotificacion.INCIDENCIA_ESTADO)).hasSize((int) jugadorAntes + 1);
		assertThat(notifsDe(mant1, TipoNotificacion.INCIDENCIA_ESTADO)).hasSize((int) mantAntes + 1);
		assertThat(notifsDe(admin, null)).hasSize((int) adminAntes);
	}

	@Test
	void estado_elReporteroReabre_avisaAlAsignadoYAlAdministrador_peroNoAEl() throws Exception {
		long id = incidenciaEn(EstadoIncidencia.RESUELTA);
		long jugadorAntes = notifsDe(jugador, null).size();
		long mantAntes = notifsDe(mant1, TipoNotificacion.INCIDENCIA_ESTADO).size();
		long adminAntes = notifsDe(admin, TipoNotificacion.INCIDENCIA_ESTADO).size();

		estado(jugador, id, "EN_CURSO", "Sigue rota").andExpect(status().isOk());

		assertThat(notifsDe(mant1, TipoNotificacion.INCIDENCIA_ESTADO)).hasSize((int) mantAntes + 1);
		assertThat(notifsDe(admin, TipoNotificacion.INCIDENCIA_ESTADO)).hasSize((int) adminAntes + 1);
		assertThat(notifsDe(jugador, null)).hasSize((int) jugadorAntes);
	}

	@Test
	void estado_rechazar_avisaSoloAlReportero_conElMotivo() throws Exception {
		long id = incidenciaDe(jugador, "Red rota");
		long antes = totalNotifs();

		estado(admin, id, "RECHAZADA", "Es un duplicado").andExpect(status().isOk());

		assertThat(totalNotifs()).isEqualTo(antes + 1);
		assertThat(notifsDe(jugador, TipoNotificacion.INCIDENCIA_ESTADO)).singleElement()
				.satisfies(n -> assertThat(n.getMensaje()).contains("Rechazada").contains("Es un duplicado"));
	}

	@Test
	void estado_variosAdministradores_todosMenosElQueActua() throws Exception {
		Persona admin2 = persona("admin2@vibora.com", "Aitor", "Admin2", Rol.ADMIN_CLUB, club);
		long id = incidenciaEn(EstadoIncidencia.ASIGNADA);
		long adminAntes = notifsDe(admin, TipoNotificacion.INCIDENCIA_ESTADO).size();
		long admin2Antes = notifsDe(admin2, TipoNotificacion.INCIDENCIA_ESTADO).size();

		estado(admin2, id, "EN_CURSO", null).andExpect(status().isOk());

		assertThat(notifsDe(admin, TipoNotificacion.INCIDENCIA_ESTADO)).hasSize((int) adminAntes + 1);
		assertThat(notifsDe(admin2, TipoNotificacion.INCIDENCIA_ESTADO)).hasSize((int) admin2Antes);
	}

	@Test
	void unaOperacionQueFallaNoDejaAvisosNiEventos() throws Exception {
		long id = incidenciaEn(EstadoIncidencia.ASIGNADA);
		long notifs = totalNotifs();
		long eventos = historial(id).size();

		estado(mant1, id, "RESUELTA", null).andExpect(status().isConflict()); // ilegal
		estado(jugador, id, "EN_CURSO", null).andExpect(status().isForbidden()); // sin permiso
		asignar(admin, id, monitor.id(), null).andExpect(status().isBadRequest()); // destino invalido

		assertThat(totalNotifs()).isEqualTo(notifs);
		assertThat(historial(id)).hasSize((int) eventos);
	}
}
