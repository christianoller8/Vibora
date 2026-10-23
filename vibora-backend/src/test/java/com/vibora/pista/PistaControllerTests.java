package com.vibora.pista;

import com.vibora.ApiTestBase;
import com.vibora.TestData;
import com.vibora.club.Club;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PistaControllerTests extends ApiTestBase {

	@Autowired PistaRepository pistas;

	private static String pistaJson(String nombre, String tipo) {
		return nombre == null || tipo == null
				? "{\"nombre\":%s,\"tipo\":%s}".formatted(q(nombre), q(tipo))
				: "{\"nombre\":\"%s\",\"tipo\":\"%s\"}".formatted(nombre, tipo);
	}

	private static String q(String s) {
		return s == null ? "null" : "\"" + s + "\"";
	}

	private static String pistaJson(String nombre, String tipo, boolean activa) {
		return "{\"nombre\":\"%s\",\"tipo\":\"%s\",\"activa\":%s}".formatted(nombre, tipo, activa);
	}

	private Pista crearPista(Club club, String nombre, boolean activa) {
		Pista p = TestData.pista(club, nombre);
		p.setActiva(activa);
		return pistas.saveAndFlush(p);
	}

	// ---- Listado ----

	@Test
	void listar_jugadorYAdminAjenoVenSoloActivas_adminPropioVeTodas() throws Exception {
		Club a = crearClub("Club A");
		Club b = crearClub("Club B");
		crearPista(a, "Pista 1", true);
		crearPista(a, "Pista 2", false);
		crearPista(b, "Otra", true);
		String propio = bearerAdminDe(a, "admina@vibora.com");
		String ajeno = bearerAdminDe(b, "adminb@vibora.com");
		String jugador = bearerJugador("p@vibora.com");

		mvc.perform(get("/api/clubes/" + a.getId() + "/pistas").header("Authorization", jugador))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].nombre").value("Pista 1"))
				.andExpect(jsonPath("$[0].clubId").value(a.getId()))
				.andExpect(jsonPath("$[0].activa").value(true));

		mvc.perform(get("/api/clubes/" + a.getId() + "/pistas").header("Authorization", ajeno))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));

		mvc.perform(get("/api/clubes/" + a.getId() + "/pistas").header("Authorization", propio))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2));
	}

	@Test
	void listar_clubInexistente404_ySinToken401() throws Exception {
		mvc.perform(get("/api/clubes/999999/pistas").header("Authorization", bearerJugador("p@vibora.com")))
				.andExpect(status().isNotFound());
		mvc.perform(get("/api/clubes/1/pistas")).andExpect(status().isUnauthorized());
	}

	// ---- Alta ----

	@Test
	void crear_adminPropio_devuelve201() throws Exception {
		Club club = crearClub("Club A");
		String token = bearerAdminDe(club, "admin@vibora.com");

		mvc.perform(post("/api/clubes/" + club.getId() + "/pistas").header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON).content(pistaJson("Pista Central", "EXTERIOR")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.nombre").value("Pista Central"))
				.andExpect(jsonPath("$.tipo").value("EXTERIOR"))
				.andExpect(jsonPath("$.activa").value(true))
				.andExpect(jsonPath("$.clubId").value(club.getId()));

		assertThat(pistas.count()).isEqualTo(1);
	}

	@Test
	void crear_jugadorOAdminAjeno_devuelve403() throws Exception {
		Club a = crearClub("Club A");
		Club b = crearClub("Club B");
		String ajeno = bearerAdminDe(b, "adminb@vibora.com");
		String jugador = bearerJugador("p@vibora.com");

		for (String token : new String[] {jugador, ajeno}) {
			mvc.perform(post("/api/clubes/" + a.getId() + "/pistas").header("Authorization", token)
							.contentType(MediaType.APPLICATION_JSON).content(pistaJson("P", "INTERIOR")))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error").isString());
		}
		assertThat(pistas.count()).isZero();
	}

	@Test
	void crear_nombreDuplicadoEnElMismoClub_409_peroOkEnOtroClub() throws Exception {
		Club a = crearClub("Club A");
		Club b = crearClub("Club B");
		String tokenA = bearerAdminDe(a, "admina@vibora.com");
		String tokenB = bearerAdminDe(b, "adminb@vibora.com");
		crearPista(a, "Pista 1", true);

		mvc.perform(post("/api/clubes/" + a.getId() + "/pistas").header("Authorization", tokenA)
						.contentType(MediaType.APPLICATION_JSON).content(pistaJson("Pista 1", "INTERIOR")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").isString());

		mvc.perform(post("/api/clubes/" + b.getId() + "/pistas").header("Authorization", tokenB)
						.contentType(MediaType.APPLICATION_JSON).content(pistaJson("Pista 1", "INTERIOR")))
				.andExpect(status().isCreated());
	}

	@Test
	void crear_datosInvalidos_devuelve400() throws Exception {
		Club club = crearClub("Club A");
		String token = bearerAdminDe(club, "admin@vibora.com");
		String[] invalidos = {
				pistaJson("", "INTERIOR"),
				pistaJson("   ", "INTERIOR"),
				pistaJson("P", null),
				pistaJson(null, "INTERIOR"),
				pistaJson("P", "NO_EXISTE"),
		};
		for (String cuerpo : invalidos) {
			mvc.perform(post("/api/clubes/" + club.getId() + "/pistas").header("Authorization", token)
							.contentType(MediaType.APPLICATION_JSON).content(cuerpo))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error").isString());
		}
		assertThat(pistas.count()).isZero();
	}

	@Test
	void crear_clubInexistente_devuelve404() throws Exception {
		Club club = crearClub("Club A");
		String token = bearerAdminDe(club, "admin@vibora.com");

		mvc.perform(post("/api/clubes/999999/pistas").header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON).content(pistaJson("P", "INTERIOR")))
				.andExpect(status().isNotFound());
	}

	// ---- Modificacion ----

	@Test
	void actualizar_adminPropio_cambiaNombreTipoYActiva() throws Exception {
		Club club = crearClub("Club A");
		String token = bearerAdminDe(club, "admin@vibora.com");
		Pista p = crearPista(club, "Pista 1", true);

		mvc.perform(put("/api/clubes/" + club.getId() + "/pistas/" + p.getId()).header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON).content(pistaJson("Pista Nueva", "CUBIERTA", false)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("Pista Nueva"))
				.andExpect(jsonPath("$.tipo").value("CUBIERTA"))
				.andExpect(jsonPath("$.activa").value(false));

		Pista guardada = pistas.findById(p.getId()).orElseThrow();
		assertThat(guardada.getNombre()).isEqualTo("Pista Nueva");
		assertThat(guardada.getTipo()).isEqualTo(TipoPista.CUBIERTA);
		assertThat(guardada.isActiva()).isFalse();
	}

	@Test
	void actualizar_conservandoSuPropioNombre_noEsConflicto() throws Exception {
		Club club = crearClub("Club A");
		String token = bearerAdminDe(club, "admin@vibora.com");
		Pista p = crearPista(club, "Pista 1", true);

		mvc.perform(put("/api/clubes/" + club.getId() + "/pistas/" + p.getId()).header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON).content(pistaJson("Pista 1", "EXTERIOR", true)))
				.andExpect(status().isOk());
	}

	@Test
	void actualizar_pistaDeOtroClubEnLaRuta_devuelve404() throws Exception {
		Club a = crearClub("Club A");
		Club b = crearClub("Club B");
		String tokenA = bearerAdminDe(a, "admina@vibora.com");
		Pista deB = crearPista(b, "Pista B", true);

		mvc.perform(put("/api/clubes/" + a.getId() + "/pistas/" + deB.getId()).header("Authorization", tokenA)
						.contentType(MediaType.APPLICATION_JSON).content(pistaJson("Hack", "INTERIOR", true)))
				.andExpect(status().isNotFound());

		assertThat(pistas.findById(deB.getId()).orElseThrow().getNombre()).isEqualTo("Pista B");
	}

	@Test
	void actualizar_adminAjeno_devuelve403() throws Exception {
		Club a = crearClub("Club A");
		Club b = crearClub("Club B");
		String tokenB = bearerAdminDe(b, "adminb@vibora.com");
		Pista p = crearPista(a, "Pista 1", true);

		mvc.perform(put("/api/clubes/" + a.getId() + "/pistas/" + p.getId()).header("Authorization", tokenB)
						.contentType(MediaType.APPLICATION_JSON).content(pistaJson("Hack", "INTERIOR", true)))
				.andExpect(status().isForbidden());
	}

	@Test
	void actualizar_nombreDuplicado_devuelve409() throws Exception {
		Club club = crearClub("Club A");
		String token = bearerAdminDe(club, "admin@vibora.com");
		crearPista(club, "Pista 1", true);
		Pista p2 = crearPista(club, "Pista 2", true);

		mvc.perform(put("/api/clubes/" + club.getId() + "/pistas/" + p2.getId()).header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON).content(pistaJson("Pista 1", "INTERIOR", true)))
				.andExpect(status().isConflict());
	}

	@Test
	void actualizar_datosInvalidos_devuelve400() throws Exception {
		Club club = crearClub("Club A");
		String token = bearerAdminDe(club, "admin@vibora.com");
		Pista p = crearPista(club, "Pista 1", true);

		mvc.perform(put("/api/clubes/" + club.getId() + "/pistas/" + p.getId()).header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON).content(pistaJson("", "INTERIOR", true)))
				.andExpect(status().isBadRequest());
	}

	// ---- Baja (soft delete) ----

	@Test
	void eliminar_adminPropio_desactivaYEsIdempotente() throws Exception {
		Club club = crearClub("Club A");
		String token = bearerAdminDe(club, "admin@vibora.com");
		Pista p = crearPista(club, "Pista 1", true);
		String url = "/api/clubes/" + club.getId() + "/pistas/" + p.getId();

		mvc.perform(delete(url).header("Authorization", token)).andExpect(status().isNoContent());
		assertThat(pistas.findById(p.getId()).orElseThrow().isActiva()).isFalse();

		mvc.perform(delete(url).header("Authorization", token)).andExpect(status().isNoContent());
		assertThat(pistas.count()).isEqualTo(1);
		assertThat(pistas.findById(p.getId()).orElseThrow().isActiva()).isFalse();
	}

	@Test
	void eliminar_adminAjenoOJugador_devuelve403_yPistaDeOtroClub404() throws Exception {
		Club a = crearClub("Club A");
		Club b = crearClub("Club B");
		String tokenA = bearerAdminDe(a, "admina@vibora.com");
		String tokenB = bearerAdminDe(b, "adminb@vibora.com");
		String jugador = bearerJugador("p@vibora.com");
		Pista p = crearPista(a, "Pista 1", true);
		String url = "/api/clubes/" + a.getId() + "/pistas/" + p.getId();

		mvc.perform(delete(url).header("Authorization", tokenB)).andExpect(status().isForbidden());
		mvc.perform(delete(url).header("Authorization", jugador)).andExpect(status().isForbidden());
		assertThat(pistas.findById(p.getId()).orElseThrow().isActiva()).isTrue();

		mvc.perform(delete("/api/clubes/" + b.getId() + "/pistas/" + p.getId()).header("Authorization", tokenB))
				.andExpect(status().isNotFound());
		mvc.perform(delete(url).header("Authorization", tokenA)).andExpect(status().isNoContent());
	}
}
