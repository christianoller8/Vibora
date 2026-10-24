package com.vibora.incidencia;

import com.vibora.notificacion.Notificacion;
import com.vibora.notificacion.TipoNotificacion;
import com.vibora.usuario.Rol;
import com.vibora.usuario.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Gestion del personal del club (/api/clubes/{id}/personal). Comparte base con los tests de incidencias. */
class PersonalControllerTests extends IncidenciaApiTestBase {

	ResultActions anadir(Persona actor, long clubId, String email, String rol) throws Exception {
		return mvc.perform(post("/api/clubes/" + clubId + "/personal").header("Authorization", actor.token())
				.contentType(MediaType.APPLICATION_JSON).content(json("email", texto(email), "rol", texto(rol))));
	}

	ResultActions listarPersonal(Persona actor, long clubId) throws Exception {
		return mvc.perform(get("/api/clubes/" + clubId + "/personal").header("Authorization", actor.token()));
	}

	ResultActions quitar(Persona actor, long clubId, long usuarioId) throws Exception {
		return mvc.perform(delete("/api/clubes/" + clubId + "/personal/" + usuarioId)
				.header("Authorization", actor.token()));
	}

	Usuario enBd(Persona p) {
		refrescar();
		return usuarios.findById(p.id()).orElseThrow();
	}

	/** El claim "rol" del JWT (el token no se verifica aqui: solo se lee su payload). */
	static String rolDelToken(String bearer) {
		String payload = bearer.substring("Bearer ".length()).split("\\.")[1];
		String json = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
		return json.replaceAll(".*\"rol\":\"([A-Z_]+)\".*", "$1");
	}

	// ---- anadir ----

	@Test
	void anadir_monitor_201_actualizaRolYClub_yAvisaALaPersona() throws Exception {
		anadir(admin, club.getId(), "otro@vibora.com", "MONITOR")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.usuarioId").value(otro.id()))
				.andExpect(jsonPath("$.nombre").value("Olga"))
				.andExpect(jsonPath("$.apellidos").value("Otra"))
				.andExpect(jsonPath("$.email").value("otro@vibora.com"))
				.andExpect(jsonPath("$.rol").value("MONITOR"));

		Usuario u = enBd(otro);
		assertThat(u.getRol()).isEqualTo(Rol.MONITOR);
		assertThat(u.getClub().getId()).isEqualTo(club.getId());
		assertThat(notifsDe(otro, TipoNotificacion.PERSONAL_ANADIDO)).singleElement()
				.satisfies(n -> assertThat(n.getMensaje()).contains("Club A").contains("monitor"));
		assertThat(notifsDe(admin, null)).isEmpty(); // el actor no se avisa a si mismo
	}

	@Test
	void anadir_mantenimiento_201() throws Exception {
		anadir(admin, club.getId(), "otro@vibora.com", "MANTENIMIENTO")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.rol").value("MANTENIMIENTO"));

		assertThat(enBd(otro).getRol()).isEqualTo(Rol.MANTENIMIENTO);
	}

	@Test
	void anadir_elEmailSeNormalizaComoEnElRegistro() throws Exception {
		anadir(admin, club.getId(), "OTRO@Vibora.COM", "MONITOR")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value("otro@vibora.com"));
	}

	@Test
	void anadir_emailInexistente404() throws Exception {
		anadir(admin, club.getId(), "nadie@vibora.com", "MONITOR").andExpect(status().isNotFound());
	}

	@Test
	void anadir_quienYaPerteneceAUnClub409_seaMismoOOtroClub_ySeaCualSeaSuRol() throws Exception {
		anadir(admin, club.getId(), "mant1@vibora.com", "MANTENIMIENTO").andExpect(status().isConflict()); // este club, mismo rol
		anadir(admin, club.getId(), "monitor@vibora.com", "MANTENIMIENTO").andExpect(status().isConflict()); // este club
		anadir(admin, club.getId(), "mantb@vibora.com", "MANTENIMIENTO").andExpect(status().isConflict()); // otro club
		anadir(admin, club.getId(), "adminb@vibora.com", "MONITOR").andExpect(status().isConflict()); // admin de otro club
		anadir(admin, club.getId(), "admin@vibora.com", "MONITOR").andExpect(status().isConflict()); // uno mismo

		assertThat(enBd(mantB).getClub().getId()).isEqualTo(clubB.getId());
		assertThat(enBd(mantB).getRol()).isEqualTo(Rol.MANTENIMIENTO);
		assertThat(enBd(admin).getRol()).isEqualTo(Rol.ADMIN_CLUB);
	}

	@Test
	void anadir_dosVecesLaMismaPersona_laSegunda409() throws Exception {
		anadir(admin, club.getId(), "otro@vibora.com", "MONITOR").andExpect(status().isCreated());
		anadir(admin, club.getId(), "otro@vibora.com", "MONITOR").andExpect(status().isConflict());
		assertThat(notifsDe(otro, TipoNotificacion.PERSONAL_ANADIDO)).hasSize(1);
	}

	@Test
	void anadir_rolNoPermitidoOAusente_o_emailInvalido_400() throws Exception {
		anadir(admin, club.getId(), "otro@vibora.com", "ADMIN_CLUB").andExpect(status().isBadRequest());
		anadir(admin, club.getId(), "otro@vibora.com", "JUGADOR").andExpect(status().isBadRequest());
		anadir(admin, club.getId(), "otro@vibora.com", "JEFE").andExpect(status().isBadRequest());
		anadir(admin, club.getId(), "otro@vibora.com", null).andExpect(status().isBadRequest());
		anadir(admin, club.getId(), "no-es-un-email", "MONITOR").andExpect(status().isBadRequest());
		anadir(admin, club.getId(), null, "MONITOR").andExpect(status().isBadRequest());

		assertThat(enBd(otro).getRol()).isEqualTo(Rol.JUGADOR);
	}

	@Test
	void anadir_soloElAdministradorDeEseClub_403_401_404() throws Exception {
		for (Persona p : List.of(monitor, mant1, jugador, otro, adminB)) {
			anadir(p, club.getId(), "otro@vibora.com", "MONITOR").andExpect(status().isForbidden());
		}
		mvc.perform(post("/api/clubes/" + club.getId() + "/personal").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"otro@vibora.com\",\"rol\":\"MONITOR\"}")).andExpect(status().isUnauthorized());
		anadir(admin, 999_999L, "otro@vibora.com", "MONITOR").andExpect(status().isNotFound());
		assertThat(enBd(otro).getRol()).isEqualTo(Rol.JUGADOR);
	}

	@Test
	void anadir_elTokenAnteriorSigueDiciendoJugador_perolaAutorizacionSeLeeDeLaBd() throws Exception {
		assertThat(rolDelToken(otro.token())).isEqualTo("JUGADOR");
		anadir(admin, club.getId(), "otro@vibora.com", "MONITOR").andExpect(status().isCreated());

		// el JWT ya emitido no cambia (como al crear un club): hasta volver a iniciar sesion sigue diciendo JUGADOR...
		assertThat(rolDelToken(otro.token())).isEqualTo("JUGADOR");
		// ...pero los permisos se deciden con la BD: ya puede reportar como personal aunque nunca haya jugado
		reportar(otro, club, "Foco fundido", "ILUMINACION", null).andExpect(status().isCreated());
		// y un token nuevo ya trae el rol nuevo
		assertThat(rolDelToken(bearer("otro@vibora.com"))).isEqualTo("MONITOR");
	}

	// ---- listar ----

	@Test
	void listar_devuelveElPersonalDelClub_conAdministradoresPrimero_sinJugadoresNiOtrosClubes() throws Exception {
		listarPersonal(admin, club.getId())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(4))
				.andExpect(jsonPath("$[0].rol").value("ADMIN_CLUB"))
				.andExpect(jsonPath("$[0].usuarioId").value(admin.id()))
				.andExpect(jsonPath("$[*].usuarioId").value(org.hamcrest.Matchers.containsInAnyOrder(
						(int) (long) admin.id(), (int) (long) mant1.id(), (int) (long) mant2.id(), (int) (long) monitor.id())))
				.andExpect(jsonPath("$[0].email").value("admin@vibora.com"))
				.andExpect(jsonPath("$[0].nombre").value("Alba"))
				.andExpect(jsonPath("$[0].apellidos").value("Admin"));
	}

	@Test
	void listar_soloElAdministradorDeEseClub_403_401_404() throws Exception {
		for (Persona p : List.of(monitor, mant1, jugador, adminB)) {
			listarPersonal(p, club.getId()).andExpect(status().isForbidden());
		}
		mvc.perform(get("/api/clubes/" + club.getId() + "/personal")).andExpect(status().isUnauthorized());
		listarPersonal(admin, 999_999L).andExpect(status().isNotFound());
	}

	// ---- quitar ----

	@Test
	void quitar_monitor_204_vuelveAJugadorSinClub_yAvisaALaPersona() throws Exception {
		quitar(admin, club.getId(), monitor.id()).andExpect(status().isNoContent());

		Usuario u = enBd(monitor);
		assertThat(u.getRol()).isEqualTo(Rol.JUGADOR);
		assertThat(u.getClub()).isNull();
		assertThat(notifsDe(monitor, TipoNotificacion.PERSONAL_QUITADO)).singleElement()
				.satisfies(n -> assertThat(n.getMensaje()).contains("Club A"));
		listarPersonal(admin, club.getId()).andExpect(jsonPath("$.length()").value(3));
	}

	@Test
	void quitar_quienNoEsPersonalDeEsteClub_404() throws Exception {
		quitar(admin, club.getId(), jugador.id()).andExpect(status().isNotFound()); // jugador sin club
		quitar(admin, club.getId(), mantB.id()).andExpect(status().isNotFound()); // personal de OTRO club
		quitar(admin, club.getId(), 999_999L).andExpect(status().isNotFound());

		assertThat(enBd(mantB).getRol()).isEqualTo(Rol.MANTENIMIENTO);
	}

	@Test
	void quitar_unAdministrador409() throws Exception {
		quitar(admin, club.getId(), admin.id()).andExpect(status().isConflict());

		assertThat(enBd(admin).getRol()).isEqualTo(Rol.ADMIN_CLUB);
		assertThat(enBd(admin).getClub()).isNotNull();
	}

	@Test
	void quitar_soloElAdministradorDeEseClub_403_401_404() throws Exception {
		for (Persona p : List.of(monitor, mant1, jugador, adminB)) {
			quitar(p, club.getId(), mant2.id()).andExpect(status().isForbidden());
		}
		mvc.perform(delete("/api/clubes/" + club.getId() + "/personal/" + mant2.id())).andExpect(status().isUnauthorized());
		quitar(admin, 999_999L, mant2.id()).andExpect(status().isNotFound());
		assertThat(enBd(mant2).getRol()).isEqualTo(Rol.MANTENIMIENTO);
	}

	@Test
	void quitar_desasignaSusIncidenciasActivas_enLaMismaTransaccion_yAvisaAlAdministrador() throws Exception {
		long asignada = incidenciaEn(EstadoIncidencia.ASIGNADA);
		long enCurso = incidenciaEn(EstadoIncidencia.EN_CURSO);
		long resuelta = incidenciaEn(EstadoIncidencia.RESUELTA);
		// una de otra persona de mantenimiento no se toca
		long deMant2 = incidenciaDe(jugador, "Otra");
		asignar(admin, deMant2, mant2.id(), null).andExpect(status().isOk());
		long avisosAntes = notifsDe(admin, TipoNotificacion.INCIDENCIA_ESTADO).size();

		quitar(admin, club.getId(), mant1.id()).andExpect(status().isNoContent());

		for (long id : new long[] { asignada, enCurso }) {
			Incidencia i = incidencia(id);
			assertThat(i.getEstado()).isEqualTo(EstadoIncidencia.ABIERTA);
			assertThat(i.getAsignadaA()).isNull();
			List<IncidenciaEvento> h = historial(id);
			IncidenciaEvento ultimo = h.get(h.size() - 1);
			assertThat(ultimo.getTipo()).isEqualTo(TipoEventoIncidencia.ASIGNADA);
			assertThat(ultimo.getEstadoNuevo()).isEqualTo("ABIERTA");
			assertThat(ultimo.getUsuario().getId()).isEqualTo(admin.id()); // lo hizo el administrador
			assertThat(ultimo.getTexto()).contains("Mario");
		}
		assertThat(historial(asignada).get(historial(asignada).size() - 1).getEstadoAnterior()).isEqualTo("ASIGNADA");
		assertThat(historial(enCurso).get(historial(enCurso).size() - 1).getEstadoAnterior()).isEqualTo("EN_CURSO");
		// la resuelta conserva estado y responsable historico; la de la otra persona sigue igual
		assertThat(incidencia(resuelta).getEstado()).isEqualTo(EstadoIncidencia.RESUELTA);
		assertThat(incidencia(resuelta).getAsignadaA().getId()).isEqualTo(mant1.id());
		assertThat(incidencia(deMant2).getEstado()).isEqualTo(EstadoIncidencia.ASIGNADA);
		assertThat(incidencia(deMant2).getAsignadaA().getId()).isEqualTo(mant2.id());
		// un unico aviso agregado al administrador y el de personal quitado a la persona
		List<Notificacion> avisos = notifsDe(admin, TipoNotificacion.INCIDENCIA_ESTADO);
		assertThat(avisos).hasSize((int) avisosAntes + 1);
		assertThat(avisos.get(avisos.size() - 1).getMensaje()).contains("2 incidencias").contains("Mario");
		assertThat(notifsDe(mant1, TipoNotificacion.PERSONAL_QUITADO)).hasSize(1);
	}

	@Test
	void quitar_conElTokenViejo_yaNoVeNadaDelClub_aunqueElTokenSigaDiciendoMantenimiento() throws Exception {
		long resuelta = incidenciaEn(EstadoIncidencia.RESUELTA);
		detalle(mant1, resuelta).andExpect(status().isOk());
		assertThat(rolDelToken(mant1.token())).isEqualTo("MANTENIMIENTO");

		quitar(admin, club.getId(), mant1.id()).andExpect(status().isNoContent());

		assertThat(rolDelToken(mant1.token())).isEqualTo("MANTENIMIENTO"); // el JWT no cambia
		detalle(mant1, resuelta).andExpect(status().isNotFound()); // pero la BD manda
		listar(mant1, club).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
		asignar(mant1, resuelta, mant1.id(), null).andExpect(status().isNotFound());
	}

	@Test
	void quitar_laPersonaPuedeUnirseDespuesAOtroClub() throws Exception {
		quitar(admin, club.getId(), monitor.id()).andExpect(status().isNoContent());

		anadir(adminB, clubB.getId(), "monitor@vibora.com", "MANTENIMIENTO").andExpect(status().isCreated());

		assertThat(enBd(monitor).getClub().getId()).isEqualTo(clubB.getId());
		assertThat(enBd(monitor).getRol()).isEqualTo(Rol.MANTENIMIENTO);
	}
}
