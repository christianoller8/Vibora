package com.vibora.config;

import com.vibora.ApiTestBase;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Solo /actuator/health es publico (lo usan el HEALTHCHECK de Docker y el orquestador); el resto sigue protegido. */
class ActuatorHealthTests extends ApiTestBase {

	@Test
	void healthEsPublicoYDevuelveSoloElEstado() throws Exception {
		mvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(content().json("{\"status\":\"UP\"}", true));
	}

	@Test
	void healthNoFiltraDetallesNiComponentesAUnAnonimo() throws Exception {
		mvc.perform(get("/actuator/health"))
				.andExpect(jsonPath("$.components").doesNotExist())
				.andExpect(jsonPath("$.details").doesNotExist());
	}

	@Test
	void healthNoFiltraDetallesTampocoConUnaSesionValida() throws Exception {
		String token = bearerJugador("jugador-health@vibora.test");

		mvc.perform(get("/actuator/health").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(content().json("{\"status\":\"UP\"}", true));
	}

	@Test
	void subRutasDeHealthNoDevuelvenComponentesAUnAnonimo() throws Exception {
		mvc.perform(get("/actuator/health/db"))
				.andExpect(status().isNotFound());
	}

	@Test
	void otrosEndpointsDeActuatorNoSonAccesiblesSinToken() throws Exception {
		for (String ruta : new String[] { "/actuator", "/actuator/env", "/actuator/beans", "/actuator/info",
				"/actuator/metrics", "/actuator/heapdump", "/actuator/configprops" }) {
			mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
		}
	}

	@Test
	void otrosEndpointsDeActuatorNoEstanExpuestosNiSiquieraConToken() throws Exception {
		String token = bearerJugador("jugador-actuator@vibora.test");

		for (String ruta : new String[] { "/actuator/env", "/actuator/beans", "/actuator/info",
				"/actuator/metrics", "/actuator/heapdump", "/actuator/configprops" }) {
			mvc.perform(get(ruta).header("Authorization", token)).andExpect(status().isNotFound());
		}
	}

	@Test
	void healthSoloPorGet() throws Exception {
		mvc.perform(post("/actuator/health")).andExpect(status().isUnauthorized());
	}

	@Test
	void elRestoDeLaApiSigueExigiendoAutenticacion() throws Exception {
		mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/partidos")).andExpect(status().isUnauthorized());
	}
}
