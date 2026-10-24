package com.vibora.factura;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** El PDF se genera de verdad y su texto se extrae con PDFBox para comprobar el contenido. */
class GeneradorPdfTests {

	private static final String AVISO = "Documento simulado sin validez fiscal";

	private static FacturaResponse original(String receptorNombre, String concepto) {
		return new FacturaResponse(7L, "F2026", 1, "F2026-0001", TipoFactura.ORIGINAL, null, 3L,
				Instant.parse("2026-03-05T23:30:00Z"), // 06/03/2026 00:30 en Madrid
				new FacturaResponse.Emisor("Pádel Ñandú S.L.", "B12345674", "Calle Álvaro Cunqueiro, 5, 36001 Pontevedra"),
				new FacturaResponse.Receptor(receptorNombre, "12345678Z", "Avenida de la Peña 12, 2º B, Ourense"), concepto,
				new BigDecimal("37.19"), new BigDecimal("21.00"), new BigDecimal("7.81"), new BigDecimal("45.00"));
	}

	private static FacturaResponse rectificativa() {
		return new FacturaResponse(8L, "F2026", 2, "F2026-0002", TipoFactura.RECTIFICATIVA, 7L, 3L,
				Instant.parse("2026-03-07T10:00:00Z"),
				new FacturaResponse.Emisor("Pádel Ñandú S.L.", "B12345674", "Calle Álvaro Cunqueiro, 5, 36001 Pontevedra"),
				new FacturaResponse.Receptor("José Ángel Peña", "12345678Z", null), "Pista 1 de Club A",
				new BigDecimal("-37.19"), new BigDecimal("21.00"), new BigDecimal("-7.81"), new BigDecimal("-45.00"));
	}

	private static String texto(byte[] pdf) throws Exception {
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			return new PDFTextStripper().getText(doc);
		}
	}

	private static String compacto(String texto) {
		return texto.replaceAll("\\s+", " ").trim();
	}

	private static String sinEspacios(String texto) {
		return texto.replaceAll("\\s+", "");
	}

	@Test
	void generaUnPdfValido_conLaCabeceraYElTamanoEsperados() throws Exception {
		byte[] pdf = GeneradorPdf.generar(original("José Ángel Peña", "Pista 1"), null);

		assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
		assertThat(pdf.length).isBetween(1_000, 200_000);
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			assertThat(doc.getNumberOfPages()).isEqualTo(1);
		}
	}

	@Test
	void laFacturaOriginalLleva_numero_fecha_nifs_nombres_desglose_yAviso() throws Exception {
		String t = compacto(texto(GeneradorPdf.generar(original("José Ángel Peña", "Pista 1 de Club A el 07/01/2030"), null)));

		assertThat(t).contains("FACTURA").doesNotContain("RECTIFICATIVA");
		assertThat(t).contains("F2026-0001");
		assertThat(t).contains("06/03/2026"); // en hora de Madrid, no el dia UTC
		assertThat(t).contains("Pádel Ñandú S.L.", "B12345674", "Calle Álvaro Cunqueiro, 5, 36001 Pontevedra");
		assertThat(t).contains("José Ángel Peña", "12345678Z", "Avenida de la Peña 12, 2º B, Ourense");
		assertThat(t).contains("Pista 1 de Club A el 07/01/2030");
		assertThat(t).contains("37,19 €", "7,81 €", "45,00 €");
		assertThat(t).contains("21%");
		assertThat(t).contains(AVISO);
		assertThat(t).doesNotContain("Rectifica");
	}

	@Test
	void losImportesUsanElFormatoEspanolConPuntoDeMillarYComaDecimal() throws Exception {
		FacturaResponse grande = new FacturaResponse(1L, "F2026", 1, "F2026-0001", TipoFactura.ORIGINAL, null, 3L,
				Instant.parse("2026-03-05T10:00:00Z"), new FacturaResponse.Emisor("Club", "B12345674", "Calle 1"),
				new FacturaResponse.Receptor("Ana", null, null), "Pista", new BigDecimal("1020.46"), new BigDecimal("21.00"),
				new BigDecimal("214.30"), new BigDecimal("1234.76"));

		String t = compacto(texto(GeneradorPdf.generar(grande, null)));

		assertThat(t).contains("1.234,76 €", "1.020,46 €", "214,30 €");
	}

	@Test
	void unTipoDeIvaConDecimalesSeMuestraSinCerosSobrantes() throws Exception {
		FacturaResponse f = new FacturaResponse(1L, "F2026", 1, "F2026-0001", TipoFactura.ORIGINAL, null, 3L,
				Instant.parse("2026-03-05T10:00:00Z"), new FacturaResponse.Emisor("Club", "B12345674", "Calle 1"),
				new FacturaResponse.Receptor("Ana", null, null), "Pista", new BigDecimal("40.72"), new BigDecimal("10.50"),
				new BigDecimal("4.28"), new BigDecimal("45.00"));

		assertThat(compacto(texto(GeneradorPdf.generar(f, null)))).contains("10,5%");
	}

	@Test
	void laRectificativaLleva_titulo_referenciaALaOriginal_eImportesNegativos() throws Exception {
		String t = compacto(texto(GeneradorPdf.generar(rectificativa(), "F2026-0001")));

		assertThat(t).contains("FACTURA RECTIFICATIVA");
		assertThat(t).contains("F2026-0002");
		assertThat(t).contains("Rectifica a la factura F2026-0001");
		assertThat(t).contains("-37,19 €", "-7,81 €", "-45,00 €");
		assertThat(t).contains("07/03/2026");
		assertThat(t).contains(AVISO);
	}

	@Test
	void sinNifNiDireccionDelReceptor_noFallaYNoEscribeNull() throws Exception {
		FacturaResponse f = new FacturaResponse(1L, "F2026", 1, "F2026-0001", TipoFactura.ORIGINAL, null, 3L,
				Instant.parse("2026-03-05T10:00:00Z"), new FacturaResponse.Emisor("Club", "B12345674", "Calle 1"),
				new FacturaResponse.Receptor("Ana García", null, null), "Pista", new BigDecimal("37.19"),
				new BigDecimal("21.00"), new BigDecimal("7.81"), new BigDecimal("45.00"));

		String t = compacto(texto(GeneradorPdf.generar(f, null)));

		assertThat(t).contains("Ana García").doesNotContain("null");
	}

	@Test
	void nombresYConceptosMuyLargos_seAjustanSinRomperElPdf() throws Exception {
		String nombre = "Ñ".repeat(200); // una sola "palabra" de 200 caracteres: no hay donde partirla
		String concepto = "Reserva muy larga ".repeat(16) + "FIN"; // 300 caracteres aprox.

		byte[] pdf = GeneradorPdf.generar(original(nombre, concepto), null);

		String t = sinEspacios(texto(pdf));
		assertThat(t).contains(nombre);
		assertThat(t).contains(sinEspacios(concepto));
		assertThat(t).contains(sinEspacios(AVISO));
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			assertThat(doc.getNumberOfPages()).isLessThanOrEqualTo(2);
		}
	}

	@Test
	void caracteresFueraDeWinAnsi_ysaltosDeLinea_noRompenElPdf() throws Exception {
		byte[] pdf = GeneradorPdf.generar(original("Łukasz\n\t Привет 😀 Zoë", "Pista\r\n1"), null);

		String t = compacto(texto(pdf));
		assertThat(t).contains("Zoë"); // lo representable se conserva
		assertThat(t).contains(AVISO);
	}

	@Test
	void elPdfNoIncrustaFuentesNiLlevaEnlacesNiJavaScript() throws Exception {
		byte[] pdf = GeneradorPdf.generar(original("http://malo.example/<b>x</b> javascript:alert(1)", "Pista"), null);

		try (PDDocument doc = Loader.loadPDF(pdf)) {
			var catalogo = doc.getDocumentCatalog().getCOSObject();
			assertThat(catalogo.containsKey(COSName.NAMES)).as("sin arbol de nombres (JavaScript, adjuntos)").isFalse();
			assertThat(catalogo.containsKey(COSName.OPEN_ACTION)).as("sin accion al abrir").isFalse();
			assertThat(catalogo.containsKey(COSName.AA)).as("sin acciones adicionales").isFalse();
			assertThat(catalogo.containsKey(COSName.ACRO_FORM)).as("sin formularios").isFalse();
			for (PDPage pagina : doc.getPages()) {
				assertThat(pagina.getCOSObject().containsKey(COSName.AA)).isFalse();
				assertThat(pagina.getAnnotations()).as("sin anotaciones ni enlaces").isEmpty();
				PDResources recursos = pagina.getResources();
				for (var nombre : recursos.getFontNames()) {
					PDFont fuente = recursos.getFont(nombre);
					assertThat(fuente.isEmbedded()).as("fuente %s", fuente.getName()).isFalse();
				}
			}
		}
		// el texto del usuario sale como texto plano, no como enlace
		assertThat(compacto(texto(pdf))).contains("http://malo.example/<b>x</b> javascript:alert(1)");
	}
}
