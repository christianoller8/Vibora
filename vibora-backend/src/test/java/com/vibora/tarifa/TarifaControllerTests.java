package com.vibora.tarifa;

import com.vibora.ApiTestBase;
import com.vibora.club.Club;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TarifaControllerTests extends ApiTestBase {

	@Autowired TarifaFranjaRepository tarifas;

	private static String json(String dia, String ini, String fin, String precio, String tipo) {
		return """
				{"diaSemana":%s,"horaInicio":%s,"horaFin":%s,"precioHora":%s,"tipo":%s}
				""".formatted(q(dia), q(ini), q(fin), precio, q(tipo));
	}

	private static String q(String s) {
		return s == null ? "null" : "\"" + s + "\"";
	}

	private static String lunes(String ini, String fin) {
		return json("MONDAY", ini, fin, "12.50", "NORMAL");
	}

	private TarifaFranja guardar(Club club, DayOfWeek dia, int horaIni, int horaFin, String precio) {
		TarifaFranja t = new TarifaFranja();
		t.setClub(club);
		t.setDiaSemana(dia);
		t.setHoraInicio(LocalTime.of(horaIni, 0));
		t.setHoraFin(LocalTime.of(horaFin, 0));
		t.setPrecioHora(new BigDecimal(precio));
		t.setTipo(TipoTarifa.NORMAL);
		return tarifas.saveAndFlush(t);
	}

	private ResultActions crear(Club club, String token, String body) throws Exception {
		return mvc.perform(post("/api/clubes/" + club.getId() + "/tarifas").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	// ---- Listado ----

	@Test
	void listar_cualquierAutenticado_veSoloLasDelClub_ordenadas() throws Exception {
		Club a = crearClub("Club A");
		Club b = crearClub("Club B");
		guardar(a, DayOfWeek.TUESDAY, 8, 12, "10.00");
		guardar(a, DayOfWeek.MONDAY, 14, 18, "11.00");
		guardar(a, DayOfWeek.MONDAY, 8, 14, "12.00");
		guardar(b, DayOfWeek.MONDAY, 8, 14, "99.00");
		String jugador = bearerJugador("p@vibora.com");

		mvc.perform(get("/api/clubes/" + a.getId() + "/tarifas").header("Authorization", jugador))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].diaSemana").value("MONDAY"))
				.andExpect(jsonPath("$[0].horaInicio").value("08:00:00"))
				.andExpect(jsonPath("$[0].horaFin").value("14:00:00"))
				.andExpect(jsonPath("$[0].precioHora").value(12.0))
				.andExpect(jsonPath("$[0].tipo").value("NORMAL"))
				.andExpect(jsonPath("$[0].clubId").value(a.getId()))
				.andExpect(jsonPath("$[1].horaInicio").value("14:00:00"))
				.andExpect(jsonPath("$[2].diaSemana").value("TUESDAY"));
	}

	@Test
	void listar_clubInexistente404_ySinToken401() throws Exception {
		mvc.perform(get("/api/clubes/999999/tarifas").header("Authorization", bearerJugador("p@vibora.com")))
				.andExpect(status().isNotFound());
		mvc.perform(get("/api/clubes/1/tarifas")).andExpect(status().isUnauthorized());
	}

	// ---- Alta ----

	@Test
	void crear_adminPropio_devuelve201YPersiste() throws Exception {
		Club club = crearClub("Club A");
		String token = bearerAdminDe(club, "admin@vibora.com");

		crear(club, token, json("FRIDAY", "18:00", "22:00", "24.5", "HORA_PUNTA"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.clubId").value(club.getId()))
				.andExpect(jsonPath("$.diaSemana").value("FRIDAY"))
				.andExpect(jsonPath("$.horaInicio").value("18:00:00"))
				.andExpect(jsonPath("$.horaFin").value("22:00:00"))
				.andExpect(jsonPath("$.precioHora").value(24.5))
				.andExpect(jsonPath("$.tipo").value("HORA_PUNTA"));

		assertThat(tarifas.findAll()).singleElement().satisfies(t -> {
			assertThat(t.getDiaSemana()).isEqualTo(DayOfWeek.FRIDAY);
			assertThat(t.getPrecioHora()).isEqualByComparingTo("24.50");
		});
	}

	@Test
	void crear_adminDeOtroClub_jugador_yAnonimo_noPueden() throws Exception {
		Club a = crearClub("Club A");
		Club b = crearClub("Club B");
		String ajeno = bearerAdminDe(b, "adminb@vibora.com");
		String jugador = bearerJugador("p@vibora.com");

		crear(a, ajeno, lunes("08:00", "12:00")).andExpect(status().isForbidden());
		crear(a, jugador, lunes("08:00", "12:00")).andExpect(status().isForbidden());
		mvc.perform(post("/api/clubes/" + a.getId() + "/tarifas").contentType(MediaType.APPLICATION_JSON)
				.content(lunes("08:00", "12:00"))).andExpect(status().isUnauthorized());
		assertThat(tarifas.count()).isZero();
	}

	@Test
	void crear_clubInexistente_404() throws Exception {
		Club club = crearClub("Club A");
		String token = bearerAdminDe(club, "admin@vibora.com");

		mvc.perform(post("/api/clubes/999999/tarifas").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content(lunes("08:00", "12:00")))
				.andExpect(status().isNotFound());
	}

	@Test
	void crear_datosInvalidos_400() throws Exception {
		Club club = crearClub("Club A");
		String token = bearerAdminDe(club, "admin@vibora.com");

		String[] invalidos = {
				lunes("12:00", "12:00"), // fin == inicio
				lunes("12:00", "08:00"), // fin < inicio
				json("MONDAY", "08:00", "12:00", "-0.01", "NORMAL"), // precio negativo
				json("MONDAY", "08:00", "12:00", "null", "NORMAL"), // sin precio
				json(null, "08:00", "12:00", "10", "NORMAL"), // sin dia
				json("MONDAY", null, "12:00", "10", "NORMAL"), // sin horaInicio
				json("MONDAY", "08:00", null, "10", "NORMAL"), // sin horaFin
				json("MONDAY", "08:00", "12:00", "10", null), // sin tipo
				json("FUNDAY", "08:00", "12:00", "10", "NORMAL"), // dia desconocido
				json("MONDAY", "08:00", "12:00", "10", "GRATIS"), // tipo desconocido
				json("MONDAY", "ocho", "12:00", "10", "NORMAL"), // hora ilegible
				"{}",
		};
		for (String body : invalidos) {
			crear(club, token, body).andExpect(status().isBadRequest());
		}
		assertThat(tarifas.count()).isZero();
	}

	@Test
	void crear_solapadaMismoClubYDia_409() throws Exception {
		Club club = crearClub("Club A");
		String token = bearerAdminDe(club, "admin@vibora.com");
		guardar(club, DayOfWeek.MONDAY, 8, 14, "12.00");

		crear(club, token, lunes("12:00", "16:00")).andExpect(status().isConflict()); // pisa el final
		crear(club, token, lunes("06:00", "09:00")).andExpect(status().isConflict()); // pisa el inicio
		crear(club, token, lunes("09:00", "10:00")).andExpect(status().isConflict()); // contenida
		crear(club, token, lunes("07:00", "15:00")).andExpect(status().isConflict()); // contiene
		assertThat(tarifas.count()).isEqualTo(1);
	}

	@Test
	void crear_contiguaOtroDiaOOtroClub_noSolapa() throws Exception {
		Club a = crearClub("Club A");
		Club b = crearClub("Club B");
		String token = bearerAdminDe(a, "admin@vibora.com");
		guardar(a, DayOfWeek.MONDAY, 8, 14, "12.00");
		guardar(b, DayOfWeek.MONDAY, 14, 18, "12.00");

		crear(a, token, lunes("14:00", "18:00")).andExpect(status().isCreated()); // contigua (semiabierta)
		crear(a, token, json("TUESDAY", "08:00", "14:00", "12", "NORMAL")).andExpect(status().isCreated());
		assertThat(tarifas.count()).isEqualTo(4);
	}

	// ---- Modificacion ----

	@Test
	void actualizar_adminPropio_devuelve200_yPuedeConservarSuPropioRango() throws Exception {
		Club club = crearClub("Club A");
		String token = bearerAdminDe(club, "admin@vibora.com");
		TarifaFranja t = guardar(club, DayOfWeek.MONDAY, 8, 14, "12.00");

		// mismo rango, otro precio: no debe chocar consigo misma
		mvc.perform(put("/api/clubes/" + club.getId() + "/tarifas/" + t.getId()).header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json("MONDAY", "08:00", "14:00", "15", "HORA_PUNTA")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(t.getId()))
				.andExpect(jsonPath("$.precioHora").value(15.0))
				.andExpect(jsonPath("$.tipo").value("HORA_PUNTA"));

		mvc.perform(put("/api/clubes/" + club.getId() + "/tarifas/" + t.getId()).header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json("WEDNESDAY", "10:00", "11:00", "15", "NORMAL")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.diaSemana").value("WEDNESDAY"))
				.andExpect(jsonPath("$.horaInicio").value("10:00:00"));
	}

	@Test
	void actualizar_solapandoConOtra_409_yDatosInvalidos_400() throws Exception {
		Club club = crearClub("Club A");
		String token = bearerAdminDe(club, "admin@vibora.com");
		TarifaFranja a = guardar(club, DayOfWeek.MONDAY, 8, 14, "12.00");
		guardar(club, DayOfWeek.MONDAY, 14, 18, "12.00");

		mvc.perform(put("/api/clubes/" + club.getId() + "/tarifas/" + a.getId()).header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON).content(lunes("08:00", "15:00")))
				.andExpect(status().isConflict());
		mvc.perform(put("/api/clubes/" + club.getId() + "/tarifas/" + a.getId()).header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON).content(lunes("10:00", "09:00")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void actualizar_permisosYNoEncontrado() throws Exception {
		Club a = crearClub("Club A");
		Club b = crearClub("Club B");
		String propio = bearerAdminDe(a, "admina@vibora.com");
		String ajeno = bearerAdminDe(b, "adminb@vibora.com");
		String jugador = bearerJugador("p@vibora.com");
		TarifaFranja deA = guardar(a, DayOfWeek.MONDAY, 8, 14, "12.00");
		TarifaFranja deB = guardar(b, DayOfWeek.MONDAY, 8, 14, "12.00");
		String ruta = "/api/clubes/" + a.getId() + "/tarifas/";

		mvc.perform(put(ruta + deA.getId()).header("Authorization", ajeno)
				.contentType(MediaType.APPLICATION_JSON).content(lunes("08:00", "14:00"))).andExpect(status().isForbidden());
		mvc.perform(put(ruta + deA.getId()).header("Authorization", jugador)
				.contentType(MediaType.APPLICATION_JSON).content(lunes("08:00", "14:00"))).andExpect(status().isForbidden());
		// tarifa de otro club bajo la ruta de este club, e inexistente
		mvc.perform(put(ruta + deB.getId()).header("Authorization", propio)
				.contentType(MediaType.APPLICATION_JSON).content(lunes("08:00", "14:00"))).andExpect(status().isNotFound());
		mvc.perform(put(ruta + 999999).header("Authorization", propio)
				.contentType(MediaType.APPLICATION_JSON).content(lunes("08:00", "14:00"))).andExpect(status().isNotFound());
		mvc.perform(put("/api/clubes/999999/tarifas/" + deA.getId()).header("Authorization", propio)
				.contentType(MediaType.APPLICATION_JSON).content(lunes("08:00", "14:00"))).andExpect(status().isNotFound());
	}

	// ---- Baja ----

	@Test
	void eliminar_adminPropio_204_yDesapareceDeLaBd() throws Exception {
		Club club = crearClub("Club A");
		String token = bearerAdminDe(club, "admin@vibora.com");
		TarifaFranja t = guardar(club, DayOfWeek.MONDAY, 8, 14, "12.00");

		mvc.perform(delete("/api/clubes/" + club.getId() + "/tarifas/" + t.getId()).header("Authorization", token))
				.andExpect(status().isNoContent());

		assertThat(tarifas.existsById(t.getId())).isFalse();
		mvc.perform(delete("/api/clubes/" + club.getId() + "/tarifas/" + t.getId()).header("Authorization", token))
				.andExpect(status().isNotFound());
	}

	@Test
	void eliminar_permisosYNoEncontrado() throws Exception {
		Club a = crearClub("Club A");
		Club b = crearClub("Club B");
		String propio = bearerAdminDe(a, "admina@vibora.com");
		String ajeno = bearerAdminDe(b, "adminb@vibora.com");
		String jugador = bearerJugador("p@vibora.com");
		TarifaFranja deA = guardar(a, DayOfWeek.MONDAY, 8, 14, "12.00");
		TarifaFranja deB = guardar(b, DayOfWeek.MONDAY, 8, 14, "12.00");
		String ruta = "/api/clubes/" + a.getId() + "/tarifas/";

		mvc.perform(delete(ruta + deA.getId()).header("Authorization", ajeno)).andExpect(status().isForbidden());
		mvc.perform(delete(ruta + deA.getId()).header("Authorization", jugador)).andExpect(status().isForbidden());
		mvc.perform(delete(ruta + deB.getId()).header("Authorization", propio)).andExpect(status().isNotFound());
		mvc.perform(delete(ruta + deA.getId())).andExpect(status().isUnauthorized());
		assertThat(tarifas.count()).isEqualTo(2);
	}
}
