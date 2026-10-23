package com.vibora.partido;

import com.vibora.notificacion.Notificacion;
import com.vibora.notificacion.TipoNotificacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.vibora.RelojTestConfig.AHORA_INICIAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificacionControllerTests extends PartidosApiTestBase {

	Jug ana;
	Jug beto;

	@BeforeEach
	void preparar() throws Exception {
		ana = jugador("ana@vibora.com", "Ana", "Alba");
		beto = jugador("beto@vibora.com", "Beto", "Bravo");
	}

	private Notificacion guardar(Jug j, TipoNotificacion tipo, String mensaje, int segundos, boolean leida) {
		Notificacion n = com.vibora.TestData.notificacion(usuarios.getReferenceById(j.id()), tipo, mensaje);
		n.setCreadaEn(AHORA_INICIAL.plus(Duration.ofSeconds(segundos)));
		n.setLeida(leida);
		return notificaciones.saveAndFlush(n);
	}

	@Test
	void listar_soloLasPropias_masNuevaPrimera_conSusCampos() throws Exception {
		Notificacion vieja = guardar(ana, TipoNotificacion.CARGO_NUEVO, "Debes 5 €", 1, false);
		Notificacion nueva = guardar(ana, TipoNotificacion.PAGO_RECIBIDO, "Te han pagado", 2, true);
		guardar(beto, TipoNotificacion.CARGO_NUEVO, "De Beto", 3, false);

		mvc.perform(get("/api/notificaciones").header("Authorization", ana.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].id").value(nueva.getId()))
				.andExpect(jsonPath("$[0].tipo").value("PAGO_RECIBIDO"))
				.andExpect(jsonPath("$[0].mensaje").value("Te han pagado"))
				.andExpect(jsonPath("$[0].leida").value(true))
				.andExpect(jsonPath("$[0].reservaId").doesNotExist())
				.andExpect(jsonPath("$[0].creadaEn").value("2030-01-01T00:00:02Z"))
				.andExpect(jsonPath("$[1].id").value(vieja.getId()))
				.andExpect(jsonPath("$[1].leida").value(false));
	}

	@Test
	void listar_soloNoLeidas() throws Exception {
		guardar(ana, TipoNotificacion.CARGO_NUEVO, "a", 1, false);
		guardar(ana, TipoNotificacion.CARGO_NUEVO, "b", 2, true);
		guardar(ana, TipoNotificacion.CARGO_NUEVO, "c", 3, false);

		mvc.perform(get("/api/notificaciones").param("soloNoLeidas", "true").header("Authorization", ana.token()))
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].mensaje").value("c"))
				.andExpect(jsonPath("$[1].mensaje").value("a"));
		mvc.perform(get("/api/notificaciones").param("soloNoLeidas", "false").header("Authorization", ana.token()))
				.andExpect(jsonPath("$.length()").value(3));
	}

	@Test
	void listar_limiteIncluidoPorDefectoDe50_yPaginacion() throws Exception {
		for (int i = 0; i < 55; i++) {
			guardar(ana, TipoNotificacion.CARGO_NUEVO, "n" + i, i, false);
		}

		mvc.perform(get("/api/notificaciones").header("Authorization", ana.token()))
				.andExpect(jsonPath("$.length()").value(50))
				.andExpect(jsonPath("$[0].mensaje").value("n54"));
		mvc.perform(get("/api/notificaciones").param("limit", "10").header("Authorization", ana.token()))
				.andExpect(jsonPath("$.length()").value(10));
		mvc.perform(get("/api/notificaciones").param("limit", "20").param("page", "2").header("Authorization", ana.token()))
				.andExpect(jsonPath("$.length()").value(15))
				.andExpect(jsonPath("$[0].mensaje").value("n14"));
		mvc.perform(get("/api/notificaciones").param("limit", "100000").header("Authorization", ana.token()))
				.andExpect(jsonPath("$.length()").value(55)); // tope razonable: no revienta, devuelve lo que hay
	}

	@Test
	void listar_parametrosInvalidos400_sinToken401() throws Exception {
		mvc.perform(get("/api/notificaciones").param("limit", "0").header("Authorization", ana.token()))
				.andExpect(status().isBadRequest());
		mvc.perform(get("/api/notificaciones").param("page", "-1").header("Authorization", ana.token()))
				.andExpect(status().isBadRequest());
		mvc.perform(get("/api/notificaciones").param("soloNoLeidas", "quiza").header("Authorization", ana.token()))
				.andExpect(status().isBadRequest());
		mvc.perform(get("/api/notificaciones")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/notificaciones/no-leidas/count")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/notificaciones/1/leer")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/notificaciones/leer-todas")).andExpect(status().isUnauthorized());
	}

	@Test
	void contarNoLeidas_soloLasPropias() throws Exception {
		guardar(ana, TipoNotificacion.CARGO_NUEVO, "a", 1, false);
		guardar(ana, TipoNotificacion.CARGO_NUEVO, "b", 2, false);
		guardar(ana, TipoNotificacion.CARGO_NUEVO, "c", 3, true);
		guardar(beto, TipoNotificacion.CARGO_NUEVO, "d", 4, false);

		mvc.perform(get("/api/notificaciones/no-leidas/count").header("Authorization", ana.token()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.count").value(2));
		mvc.perform(get("/api/notificaciones/no-leidas/count").header("Authorization", beto.token()))
				.andExpect(jsonPath("$.count").value(1));
	}

	@Test
	void leer_marcaLaPropia_yEsIdempotente() throws Exception {
		Notificacion n = guardar(ana, TipoNotificacion.CARGO_NUEVO, "a", 1, false);

		mvc.perform(post("/api/notificaciones/" + n.getId() + "/leer").header("Authorization", ana.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(n.getId()))
				.andExpect(jsonPath("$.leida").value(true));
		mvc.perform(post("/api/notificaciones/" + n.getId() + "/leer").header("Authorization", ana.token()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.leida").value(true));

		refrescar();
		assertThat(notificaciones.findById(n.getId())).get().satisfies(x -> assertThat(x.isLeida()).isTrue());
		mvc.perform(get("/api/notificaciones/no-leidas/count").header("Authorization", ana.token()))
				.andExpect(jsonPath("$.count").value(0));
	}

	@Test
	void leer_deOtroUsuario404_inexistente404_yNoCambiaNada() throws Exception {
		Notificacion deBeto = guardar(beto, TipoNotificacion.CARGO_NUEVO, "d", 1, false);

		mvc.perform(post("/api/notificaciones/" + deBeto.getId() + "/leer").header("Authorization", ana.token()))
				.andExpect(status().isNotFound());
		mvc.perform(post("/api/notificaciones/999999/leer").header("Authorization", ana.token()))
				.andExpect(status().isNotFound());

		refrescar();
		assertThat(notificaciones.findById(deBeto.getId())).get().satisfies(x -> assertThat(x.isLeida()).isFalse());
	}

	@Test
	void leerTodas_soloMarcaLasMias() throws Exception {
		guardar(ana, TipoNotificacion.CARGO_NUEVO, "a", 1, false);
		guardar(ana, TipoNotificacion.CARGO_NUEVO, "b", 2, false);
		Notificacion deBeto = guardar(beto, TipoNotificacion.CARGO_NUEVO, "d", 3, false);

		mvc.perform(post("/api/notificaciones/leer-todas").header("Authorization", ana.token()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.marcadas").value(2));

		mvc.perform(get("/api/notificaciones/no-leidas/count").header("Authorization", ana.token()))
				.andExpect(jsonPath("$.count").value(0));
		mvc.perform(get("/api/notificaciones/no-leidas/count").header("Authorization", beto.token()))
				.andExpect(jsonPath("$.count").value(1));
		mvc.perform(post("/api/notificaciones/leer-todas").header("Authorization", ana.token()))
				.andExpect(jsonPath("$.marcadas").value(0));
		assertThat(deBeto.getId()).isNotNull();
	}
}
