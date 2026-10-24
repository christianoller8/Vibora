package com.vibora.factura;

import com.vibora.club.Club;
import com.vibora.usuario.Rol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DatosFiscalesTests extends FacturaApiTestBase {

	private static final String VALIDOS = """
			{"razonSocial":"Pádel Víbora S.L.","nif":"B12345674","direccionFiscal":"Calle Álvaro Cunqueiro 5, 36001 Pontevedra"}
			""";

	// ---- consultar ----

	@Test
	void unClubSinConfigurar_devuelveNulosElIvaPorDefecto_yConfiguradoFalso() throws Exception {
		verDatosFiscales(admin, club).andExpect(status().isOk())
				.andExpect(jsonPath("$.razonSocial").doesNotExist())
				.andExpect(jsonPath("$.nif").doesNotExist())
				.andExpect(jsonPath("$.direccionFiscal").doesNotExist())
				.andExpect(jsonPath("$.tipoIva").value(21.0))
				.andExpect(jsonPath("$.configurado").value(false));
	}

	// ---- configurar ----

	@Test
	void elAdministradorConfiguraLosDatos_yLosVuelveALeer() throws Exception {
		datosFiscales(admin, club, VALIDOS).andExpect(status().isOk())
				.andExpect(jsonPath("$.razonSocial").value("Pádel Víbora S.L."))
				.andExpect(jsonPath("$.nif").value("B12345674"))
				.andExpect(jsonPath("$.direccionFiscal").value("Calle Álvaro Cunqueiro 5, 36001 Pontevedra"))
				.andExpect(jsonPath("$.tipoIva").value(21.0))
				.andExpect(jsonPath("$.configurado").value(true));

		verDatosFiscales(admin, club).andExpect(status().isOk())
				.andExpect(jsonPath("$.razonSocial").value("Pádel Víbora S.L."))
				.andExpect(jsonPath("$.nif").value("B12345674"))
				.andExpect(jsonPath("$.configurado").value(true));
	}

	@Test
	void elNifSeGuardaNormalizado() throws Exception {
		datosFiscales(admin, club, """
				{"razonSocial":"Club","nif":" b-1234567 4 ","direccionFiscal":"Calle 1"}
				""").andExpect(status().isOk()).andExpect(jsonPath("$.nif").value("B12345674"));
	}

	@Test
	void conTipoIvaLoGuarda_ySinTipoIvaConservaElAnterior() throws Exception {
		datosFiscales(admin, club, """
				{"razonSocial":"Club","nif":"B12345674","direccionFiscal":"Calle 1","tipoIva":10.5}
				""").andExpect(status().isOk()).andExpect(jsonPath("$.tipoIva").value(10.5));

		datosFiscales(admin, club, VALIDOS).andExpect(status().isOk()).andExpect(jsonPath("$.tipoIva").value(10.5));
		verDatosFiscales(admin, club).andExpect(jsonPath("$.tipoIva").value(10.5));
	}

	@Test
	void elTipoIvaAdmiteCeroYElMaximoDeLaColumna() throws Exception {
		for (String tipo : new String[] { "0", "0.00", "99.99", "4", "21.00" }) {
			datosFiscales(admin, club, """
					{"razonSocial":"Club","nif":"B12345674","direccionFiscal":"Calle 1","tipoIva":%s}
					""".formatted(tipo)).andExpect(status().isOk());
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "-0.01", "100", "100.00", "250", "21.123", "\"abc\"" })
	void tiposDeIvaImposibles_400(String tipo) throws Exception {
		datosFiscales(admin, club, """
				{"razonSocial":"Club","nif":"B12345674","direccionFiscal":"Calle 1","tipoIva":%s}
				""".formatted(tipo)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
	}

	@ParameterizedTest
	@ValueSource(strings = { "12345678A", "B12345675", "A5881850A", "X1234567A", "ABC", "1234", "<script>", "B1234567 4 5" })
	void unNifMalFormadoOConControlEquivocado_400ConMensaje(String nif) throws Exception {
		datosFiscales(admin, club, """
				{"razonSocial":"Club","nif":"%s","direccionFiscal":"Calle 1"}
				""".formatted(nif)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("El NIF/CIF no es válido"));
	}

	@Test
	void aceptaDniNieYCifComoIdentificadorFiscal() throws Exception {
		for (String nif : new String[] { "12345678Z", "X1234567L", "A58818501", "Q2826000H" }) {
			datosFiscales(admin, club, """
					{"razonSocial":"Club","nif":"%s","direccionFiscal":"Calle 1"}
					""".formatted(nif)).andExpect(status().isOk()).andExpect(jsonPath("$.nif").value(nif));
		}
	}

	@Test
	void faltanDatosOSonDemasiadoLargos_400() throws Exception {
		String[] malos = {
				"{\"razonSocial\":\"\",\"nif\":\"B12345674\",\"direccionFiscal\":\"Calle 1\"}",
				"{\"razonSocial\":\"   \",\"nif\":\"B12345674\",\"direccionFiscal\":\"Calle 1\"}",
				"{\"nif\":\"B12345674\",\"direccionFiscal\":\"Calle 1\"}",
				"{\"razonSocial\":\"Club\",\"direccionFiscal\":\"Calle 1\"}",
				"{\"razonSocial\":\"Club\",\"nif\":\"B12345674\"}",
				"{\"razonSocial\":\"Club\",\"nif\":\"B12345674\",\"direccionFiscal\":\"\"}",
				"{\"razonSocial\":\"" + "x".repeat(201) + "\",\"nif\":\"B12345674\",\"direccionFiscal\":\"Calle 1\"}",
				"{\"razonSocial\":\"Club\",\"nif\":\"B12345674\",\"direccionFiscal\":\"" + "x".repeat(301) + "\"}",
				"{\"razonSocial\":\"Club\",\"nif\":\"" + "1".repeat(16) + "\",\"direccionFiscal\":\"Calle 1\"}",
				"{}", "no es json", ""
		};
		for (String malo : malos) {
			datosFiscales(admin, club, malo).andExpect(status().isBadRequest());
		}
		verDatosFiscales(admin, club).andExpect(jsonPath("$.configurado").value(false));
	}

	@Test
	void unaPeticionRechazadaNoModificaLoYaConfigurado() throws Exception {
		datosFiscales(admin, club, VALIDOS).andExpect(status().isOk());

		datosFiscales(admin, club, """
				{"razonSocial":"Otra","nif":"12345678A","direccionFiscal":"Calle 2"}
				""").andExpect(status().isBadRequest());

		verDatosFiscales(admin, club).andExpect(jsonPath("$.razonSocial").value("Pádel Víbora S.L."));
	}

	// ---- permisos ----

	@Test
	void soloElAdministradorDelClubPuedeLeerYEscribir() throws Exception {
		Club otro = crearClub("Club B");
		Jug adminOtro = administrador(otro, "admin-b@vibora.com");
		Jug jugador = ana("ana@vibora.com");
		Jug monitor = personal(club, "monitor@vibora.com", Rol.MONITOR);
		Jug mantenimiento = personal(club, "mant@vibora.com", Rol.MANTENIMIENTO);

		for (Jug intruso : new Jug[] { adminOtro, jugador, monitor, mantenimiento }) {
			datosFiscales(intruso, club, VALIDOS).andExpect(status().isForbidden());
			verDatosFiscales(intruso, club).andExpect(status().isForbidden());
		}
		verDatosFiscales(admin, club).andExpect(jsonPath("$.configurado").value(false));
	}

	@Test
	void sinTokenEs401() throws Exception {
		mvc.perform(put("/api/clubes/" + club.getId() + "/datos-fiscales").contentType("application/json").content(VALIDOS))
				.andExpect(status().isUnauthorized());
		mvc.perform(get("/api/clubes/" + club.getId() + "/datos-fiscales")).andExpect(status().isUnauthorized());
	}

	@Test
	void unClubInexistente_404() throws Exception {
		mvc.perform(put("/api/clubes/999999/datos-fiscales").header("Authorization", admin.token())
				.contentType("application/json").content(VALIDOS)).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error", is("Club no encontrado")));
		mvc.perform(get("/api/clubes/999999/datos-fiscales").header("Authorization", admin.token()))
				.andExpect(status().isNotFound());
	}
}
