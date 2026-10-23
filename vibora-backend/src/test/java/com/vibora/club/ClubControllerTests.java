package com.vibora.club;

import com.vibora.ApiTestBase;
import com.vibora.auth.JwtService;
import com.vibora.usuario.Rol;
import com.vibora.usuario.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClubControllerTests extends ApiTestBase {

	@Autowired JwtService jwtService;

	private static String clubJson(String nombre, String apertura, String cierre) {
		return """
				{"nombre":"%s","direccion":"Calle 1","ciudad":"Madrid","horaApertura":"%s","horaCierre":"%s"}
				""".formatted(nombre, apertura, cierre);
	}

	// ---- Lectura ----

	@Test
	void listar_comoJugador_devuelveLosClubes() throws Exception {
		crearClub("Club A");
		crearClub("Club B");

		mvc.perform(get("/api/clubes").header("Authorization", bearerJugador("p@vibora.com")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].id").isNumber())
				.andExpect(jsonPath("$[0].nombre").isString())
				.andExpect(jsonPath("$[0].horaApertura").value("08:00:00"));
	}

	@Test
	void obtener_comoJugador_ok_yInexistente404() throws Exception {
		Club club = crearClub("Club A");
		String token = bearerJugador("p@vibora.com");

		mvc.perform(get("/api/clubes/" + club.getId()).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(club.getId()))
				.andExpect(jsonPath("$.nombre").value("Club A"))
				.andExpect(jsonPath("$.ciudad").value("Madrid"));

		mvc.perform(get("/api/clubes/999999").header("Authorization", token))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").isString());
	}

	@Test
	void sinToken_devuelve401() throws Exception {
		mvc.perform(get("/api/clubes")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/clubes/1")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/clubes").contentType(MediaType.APPLICATION_JSON)
				.content(clubJson("X", "08:00", "22:00"))).andExpect(status().isUnauthorized());
	}

	// ---- Alta (onboarding) ----

	@Test
	void crear_jugadorSinClub_creaClubYSeConvierteEnAdmin() throws Exception {
		crearUsuario("nuevo@vibora.com", Rol.JUGADOR, null);
		String tokenViejo = bearer("nuevo@vibora.com");

		mvc.perform(post("/api/clubes").header("Authorization", tokenViejo)
						.contentType(MediaType.APPLICATION_JSON).content(clubJson("Mi Club", "09:00", "22:00")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.nombre").value("Mi Club"))
				.andExpect(jsonPath("$.horaCierre").value("22:00:00"));

		Usuario u = usuarios.findByEmail("nuevo@vibora.com").orElseThrow();
		assertThat(u.getRol()).isEqualTo(Rol.ADMIN_CLUB);
		assertThat(u.getClub()).isNotNull();
		assertThat(u.getClub().getNombre()).isEqualTo("Mi Club");
		assertThat(clubs.count()).isEqualTo(1);

		// El token anterior sigue diciendo JUGADOR: hay que volver a iniciar sesion
		assertThat(jwtService.decodificar(tokenViejo.substring(7)).getClaimAsString("rol")).isEqualTo("JUGADOR");
		String tokenNuevo = tokenDe("nuevo@vibora.com");
		assertThat(jwtService.decodificar(tokenNuevo).getClaimAsString("rol")).isEqualTo("ADMIN_CLUB");
	}

	@Test
	void crear_personalConClub_devuelve409YNoCreaNada() throws Exception {
		Club existente = crearClub("Existente");
		String token = bearerAdminDe(existente, "admin@vibora.com");

		mvc.perform(post("/api/clubes").header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON).content(clubJson("Otro", "09:00", "22:00")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").isString());

		assertThat(clubs.count()).isEqualTo(1);
	}

	@Test
	void crear_datosInvalidos_devuelve400() throws Exception {
		String token = bearerJugador("p@vibora.com");
		String[] invalidos = {
				clubJson("", "09:00", "22:00"),
				clubJson("   ", "09:00", "22:00"),
				clubJson("X", "22:00", "09:00"),
				clubJson("X", "09:00", "09:00"),
				"{\"nombre\":\"X\",\"horaApertura\":\"09:00\"}",
		};
		for (String cuerpo : invalidos) {
			mvc.perform(post("/api/clubes").header("Authorization", token)
							.contentType(MediaType.APPLICATION_JSON).content(cuerpo))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error").isString());
		}
		assertThat(clubs.count()).isZero();
		assertThat(usuarios.findByEmail("p@vibora.com").orElseThrow().getRol()).isEqualTo(Rol.JUGADOR);
	}

	// ---- Modificacion ----

	@Test
	void actualizar_adminDelClub_ok() throws Exception {
		Club club = crearClub("Club A");
		String token = bearerAdminDe(club, "admin@vibora.com");

		mvc.perform(put("/api/clubes/" + club.getId()).header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON).content(clubJson("Renombrado", "07:30", "23:30")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("Renombrado"))
				.andExpect(jsonPath("$.horaApertura").value("07:30:00"));

		assertThat(clubs.findById(club.getId()).orElseThrow().getNombre()).isEqualTo("Renombrado");
	}

	@Test
	void actualizar_adminDeOtroClub_devuelve403() throws Exception {
		Club a = crearClub("Club A");
		Club b = crearClub("Club B");
		String tokenB = bearerAdminDe(b, "adminb@vibora.com");

		mvc.perform(put("/api/clubes/" + a.getId()).header("Authorization", tokenB)
						.contentType(MediaType.APPLICATION_JSON).content(clubJson("Hack", "08:00", "23:00")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").isString());

		assertThat(clubs.findById(a.getId()).orElseThrow().getNombre()).isEqualTo("Club A");
	}

	@Test
	void actualizar_jugador_devuelve403() throws Exception {
		Club club = crearClub("Club A");

		mvc.perform(put("/api/clubes/" + club.getId()).header("Authorization", bearerJugador("p@vibora.com"))
						.contentType(MediaType.APPLICATION_JSON).content(clubJson("Hack", "08:00", "23:00")))
				.andExpect(status().isForbidden());
	}

	@Test
	void actualizar_clubInexistente_devuelve404() throws Exception {
		Club club = crearClub("Club A");
		String token = bearerAdminDe(club, "admin@vibora.com");

		mvc.perform(put("/api/clubes/999999").header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON).content(clubJson("X", "08:00", "23:00")))
				.andExpect(status().isNotFound());
	}

	@Test
	void actualizar_datosInvalidos_devuelve400() throws Exception {
		Club club = crearClub("Club A");
		String token = bearerAdminDe(club, "admin@vibora.com");

		mvc.perform(put("/api/clubes/" + club.getId()).header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON).content(clubJson("X", "23:00", "08:00")))
				.andExpect(status().isBadRequest());
		mvc.perform(put("/api/clubes/" + club.getId()).header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON).content(clubJson("", "08:00", "23:00")))
				.andExpect(status().isBadRequest());
	}
}
