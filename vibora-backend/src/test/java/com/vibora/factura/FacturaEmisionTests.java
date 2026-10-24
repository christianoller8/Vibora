package com.vibora.factura;

import com.vibora.club.Club;
import com.vibora.usuario.Rol;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FacturaEmisionTests extends FacturaApiTestBase {

	private static final String SIN_DATOS_FISCALES = "El club no ha configurado sus datos fiscales";

	// ---- caso feliz ----

	@Test
	void elOrganizadorFacturaSuReserva_201ConLosDatosCopiadosYElDesgloseDelIva() throws Exception {
		configurarDatosFiscales(admin, club);
		Jug ana = ana("ana@vibora.com");
		long reserva = reservaEnElLunes(ana, 11, 0);

		facturar(ana, reserva).andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.serie").value("F2030"))
				.andExpect(jsonPath("$.numero").value(1))
				.andExpect(jsonPath("$.numeroCompleto").value("F2030-0001"))
				.andExpect(jsonPath("$.tipo").value("ORIGINAL"))
				.andExpect(jsonPath("$.rectificaA").doesNotExist())
				.andExpect(jsonPath("$.reservaId").value(reserva))
				.andExpect(jsonPath("$.emitidaEn").value("2030-01-01T00:00:00Z"))
				.andExpect(jsonPath("$.emisor.razonSocial").value("Pádel Víbora S.L."))
				.andExpect(jsonPath("$.emisor.nif").value("B12345674"))
				.andExpect(jsonPath("$.emisor.direccion").value("Calle Álvaro Cunqueiro 5, 36001 Pontevedra"))
				.andExpect(jsonPath("$.receptor.nombre").value("Ana García"))
				.andExpect(jsonPath("$.receptor.nif").doesNotExist())
				.andExpect(jsonPath("$.receptor.direccion").doesNotExist())
				.andExpect(jsonPath("$.concepto").value(containsString("Pista 1 de Club A")))
				.andExpect(jsonPath("$.concepto").value(containsString("07/01/2030 a las 11:00")))
				.andExpect(jsonPath("$.base").value(37.19))
				.andExpect(jsonPath("$.tipoIva").value(21.0))
				.andExpect(jsonPath("$.cuotaIva").value(7.81))
				.andExpect(jsonPath("$.total").value(45.0));
		assertThat(contarFacturas()).isEqualTo(1);
		assertThat(contador(club, 2030)).isEqualTo(1);
	}

	@Test
	void laRespuestaNuncaLlevaEmailsNiOtrosDatosPrivados() throws Exception {
		configurarDatosFiscales(admin, club);
		Jug ana = ana("ana@vibora.com");
		long reserva = reservaEnElLunes(ana, 11, 0);

		String cuerpo = facturar(ana, reserva).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		assertThat(cuerpo).doesNotContain("@").doesNotContain("password").doesNotContain("email");
	}

	@Test
	void elIvaDelClubSeAplica_yLaFacturaRecuerdaElTipoDeEseMomento() throws Exception {
		datosFiscales(admin, club, """
				{"razonSocial":"Club","nif":"B12345674","direccionFiscal":"Calle 1","tipoIva":10}
				""").andExpect(status().isOk());
		Jug ana = ana("ana@vibora.com");
		long reserva = reservaEnElLunes(ana, 11, 0);

		facturar(ana, reserva).andExpect(status().isCreated())
				.andExpect(jsonPath("$.base").value(40.91)) // 45 / 1.10 = 40.909...
				.andExpect(jsonPath("$.tipoIva").value(10.0))
				.andExpect(jsonPath("$.cuotaIva").value(4.09))
				.andExpect(jsonPath("$.total").value(45.0));
	}

	@Test
	void elAdministradorDelClubFacturaEnNombreDelOrganizador() throws Exception {
		configurarDatosFiscales(admin, club);
		Jug ana = ana("ana@vibora.com");
		long reserva = reservaEnElLunes(ana, 11, 0);

		facturar(admin, reserva).andExpect(status().isCreated())
				.andExpect(jsonPath("$.receptor.nombre").value("Ana García")) // el receptor es el organizador, no el admin
				.andExpect(jsonPath("$.numeroCompleto").value("F2030-0001"));

		// y la factura es de Ana: la ve en sus facturas
		misFacturas(ana).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void sePuedeFacturarUnaReservaYaJugada() throws Exception {
		configurarDatosFiscales(admin, club);
		Jug ana = ana("ana@vibora.com");
		long reserva = reservaEnElLunes(ana, 11, 0);
		reloj.fijar(lunes(20, 0)); // la reserva ya ha terminado

		facturar(ana, reserva).andExpect(status().isCreated()).andExpect(jsonPath("$.total").value(45.0));
	}

	// ---- idempotencia ----

	@Test
	void pedirlaDeNuevoDevuelve200ConLaMismaFactura_sinGastarOtroNumero() throws Exception {
		configurarDatosFiscales(admin, club);
		Jug ana = ana("ana@vibora.com");
		long reserva = reservaEnElLunes(ana, 11, 0);
		long primera = idDe(facturar(ana, reserva).andExpect(status().isCreated()));

		facturar(ana, reserva).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(primera))
				.andExpect(jsonPath("$.numeroCompleto").value("F2030-0001"));
		facturar(admin, reserva).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(primera));

		assertThat(contarFacturas()).isEqualTo(1);
		assertThat(contador(club, 2030)).isEqualTo(1);
	}

	@Test
	void volverAPedirlaConOtroReceptorNoCambiaLaFacturaYaEmitida() throws Exception {
		configurarDatosFiscales(admin, club);
		Jug ana = ana("ana@vibora.com");
		long reserva = reservaEnElLunes(ana, 11, 0);
		facturar(ana, reserva).andExpect(status().isCreated()).andExpect(jsonPath("$.receptor.nombre").value("Ana García"));

		facturar(ana, reserva, """
				{"nombre":"Otra Persona S.L.","nif":"B12345674"}
				""").andExpect(status().isOk()).andExpect(jsonPath("$.receptor.nombre").value("Ana García"))
				.andExpect(jsonPath("$.receptor.nif").doesNotExist());
	}

	// ---- receptor ----

	@Test
	void elReceptorPuedeSustituirNombreNifYDireccion_yElNifSeNormaliza() throws Exception {
		configurarDatosFiscales(admin, club);
		Jug ana = ana("ana@vibora.com");
		long reserva = reservaEnElLunes(ana, 11, 0);

		facturar(ana, reserva, """
				{"nombre":"  Construcciones Peña S.L. ","nif":"b-12345674","direccion":"Rúa do Vilar 7, Santiago"}
				""").andExpect(status().isCreated())
				.andExpect(jsonPath("$.receptor.nombre").value("Construcciones Peña S.L."))
				.andExpect(jsonPath("$.receptor.nif").value("B12345674"))
				.andExpect(jsonPath("$.receptor.direccion").value("Rúa do Vilar 7, Santiago"));
	}

	@Test
	void conSoloElNifElNombreSigueSiendoElDelOrganizador() throws Exception {
		configurarDatosFiscales(admin, club);
		Jug ana = ana("ana@vibora.com");
		long reserva = reservaEnElLunes(ana, 11, 0);

		facturar(ana, reserva, """
				{"nif":"12345678Z"}
				""").andExpect(status().isCreated()).andExpect(jsonPath("$.receptor.nombre").value("Ana García"))
				.andExpect(jsonPath("$.receptor.nif").value("12345678Z"));
	}

	@Test
	void campoEnBlancoEquivaleANoPonerlo_yUnCuerpoVacioTambienVale() throws Exception {
		configurarDatosFiscales(admin, club);
		Jug ana = ana("ana@vibora.com");
		long primera = reservaEnElLunes(ana, 8, 0);
		long segunda = reservaEnElLunes(ana, 9, 30);

		facturar(ana, primera, """
				{"nombre":"   ","nif":"","direccion":" "}
				""").andExpect(status().isCreated()).andExpect(jsonPath("$.receptor.nombre").value("Ana García"))
				.andExpect(jsonPath("$.receptor.nif").doesNotExist()).andExpect(jsonPath("$.receptor.direccion").doesNotExist());
		facturar(ana, segunda, "{}").andExpect(status().isCreated()).andExpect(jsonPath("$.receptor.nombre").value("Ana García"));
	}

	@Test
	void unReceptorInvalido_400_ynoSeGastaNumeroNiSeCreaNada() throws Exception {
		configurarDatosFiscales(admin, club);
		Jug ana = ana("ana@vibora.com");
		long reserva = reservaEnElLunes(ana, 11, 0);

		facturar(ana, reserva, "{\"nif\":\"12345678A\"}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("El NIF/CIF no es válido"));
		facturar(ana, reserva, "{\"nif\":\"" + "1".repeat(16) + "\"}").andExpect(status().isBadRequest());
		facturar(ana, reserva, "{\"nombre\":\"" + "x".repeat(201) + "\"}").andExpect(status().isBadRequest());
		facturar(ana, reserva, "{\"direccion\":\"" + "x".repeat(301) + "\"}").andExpect(status().isBadRequest());
		facturar(ana, reserva, "no es json").andExpect(status().isBadRequest());

		assertThat(contarFacturas()).isZero();
		assertThat(contador(club, 2030)).isZero();
		facturar(ana, reserva).andExpect(status().isCreated()).andExpect(jsonPath("$.numero").value(1));
	}

	@Test
	void losSaltosDeLineaYControlesDelNombreSeConviertenEnEspacios() throws Exception {
		configurarDatosFiscales(admin, club);
		Jug ana = ana("ana@vibora.com");
		long reserva = reservaEnElLunes(ana, 11, 0);

		facturar(ana, reserva, "{\"nombre\":\"Ana\\n\\tGarcia   Lopez\",\"direccion\":\"Calle\\r\\n1\"}")
				.andExpect(status().isCreated()).andExpect(jsonPath("$.receptor.nombre").value("Ana Garcia Lopez"))
				.andExpect(jsonPath("$.receptor.direccion").value("Calle 1"));
	}

	// ---- condiciones ----

	@Test
	void sinDatosFiscalesDelClub_409_ynoSeConsumeNumero() throws Exception {
		Jug ana = ana("ana@vibora.com");
		long reserva = reservaEnElLunes(ana, 11, 0);

		facturar(ana, reserva).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value(SIN_DATOS_FISCALES));
		facturar(admin, reserva).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value(SIN_DATOS_FISCALES));

		assertThat(contarFacturas()).isZero();
		assertThat(contador(club, 2030)).isZero();
		// al configurarlos ya se puede
		configurarDatosFiscales(admin, club);
		facturar(ana, reserva).andExpect(status().isCreated()).andExpect(jsonPath("$.numero").value(1));
	}

	@Test
	void unaReservaCancelada_409() throws Exception {
		configurarDatosFiscales(admin, club);
		Jug ana = ana("ana@vibora.com");
		long reserva = reservaEnElLunes(ana, 11, 0);
		cancelar(ana, reserva).andExpect(status().isOk());

		facturar(ana, reserva).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Solo se pueden facturar reservas confirmadas"));
		assertThat(contarFacturas()).isZero();
	}

	@Test
	void unaReservaSinImporte_409_porqueNoSePuedeEmitirUnaFacturaDeCeroEuros() throws Exception {
		configurarDatosFiscales(admin, club);
		Jug ana = ana("ana@vibora.com");
		long reserva = reservaEnElLunes(ana, 11, 0);
		jdbc.update("update reserva set precio_total = 0 where id = ?", reserva);
		refrescar(); // la reserva ya estaba cargada en la sesion de test

		facturar(ana, reserva).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("La reserva no tiene importe que facturar"));
		assertThat(contarFacturas()).isZero();
		assertThat(contador(club, 2030)).isZero();
	}

	@Test
	void lasDeudasEntreJugadoresNoSeFacturan_laFacturaEsDelPrecioCompletoAlOrganizador() throws Exception {
		configurarDatosFiscales(admin, club);
		Jug ana = ana("ana@vibora.com");
		Jug beto = jugador("beto@vibora.com", "Beto", "Ruiz");
		long reserva = reservaEnElLunes(ana, 11, 0);
		mvc.perform(put("/api/reservas/" + reserva + "/abierta")
				.header("Authorization", ana.token()).contentType(MediaType.APPLICATION_JSON).content("{\"abierta\":true}"))
				.andExpect(status().isOk());
		mvc.perform(post("/api/reservas/" + reserva + "/apuntarse").header("Authorization", beto.token()))
				.andExpect(status().isCreated());

		facturar(ana, reserva).andExpect(status().isCreated()).andExpect(jsonPath("$.total").value(45.0))
				.andExpect(jsonPath("$.receptor.nombre").value("Ana García"));
		misFacturas(beto).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void cambiarLosDatosFiscalesDespues_noAlteraLasFacturasYaEmitidas_peroSiLasNuevas() throws Exception {
		configurarDatosFiscales(admin, club);
		Jug ana = ana("ana@vibora.com");
		long vieja = facturaEmitida(ana, reservaEnElLunes(ana, 8, 0));

		datosFiscales(admin, club, """
				{"razonSocial":"Nueva Razón S.A.","nif":"A58818501","direccionFiscal":"Avenida Nueva 9","tipoIva":10}
				""").andExpect(status().isOk());
		long nueva = facturaEmitida(ana, reservaEnElLunes(ana, 9, 30));

		factura(ana, vieja).andExpect(status().isOk())
				.andExpect(jsonPath("$.emisor.razonSocial").value("Pádel Víbora S.L."))
				.andExpect(jsonPath("$.emisor.nif").value("B12345674"))
				.andExpect(jsonPath("$.emisor.direccion").value("Calle Álvaro Cunqueiro 5, 36001 Pontevedra"))
				.andExpect(jsonPath("$.tipoIva").value(21.0))
				.andExpect(jsonPath("$.base").value(37.19));
		factura(ana, nueva).andExpect(status().isOk())
				.andExpect(jsonPath("$.emisor.razonSocial").value("Nueva Razón S.A."))
				.andExpect(jsonPath("$.emisor.nif").value("A58818501"))
				.andExpect(jsonPath("$.tipoIva").value(10.0))
				.andExpect(jsonPath("$.base").value(40.91));
		assertThat(jdbc.queryForObject("select count(*) from factura where emisor_nif = 'B12345674' and tipo_iva = 21.00",
				Long.class)).isEqualTo(1);
	}

	@Test
	void elNombreDelOrganizadorSeCopia_yRenombrarloDespuesNoCambiaLaFactura() throws Exception {
		configurarDatosFiscales(admin, club);
		Jug ana = ana("ana@vibora.com");
		long id = facturaEmitida(ana, reservaEnElLunes(ana, 11, 0));

		jdbc.update("update usuario set nombre = 'Anita', apellidos = 'Nueva' where id = ?", ana.id());

		factura(ana, id).andExpect(jsonPath("$.receptor.nombre").value("Ana García"));
	}

	// ---- permisos ----

	@Test
	void permisos_soloElOrganizadorYElAdminDelClub() throws Exception {
		configurarDatosFiscales(admin, club);
		Jug ana = ana("ana@vibora.com");
		Jug beto = jugador("beto@vibora.com", "Beto", "Ruiz");
		Club otro = crearClub("Club B");
		Jug adminOtro = administrador(otro, "admin-b@vibora.com");
		Jug monitor = personal(club, "monitor@vibora.com", Rol.MONITOR);
		long reserva = reservaEnElLunes(ana, 11, 0);

		for (Jug intruso : new Jug[] { beto, adminOtro, monitor }) {
			facturar(intruso, reserva).andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("Acceso denegado"));
		}
		mvc.perform(post("/api/reservas/" + reserva + "/factura")).andExpect(status().isUnauthorized());
		assertThat(contarFacturas()).isZero();
		assertThat(contador(club, 2030)).isZero();
	}

	@Test
	void unJugadorQueSoloSeApuntaAlPartido_noPuedeFacturarlo() throws Exception {
		configurarDatosFiscales(admin, club);
		Jug ana = ana("ana@vibora.com");
		Jug beto = jugador("beto@vibora.com", "Beto", "Ruiz");
		long reserva = reservaEnElLunes(ana, 11, 0);
		mvc.perform(put("/api/reservas/" + reserva + "/abierta")
				.header("Authorization", ana.token()).contentType(MediaType.APPLICATION_JSON).content("{\"abierta\":true}"))
				.andExpect(status().isOk());
		mvc.perform(post("/api/reservas/" + reserva + "/apuntarse").header("Authorization", beto.token()))
				.andExpect(status().isCreated());

		facturar(beto, reserva).andExpect(status().isForbidden());
	}

	@Test
	void unaReservaInexistente_404() throws Exception {
		configurarDatosFiscales(admin, club);
		Jug ana = ana("ana@vibora.com");

		facturar(ana, 999999).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Reserva no encontrada"));
	}
}
