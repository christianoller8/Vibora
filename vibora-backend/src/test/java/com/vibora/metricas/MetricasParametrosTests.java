package com.vibora.metricas;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Validacion de los parametros comunes (desde, hasta, granularidad, limit) en los cinco endpoints. */
class MetricasParametrosTests extends MetricasApiTestBase {

	@ParameterizedTest
	@ValueSource(strings = { "resumen", "ingresos", "ocupacion", "franjas", "cancelaciones" })
	void sinParametrosSonLosUltimos30DiasTerminandoHoyEnMadrid(String recurso) throws Exception {
		// reloj: 2030-01-01T00:00Z = 2030-01-01 01:00 en Madrid
		comoAdmin(recurso).andExpect(status().isOk()).andExpect(jsonPath("$.desde").value("2029-12-03"))
				.andExpect(jsonPath("$.hasta").value("2030-01-01"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "resumen", "ingresos", "ocupacion", "franjas", "cancelaciones" })
	void desdePosteriorAHastaEs400(String recurso) throws Exception {
		comoAdmin(recurso, "desde", "2030-01-10", "hasta", "2030-01-09").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("La fecha inicial no puede ser posterior a la final"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "resumen", "ingresos", "ocupacion", "franjas", "cancelaciones" })
	void masDe366DiasEs400YExactamente366Es200(String recurso) throws Exception {
		comoAdmin(recurso, "desde", "2029-01-01", "hasta", "2030-01-02").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("El rango no puede superar los 366 días"));
		comoAdmin(recurso, "desde", "2029-01-01", "hasta", "2030-01-01").andExpect(status().isOk());
	}

	@ParameterizedTest
	@ValueSource(strings = { "resumen", "ingresos", "ocupacion", "franjas", "cancelaciones" })
	void fechasMalFormadasSon400(String recurso) throws Exception {
		comoAdmin(recurso, "desde", "ayer").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Datos no válidos"));
		comoAdmin(recurso, "hasta", "2030-13-01").andExpect(status().isBadRequest());
		comoAdmin(recurso, "desde", "2030-02-30").andExpect(status().isBadRequest());
		comoAdmin(recurso, "desde", "01/02/2030").andExpect(status().isBadRequest());
	}

	@ParameterizedTest
	@ValueSource(strings = { "resumen", "ingresos", "ocupacion", "franjas", "cancelaciones" })
	void fechasFueraDeLosLimitesAbsolutosSon400_noUn500(String recurso) throws Exception {
		String mensaje = "Las fechas deben estar entre 1970 y 2100";
		comoAdmin(recurso, "hasta", "+999999999-12-31").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value(mensaje));
		comoAdmin(recurso, "desde", "+999999999-12-31", "hasta", "+999999999-12-31").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value(mensaje));
		comoAdmin(recurso, "desde", "0001-01-01").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value(mensaje));
		comoAdmin(recurso, "desde", "+500000-01-01", "hasta", "+500000-01-02").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value(mensaje));
		comoAdmin(recurso, "desde", "1969-12-31", "hasta", "1970-01-02").andExpect(status().isBadRequest());
		comoAdmin(recurso, "desde", "2100-12-31", "hasta", "2101-01-01").andExpect(status().isBadRequest());
	}

	@ParameterizedTest
	@ValueSource(strings = { "resumen", "ingresos", "ocupacion", "franjas", "cancelaciones" })
	void lasFechasEnLosExtremosAbsolutosSon200(String recurso) throws Exception {
		comoAdmin(recurso, "desde", "1970-01-01", "hasta", "1970-01-01").andExpect(status().isOk())
				.andExpect(jsonPath("$.desde").value("1970-01-01"));
		comoAdmin(recurso, "desde", "2100-12-31", "hasta", "2100-12-31").andExpect(status().isOk())
				.andExpect(jsonPath("$.hasta").value("2100-12-31"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "ingresos", "cancelaciones" })
	void laGranularidadPorDefectoEsDiaYSoloSeAceptanLosTresValores(String recurso) throws Exception {
		comoAdmin(recurso).andExpect(status().isOk()).andExpect(jsonPath("$.granularidad").value("DIA"));
		comoAdmin(recurso, "granularidad", "SEMANA").andExpect(status().isOk()).andExpect(jsonPath("$.granularidad").value("SEMANA"));
		comoAdmin(recurso, "granularidad", "MES").andExpect(status().isOk()).andExpect(jsonPath("$.granularidad").value("MES"));
		comoAdmin(recurso, "granularidad", "ANIO").andExpect(status().isBadRequest());
		comoAdmin(recurso, "granularidad", "dia").andExpect(status().isBadRequest());
	}

	@Test
	void elLimiteDeFranjasEstaEntre1y50() throws Exception {
		comoAdmin("franjas").andExpect(status().isOk()).andExpect(jsonPath("$.limit").value(10));
		comoAdmin("franjas", "limit", "1").andExpect(status().isOk()).andExpect(jsonPath("$.limit").value(1));
		comoAdmin("franjas", "limit", "50").andExpect(status().isOk()).andExpect(jsonPath("$.limit").value(50));
		comoAdmin("franjas", "limit", "0").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("El límite debe estar entre 1 y 50"));
		comoAdmin("franjas", "limit", "51").andExpect(status().isBadRequest());
		comoAdmin("franjas", "limit", "-3").andExpect(status().isBadRequest());
		comoAdmin("franjas", "limit", "muchas").andExpect(status().isBadRequest());
	}

	@Test
	void lasDemasMetricasIgnoranParametrosQueNoSonSuyos() throws Exception {
		comoAdmin("resumen", "granularidad", "MES", "limit", "999").andExpect(status().isOk());
	}
}
