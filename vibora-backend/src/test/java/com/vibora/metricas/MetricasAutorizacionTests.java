package com.vibora.metricas;

import com.vibora.club.Club;
import com.vibora.usuario.Rol;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Solo el administrador DEL club ve las metricas del club: la matriz completa en los cinco endpoints. */
class MetricasAutorizacionTests extends MetricasApiTestBase {

	@ParameterizedTest
	@ValueSource(strings = { "resumen", "ingresos", "ocupacion", "franjas", "cancelaciones" })
	void elAdministradorDelClubPuede(String recurso) throws Exception {
		comoAdmin(recurso).andExpect(status().isOk());
	}

	@ParameterizedTest
	@ValueSource(strings = { "resumen", "ingresos", "ocupacion", "franjas", "cancelaciones" })
	void elAdministradorDeOtroClubNoPuede(String recurso) throws Exception {
		Club otro = crearClub("Club B");
		String adminB = bearerAdminDe(otro, "admin@b.com");

		pedir(adminB, club, recurso).andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("Acceso denegado"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "resumen", "ingresos", "ocupacion", "franjas", "cancelaciones" })
	void unJugadorNoPuede(String recurso) throws Exception {
		String jugador = bearerJugador("jugador@x.com");

		pedir(jugador, club, recurso).andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("Acceso denegado"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "resumen", "ingresos", "ocupacion", "franjas", "cancelaciones" })
	void elPersonalDelClubQueNoEsAdministradorNoPuede(String recurso) throws Exception {
		crearUsuario("monitor@a.com", Rol.MONITOR, club);
		crearUsuario("mantenimiento@a.com", Rol.MANTENIMIENTO, club);

		pedir(bearer("monitor@a.com"), club, recurso).andExpect(status().isForbidden());
		pedir(bearer("mantenimiento@a.com"), club, recurso).andExpect(status().isForbidden());
	}

	@ParameterizedTest
	@ValueSource(strings = { "resumen", "ingresos", "ocupacion", "franjas", "cancelaciones" })
	void sinTokenEs401(String recurso) throws Exception {
		pedir(null, club, recurso).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").exists());
	}

	@ParameterizedTest
	@ValueSource(strings = { "resumen", "ingresos", "ocupacion", "franjas", "cancelaciones" })
	void conTokenInvalidoEs401(String recurso) throws Exception {
		pedir("Bearer esto.no.es.un.jwt", club, recurso).andExpect(status().isUnauthorized());
	}

	@ParameterizedTest
	@ValueSource(strings = { "resumen", "ingresos", "ocupacion", "franjas", "cancelaciones" })
	void unClubQueNoExisteEs404(String recurso) throws Exception {
		pedir(admin, 987654321L, recurso).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Club no encontrado"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "resumen", "ingresos", "ocupacion", "franjas", "cancelaciones" })
	void laAutorizacionVaAntesQueLaValidacionDeParametros(String recurso) throws Exception {
		String jugador = bearerJugador("jugador@x.com");

		pedir(jugador, club, recurso, "desde", "2030-02-01", "hasta", "2030-01-01").andExpect(status().isForbidden());
	}
}
