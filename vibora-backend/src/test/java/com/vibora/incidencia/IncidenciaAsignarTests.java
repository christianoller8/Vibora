package com.vibora.incidencia;

import com.vibora.notificacion.TipoNotificacion;
import com.vibora.usuario.Rol;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /api/incidencias/{id}/asignar. */
class IncidenciaAsignarTests extends IncidenciaApiTestBase {

	@Test
	void asignar_abierta_pasaAAsignada_registraElEvento_yAvisaALaPersonaYAlReportero() throws Exception {
		long id = incidenciaDe(jugador, "Red rota");

		asignar(admin, id, mant1.id(), null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("ASIGNADA"))
				.andExpect(jsonPath("$.asignadaA.id").value(mant1.id()))
				.andExpect(jsonPath("$.asignadaA.nombre").value("Mario Mantiene"))
				.andExpect(jsonPath("$.prioridad").value("MEDIA"));

		Incidencia i = incidencia(id);
		assertThat(i.getEstado()).isEqualTo(EstadoIncidencia.ASIGNADA);
		assertThat(i.getAsignadaA().getId()).isEqualTo(mant1.id());
		assertThat(i.getVersion()).isPositive();
		List<IncidenciaEvento> h = historial(id);
		assertThat(h).extracting(IncidenciaEvento::getTipo).containsExactly(TipoEventoIncidencia.CREADA,
				TipoEventoIncidencia.ASIGNADA);
		IncidenciaEvento e = h.get(1);
		assertThat(e.getEstadoAnterior()).isEqualTo("ABIERTA");
		assertThat(e.getEstadoNuevo()).isEqualTo("ASIGNADA");
		assertThat(e.getUsuario().getId()).isEqualTo(admin.id());
		assertThat(e.getTexto()).contains("Mario Mantiene");

		assertThat(notifsDe(mant1, TipoNotificacion.INCIDENCIA_ASIGNADA)).singleElement()
				.satisfies(n -> assertThat(n.getMensaje()).contains("Red rota"));
		assertThat(notifsDe(jugador, TipoNotificacion.INCIDENCIA_ESTADO)).singleElement()
				.satisfies(n -> assertThat(n.getMensaje()).contains("Red rota").contains("Asignada"));
		assertThat(notifsDe(admin, TipoNotificacion.INCIDENCIA_ASIGNADA)).isEmpty(); // el actor no se avisa
		assertThat(notifsDe(admin, TipoNotificacion.INCIDENCIA_ESTADO)).isEmpty();
		assertThat(notifsDe(mant2, null)).isEmpty();
	}

	@Test
	void asignar_conPrioridad_registraTambienElEventoPrioridad_soloSiCambia() throws Exception {
		long id = incidenciaDe(jugador, "Red rota");

		asignar(admin, id, mant1.id(), "URGENTE").andExpect(status().isOk())
				.andExpect(jsonPath("$.prioridad").value("URGENTE"));

		List<IncidenciaEvento> h = historial(id);
		assertThat(h).extracting(IncidenciaEvento::getTipo).containsExactly(TipoEventoIncidencia.CREADA,
				TipoEventoIncidencia.ASIGNADA, TipoEventoIncidencia.PRIORIDAD);
		assertThat(h.get(2).getEstadoAnterior()).isEqualTo("MEDIA");
		assertThat(h.get(2).getEstadoNuevo()).isEqualTo("URGENTE");
		assertThat(h.get(2).getUsuario().getId()).isEqualTo(admin.id());

		long otraId = incidenciaDe(jugador, "Otra");
		asignar(admin, otraId, mant1.id(), "MEDIA").andExpect(status().isOk()); // ya era MEDIA: no hay cambio
		assertThat(historial(otraId)).extracting(IncidenciaEvento::getTipo)
				.containsExactly(TipoEventoIncidencia.CREADA, TipoEventoIncidencia.ASIGNADA);
	}

	@Test
	void reasignar_asignada_siguesiendoAsignada_conElNuevoResponsable() throws Exception {
		long id = incidenciaEn(EstadoIncidencia.ASIGNADA);

		asignar(admin, id, mant2.id(), null).andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("ASIGNADA"))
				.andExpect(jsonPath("$.asignadaA.id").value(mant2.id()));

		assertThat(incidencia(id).getAsignadaA().getId()).isEqualTo(mant2.id());
		IncidenciaEvento ultimo = historial(id).get(historial(id).size() - 1);
		assertThat(ultimo.getTipo()).isEqualTo(TipoEventoIncidencia.ASIGNADA);
		assertThat(ultimo.getEstadoAnterior()).isEqualTo("ASIGNADA");
		assertThat(ultimo.getEstadoNuevo()).isEqualTo("ASIGNADA");
		assertThat(notifsDe(mant2, TipoNotificacion.INCIDENCIA_ASIGNADA)).hasSize(1);
	}

	@Test
	void reasignar_enCurso_vuelveAAsignada_laNuevaPersonaEmpiezaDeNuevo() throws Exception {
		long id = incidenciaEn(EstadoIncidencia.EN_CURSO);

		asignar(admin, id, mant2.id(), null).andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("ASIGNADA"))
				.andExpect(jsonPath("$.asignadaA.id").value(mant2.id()));

		IncidenciaEvento ultimo = historial(id).get(historial(id).size() - 1);
		assertThat(ultimo.getEstadoAnterior()).isEqualTo("EN_CURSO");
		assertThat(ultimo.getEstadoNuevo()).isEqualTo("ASIGNADA");
		// la persona anterior ya no la ve entre las suyas y no puede cambiarle el estado
		estado(mant1, id, "RESUELTA", null).andExpect(status().isNotFound());
		estado(mant2, id, "EN_CURSO", null).andExpect(status().isOk());
	}

	@Test
	void asignar_alMismoResponsable_409_salvoQueSoloCambieLaPrioridad() throws Exception {
		long id = incidenciaEn(EstadoIncidencia.EN_CURSO);
		long eventosAntes = historial(id).size();

		asignar(admin, id, mant1.id(), null).andExpect(status().isConflict());
		asignar(admin, id, mant1.id(), "MEDIA").andExpect(status().isConflict()); // misma prioridad que ya tiene
		assertThat(historial(id)).hasSize((int) eventosAntes);

		asignar(admin, id, mant1.id(), "ALTA").andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("EN_CURSO")) // no se reinicia
				.andExpect(jsonPath("$.prioridad").value("ALTA"))
				.andExpect(jsonPath("$.asignadaA.id").value(mant1.id()));
		List<IncidenciaEvento> h = historial(id);
		assertThat(h).hasSize((int) eventosAntes + 1);
		assertThat(h.get(h.size() - 1).getTipo()).isEqualTo(TipoEventoIncidencia.PRIORIDAD);
	}

	@Test
	void asignar_soloDesdeAbiertaAsignadaOEnCurso_resueltaCerradaYRechazada409() throws Exception {
		for (EstadoIncidencia estado : List.of(EstadoIncidencia.RESUELTA, EstadoIncidencia.CERRADA, EstadoIncidencia.RECHAZADA)) {
			long id = incidenciaEn(estado);
			asignar(admin, id, mant2.id(), null).andExpect(status().isConflict());
			assertThat(incidencia(id).getEstado()).isEqualTo(estado);
		}
	}

	@Test
	void asignar_elResponsableDebeSerDeMantenimientoDeEsteClub_400() throws Exception {
		long id = incidenciaDe(jugador, "Red rota");

		asignar(admin, id, monitor.id(), null).andExpect(status().isBadRequest()); // monitor
		asignar(admin, id, jugador.id(), null).andExpect(status().isBadRequest()); // jugador
		asignar(admin, id, admin.id(), null).andExpect(status().isBadRequest()); // administrador
		asignar(admin, id, mantB.id(), null).andExpect(status().isBadRequest()); // de OTRO club
		asignar(admin, id, 999_999L, null).andExpect(status().isBadRequest()); // inexistente: igual que los demas
		asignar(admin, id, null, null).andExpect(status().isBadRequest()); // falta
		asignar(admin, id, mant1.id(), "CRITICA").andExpect(status().isBadRequest()); // prioridad desconocida

		Incidencia i = incidencia(id);
		assertThat(i.getEstado()).isEqualTo(EstadoIncidencia.ABIERTA);
		assertThat(i.getAsignadaA()).isNull();
		assertThat(historial(id)).hasSize(1);
	}

	@Test
	void asignar_soloElAdministradorDelClub_403SiLaVe_404SiNo() throws Exception {
		long id = incidenciaDe(jugador, "Red rota");
		asignar(admin, id, mant1.id(), null).andExpect(status().isOk());

		asignar(jugador, id, mant2.id(), null).andExpect(status().isForbidden()); // el reportero
		asignar(mant1, id, mant1.id(), null).andExpect(status().isForbidden()); // el asignado
		asignar(otro, id, mant2.id(), null).andExpect(status().isNotFound()); // no la ve
		asignar(monitor, id, mant2.id(), null).andExpect(status().isNotFound());
		asignar(mant2, id, mant2.id(), null).andExpect(status().isNotFound());
		asignar(adminB, id, mantB.id(), null).andExpect(status().isNotFound()); // administrador de OTRO club
		asignar(admin, 999_999L, mant2.id(), null).andExpect(status().isNotFound());
		mvc.perform(post("/api/incidencias/" + id + "/asignar").contentType("application/json")
				.content("{\"usuarioId\":" + mant2.id() + "}")).andExpect(status().isUnauthorized());

		assertThat(incidencia(id).getAsignadaA().getId()).isEqualTo(mant1.id());
	}

	@Test
	void asignar_siElReporteroEsLaPersonaAsignada_recibeSoloElAvisoDeAsignacion() throws Exception {
		long id = incidenciaDe(mant1, "La reporto Mario");

		asignar(admin, id, mant1.id(), null).andExpect(status().isOk());

		assertThat(notifsDe(mant1, TipoNotificacion.INCIDENCIA_ASIGNADA)).hasSize(1);
		assertThat(notifsDe(mant1, TipoNotificacion.INCIDENCIA_ESTADO)).isEmpty(); // sin duplicar
	}

	@Test
	void asignar_siElReporteroEsElAdministrador_noSeAvisaASiMismo() throws Exception {
		long id = incidenciaDe(admin, "La reporto el admin");

		asignar(admin, id, mant1.id(), null).andExpect(status().isOk());

		assertThat(notifsDe(admin, null)).isEmpty();
		assertThat(notifsDe(mant1, TipoNotificacion.INCIDENCIA_ASIGNADA)).hasSize(1);
	}

	@Test
	void asignar_alOtroAdministradorDelClubComoReportero_lePuedeAvisar() throws Exception {
		Persona admin2 = persona("admin2@vibora.com", "Aitor", "Admin2", Rol.ADMIN_CLUB, club);
		long id = incidenciaDe(admin2, "La reporto el admin2");

		asignar(admin, id, mant1.id(), null).andExpect(status().isOk());

		assertThat(notifsDe(admin2, TipoNotificacion.INCIDENCIA_ESTADO)).hasSize(1);
	}
}
