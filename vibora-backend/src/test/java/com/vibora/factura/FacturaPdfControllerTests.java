package com.vibora.factura;

import com.vibora.club.Club;
import com.vibora.usuario.Rol;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FacturaPdfControllerTests extends FacturaApiTestBase {

	private Jug ana;

	@BeforeEach
	void preparar() throws Exception {
		configurarDatosFiscales(admin, club);
		ana = ana("ana@vibora.com");
	}

	private static String textoDe(byte[] pdf) throws Exception {
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			return new PDFTextStripper().getText(doc).replaceAll("\\s+", " ");
		}
	}

	@Test
	void elPdfSaleConLasCabecerasSeguras_yEsUnPdfDeVerdad() throws Exception {
		long id = facturaEmitida(ana, reservaEnElLunes(ana, 11, 0));

		MvcResult r = pdf(ana, id).andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "application/pdf"))
				.andExpect(header().string("Content-Disposition", "attachment; filename=\"F2030-0001.pdf\""))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().string("Cache-Control", "private, no-store"))
				.andReturn();

		byte[] pdf = r.getResponse().getContentAsByteArray();
		assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
		assertThat(pdf.length).isBetween(1_000, 200_000);
		assertThat(r.getResponse().getHeader("Content-Length")).isEqualTo(String.valueOf(pdf.length));
	}

	@Test
	void elTextoDelPdfLleva_numero_nifs_nombres_desglose_yElAviso() throws Exception {
		long id = facturaEmitida(ana, reservaEnElLunes(ana, 11, 0));

		String texto = textoDe(pdf(ana, id).andReturn().getResponse().getContentAsByteArray());

		assertThat(texto).contains("FACTURA").doesNotContain("RECTIFICATIVA");
		assertThat(texto).contains("F2030-0001", "01/01/2030");
		assertThat(texto).contains("Pádel Víbora S.L.", "B12345674", "Calle Álvaro Cunqueiro 5, 36001 Pontevedra");
		assertThat(texto).contains("Ana García");
		assertThat(texto).contains("Pista 1 de Club A", "07/01/2030 a las 11:00");
		assertThat(texto).contains("37,19 €", "7,81 €", "45,00 €", "IVA (21%)");
		assertThat(texto).contains("Documento simulado sin validez fiscal");
	}

	@Test
	void elReceptorPersonalizadoSaleEnElPdf_conSusAcentos() throws Exception {
		long reserva = reservaEnElLunes(ana, 11, 0);
		facturar(ana, reserva, """
				{"nombre":"Construcciones Peña y Núñez S.L.","nif":"12345678Z","direccion":"Rúa do Vilar 7, 15705 Santiago"}
				""").andExpect(status().isCreated());
		long id = jdbc.queryForObject("select id from factura", Long.class);

		String texto = textoDe(pdf(ana, id).andReturn().getResponse().getContentAsByteArray());

		assertThat(texto).contains("Construcciones Peña y Núñez S.L.", "12345678Z", "Rúa do Vilar 7, 15705 Santiago");
	}

	@Test
	void laRectificativaTienePdfPropio_conLaReferenciaALaOriginalYLosImportesNegativos() throws Exception {
		long reserva = reservaEnElLunes(ana, 11, 0);
		facturaEmitida(ana, reserva);
		cancelar(ana, reserva).andExpect(status().isOk());
		long rectificativa = jdbc.queryForObject("select id from factura where tipo = 'RECTIFICATIVA'", Long.class);

		MvcResult r = pdf(ana, rectificativa).andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", "attachment; filename=\"F2030-0002.pdf\"")).andReturn();
		String texto = textoDe(r.getResponse().getContentAsByteArray());

		assertThat(texto).contains("FACTURA RECTIFICATIVA", "F2030-0002", "Rectifica a la factura F2030-0001");
		assertThat(texto).contains("-37,19 €", "-7,81 €", "-45,00 €");
		assertThat(texto).contains("Documento simulado sin validez fiscal");
	}

	@Test
	void nombresEnormes_sacanUnPdfIgualmenteSinError() throws Exception {
		long reserva = reservaEnElLunes(ana, 11, 0);
		facturar(ana, reserva, "{\"nombre\":\"" + "Ñ".repeat(200) + "\",\"direccion\":\"" + "Calle ".repeat(50) + "\"}")
				.andExpect(status().isCreated());
		long id = jdbc.queryForObject("select id from factura", Long.class);

		MvcResult r = pdf(ana, id).andExpect(status().isOk()).andReturn();

		assertThat(textoDe(r.getResponse().getContentAsByteArray()).replace(" ", "")).contains("Ñ".repeat(200));
	}

	@Test
	void visibilidad_elReceptorYElAdminDelClub_cualquierOtroRecibe404_ySinTokenEs401() throws Exception {
		long id = facturaEmitida(ana, reservaEnElLunes(ana, 11, 0));
		Jug beto = jugador("beto@vibora.com", "Beto", "Ruiz");
		Club otro = crearClub("Club B");
		Jug adminOtro = administrador(otro, "admin-b@vibora.com");
		Jug monitor = personal(club, "monitor@vibora.com", Rol.MONITOR);

		pdf(ana, id).andExpect(status().isOk());
		pdf(admin, id).andExpect(status().isOk());
		for (Jug intruso : new Jug[] { beto, adminOtro, monitor }) {
			pdf(intruso, id).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Factura no encontrada"));
		}
		pdf(ana, 999999).andExpect(status().isNotFound());
		mvc.perform(get("/api/facturas/" + id + "/pdf")).andExpect(status().isUnauthorized());
	}
}
