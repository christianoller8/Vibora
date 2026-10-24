package com.vibora.incidencia;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.vibora.incidencia.EstadoIncidencia.ABIERTA;
import static com.vibora.incidencia.EstadoIncidencia.ASIGNADA;
import static com.vibora.incidencia.EstadoIncidencia.CERRADA;
import static com.vibora.incidencia.EstadoIncidencia.EN_CURSO;
import static com.vibora.incidencia.EstadoIncidencia.RECHAZADA;
import static com.vibora.incidencia.EstadoIncidencia.RESUELTA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /api/incidencias/{id}/estado: maquina de estados y reglas por rol. */
class IncidenciaEstadoTests extends IncidenciaApiTestBase {

	/** Las unicas transiciones que admite el endpoint de estado (ABIERTA->ASIGNADA va por /asignar). */
	static final Map<EstadoIncidencia, Set<EstadoIncidencia>> LEGALES = Map.of(
			ABIERTA, Set.of(RECHAZADA),
			ASIGNADA, Set.of(EN_CURSO),
			EN_CURSO, Set.of(RESUELTA),
			RESUELTA, Set.of(CERRADA, EN_CURSO),
			CERRADA, Set.of(),
			RECHAZADA, Set.of());

	// ---- caminos legales y su efecto ----

	@Test
	void cicloCompleto_asignadaEnCursoResueltaCerrada_conEventosEnOrden() throws Exception {
		long id = incidenciaDe(jugador, "Red rota");
		asignar(admin, id, mant1.id(), null).andExpect(status().isOk());

		estado(mant1, id, "EN_CURSO", "Voy para alla").andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("EN_CURSO")).andExpect(jsonPath("$.resueltaEn").doesNotExist());
		reloj.fijar(reloj.instant().plusSeconds(3600));
		estado(mant1, id, "RESUELTA", "Cambiada la red").andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("RESUELTA"))
				.andExpect(jsonPath("$.resueltaEn").value(reloj.instant().toString()));
		assertThat(incidencia(id).getResueltaEn()).isEqualTo(reloj.instant());
		estado(jugador, id, "CERRADA", null).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CERRADA"));

		List<IncidenciaEvento> h = historial(id);
		assertThat(h).extracting(IncidenciaEvento::getTipo).containsExactly(TipoEventoIncidencia.CREADA,
				TipoEventoIncidencia.ASIGNADA, TipoEventoIncidencia.ESTADO, TipoEventoIncidencia.ESTADO,
				TipoEventoIncidencia.ESTADO);
		assertThat(h.subList(2, 5)).extracting(IncidenciaEvento::getEstadoAnterior)
				.containsExactly("ASIGNADA", "EN_CURSO", "RESUELTA");
		assertThat(h.subList(2, 5)).extracting(IncidenciaEvento::getEstadoNuevo)
				.containsExactly("EN_CURSO", "RESUELTA", "CERRADA");
		assertThat(h.subList(2, 5)).extracting(e -> e.getUsuario().getId())
				.containsExactly(mant1.id(), mant1.id(), jugador.id());
		assertThat(h.subList(2, 5)).extracting(IncidenciaEvento::getTexto)
				.containsExactly("Voy para alla", "Cambiada la red", null);
		assertThat(incidencia(id).getResueltaEn()).isEqualTo(reloj.instant()); // se conserva al cerrar
	}

	@Test
	void rechazar_desdeAbierta_exigeComentario_yPasaARechazada() throws Exception {
		long id = incidenciaDe(jugador, "No es un problema");

		estado(admin, id, "RECHAZADA", null).andExpect(status().isBadRequest());
		estado(admin, id, "RECHAZADA", "   ").andExpect(status().isBadRequest());
		assertThat(incidencia(id).getEstado()).isEqualTo(ABIERTA);

		estado(admin, id, "RECHAZADA", "Es lo normal en esa pista").andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("RECHAZADA"))
				.andExpect(jsonPath("$.eventos[1].texto").value("Es lo normal en esa pista"));
		assertThat(incidencia(id).getEstado()).isEqualTo(RECHAZADA);
	}

	@Test
	void reabrir_resueltaAEnCurso_laLimpiaResueltaEn_yPuedeHacerloElReporteroOElAdmin() throws Exception {
		long id = incidenciaEn(RESUELTA);
		assertThat(incidencia(id).getResueltaEn()).isNotNull();

		estado(jugador, id, "EN_CURSO", "Sigue rota").andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("EN_CURSO"))
				.andExpect(jsonPath("$.resueltaEn").doesNotExist())
				.andExpect(jsonPath("$.asignadaA.id").value(mant1.id()));
		Incidencia i = incidencia(id);
		assertThat(i.getResueltaEn()).isNull();
		assertThat(i.getEstado()).isEqualTo(EN_CURSO);

		estado(mant1, id, "RESUELTA", null).andExpect(status().isOk());
		estado(admin, id, "EN_CURSO", null).andExpect(status().isOk()); // tambien el administrador
		assertThat(incidencia(id).getResueltaEn()).isNull();
	}

	@Test
	void reabrir_siElResponsableYaNoEsDelClub_laIncidenciaVuelveAAbierta_yPuedeReasignarse() throws Exception {
		long id = incidenciaEn(RESUELTA);
		mvc.perform(delete("/api/clubes/" + club.getId() + "/personal/" + mant1.id())
				.header("Authorization", admin.token())).andExpect(status().isNoContent());
		assertThat(incidencia(id).getEstado()).isEqualTo(RESUELTA); // la resuelta no se toca al retirarlo

		estado(jugador, id, "EN_CURSO", "Sigue rota").andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("ABIERTA"))
				.andExpect(jsonPath("$.asignadaA").doesNotExist())
				.andExpect(jsonPath("$.resueltaEn").doesNotExist());

		IncidenciaEvento ultimo = historial(id).get(historial(id).size() - 1);
		assertThat(ultimo.getEstadoAnterior()).isEqualTo("RESUELTA");
		assertThat(ultimo.getEstadoNuevo()).isEqualTo("ABIERTA");
		assertThat(ultimo.getTexto()).contains("Sigue rota").contains("ya no");
		asignar(admin, id, mant2.id(), null).andExpect(status().isOk());
	}

	@Test
	void reabrir_sinResponsable_conComentarioDe2000_noRevientaYElEventoCabeEnLaColumna() throws Exception {
		long id = incidenciaEn(RESUELTA);
		mvc.perform(delete("/api/clubes/" + club.getId() + "/personal/" + mant1.id())
				.header("Authorization", admin.token())).andExpect(status().isNoContent());

		estado(jugador, id, "EN_CURSO", "x".repeat(2000)).andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("ABIERTA"));

		IncidenciaEvento ultimo = historial(id).get(historial(id).size() - 1);
		assertThat(ultimo.getTexto()).hasSizeLessThanOrEqualTo(2000)
				.endsWith("(la persona asignada ya no pertenece al club; vuelve a estar abierta)");
	}

	@Test
	void cerrar_laPuedeHacerElAdministradorSinQueElReporteroLoConfirme() throws Exception {
		long id = incidenciaEn(RESUELTA);

		estado(admin, id, "CERRADA", null).andExpect(status().isOk());

		assertThat(incidencia(id).getEstado()).isEqualTo(CERRADA);
	}

	@Test
	void elAdministradorPuedeHacerAvanzarLaIncidenciaAunqueNoSeaElAsignado() throws Exception {
		long id = incidenciaEn(ASIGNADA);

		estado(admin, id, "EN_CURSO", null).andExpect(status().isOk());
		estado(admin, id, "RESUELTA", null).andExpect(status().isOk());
		assertThat(incidencia(id).getEstado()).isEqualTo(RESUELTA);
	}

	// ---- transiciones ilegales ----

	@Test
	void cualquierTransicionNoPrevista_409_conMensajeClaro_yNadaCambia() throws Exception {
		for (EstadoIncidencia desde : EstadoIncidencia.values()) {
			long id = incidenciaEn(desde);
			long eventos = historial(id).size();
			for (EstadoIncidencia hacia : EstadoIncidencia.values()) {
				if (LEGALES.get(desde).contains(hacia)) {
					continue;
				}
				estado(admin, id, hacia.name(), "motivo").andExpect(status().isConflict())
						.andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("No se puede pasar de")));
			}
			assertThat(incidencia(id).getEstado()).as("tras los intentos desde %s", desde).isEqualTo(desde);
			assertThat(historial(id)).hasSize((int) eventos);
		}
	}

	@Test
	void asignadaYEnCursoNoVuelvenAAbiertaPorElEndpointDeEstado_soloAlDesasignar() throws Exception {
		long asignada = incidenciaEn(ASIGNADA);
		long enCurso = incidenciaEn(EN_CURSO);

		estado(admin, asignada, "ABIERTA", null).andExpect(status().isConflict());
		estado(admin, enCurso, "ABIERTA", null).andExpect(status().isConflict());
		estado(admin, asignada, "ASIGNADA", null).andExpect(status().isConflict()); // tampoco "no hacer nada"
	}

	@Test
	void unaTransicionIlegalDa409AunqueQuienLaPideNoTengaPermiso() throws Exception {
		long abierta = incidenciaDe(jugador, "Red rota");

		estado(jugador, abierta, "CERRADA", null).andExpect(status().isConflict());
	}

	// ---- reglas por rol ----

	@Test
	void quienNoTienePermisoParaUnaTransicionLegal_403() throws Exception {
		long abierta = incidenciaDe(jugador, "Red rota");
		long asignada = incidenciaEn(ASIGNADA);
		long enCurso = incidenciaEn(EN_CURSO);
		long resuelta = incidenciaEn(RESUELTA);

		estado(jugador, abierta, "RECHAZADA", "no").andExpect(status().isForbidden()); // el reportero no rechaza lo suyo
		estado(jugador, asignada, "EN_CURSO", null).andExpect(status().isForbidden());
		estado(jugador, enCurso, "RESUELTA", null).andExpect(status().isForbidden());
		estado(mant1, resuelta, "CERRADA", null).andExpect(status().isForbidden()); // el asignado no cierra
		estado(mant1, resuelta, "EN_CURSO", null).andExpect(status().isForbidden()); // ni reabre
		estado(mant1, abierta, "RECHAZADA", "no").andExpect(status().isNotFound()); // no la ve (aun no es suya)

		assertThat(incidencia(abierta).getEstado()).isEqualTo(ABIERTA);
		assertThat(incidencia(asignada).getEstado()).isEqualTo(ASIGNADA);
		assertThat(incidencia(enCurso).getEstado()).isEqualTo(EN_CURSO);
		assertThat(incidencia(resuelta).getEstado()).isEqualTo(RESUELTA);
	}

	@Test
	void quienNoVeLaIncidencia_404_ySinToken401() throws Exception {
		long asignada = incidenciaEn(ASIGNADA);

		estado(mant2, asignada, "EN_CURSO", null).andExpect(status().isNotFound()); // otra persona de mantenimiento
		estado(otro, asignada, "EN_CURSO", null).andExpect(status().isNotFound());
		estado(monitor, asignada, "EN_CURSO", null).andExpect(status().isNotFound());
		estado(adminB, asignada, "EN_CURSO", null).andExpect(status().isNotFound()); // administrador de otro club
		estado(mantB, asignada, "EN_CURSO", null).andExpect(status().isNotFound());
		estado(admin, 999_999L, "EN_CURSO", null).andExpect(status().isNotFound());
		mvc.perform(post("/api/incidencias/" + asignada + "/estado").contentType(MediaType.APPLICATION_JSON)
				.content("{\"estado\":\"EN_CURSO\"}")).andExpect(status().isUnauthorized());
		assertThat(incidencia(asignada).getEstado()).isEqualTo(ASIGNADA);
	}

	// ---- validacion ----

	@Test
	void estadoAusenteDesconocidoOComentarioDemasiadoLargo_400() throws Exception {
		long id = incidenciaEn(ASIGNADA);

		estado(mant1, id, null, null).andExpect(status().isBadRequest());
		estado(mant1, id, "PAUSADA", null).andExpect(status().isBadRequest());
		estado(mant1, id, "EN_CURSO", "x".repeat(2001)).andExpect(status().isBadRequest());
		estado(mant1, id, "EN_CURSO", "x".repeat(2000)).andExpect(status().isOk());
	}

	@Test
	void unCambioDeEstadoActualizaLaFechaDeActualizacion() throws Exception {
		long id = incidenciaEn(ASIGNADA);
		reloj.fijar(reloj.instant().plusSeconds(120));

		estado(mant1, id, "EN_CURSO", null).andExpect(status().isOk());

		assertThat(incidencia(id).getActualizadaEn()).isEqualTo(reloj.instant());
		assertThat(incidencia(id).getCreadaEn()).isBefore(reloj.instant());
	}
}
