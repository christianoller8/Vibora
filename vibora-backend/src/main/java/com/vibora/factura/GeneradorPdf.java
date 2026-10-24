package com.vibora.factura;

import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * PDF de una factura (simulada). Seguridad: solo escribe TEXTO con las dos fuentes estandar de PDF (Helvetica y
 * Helvetica-Bold, SIN incrustar, codificacion WinAnsi/CP1252); nada de lo que escribe el usuario llega a una fuente,
 * un enlace, una accion ni un JavaScript. Los caracteres que WinAnsi no representa salen como "?" y los saltos de linea
 * y tabuladores se convierten en espacios. Los nombres y conceptos largos se ajustan dentro de sus celdas.
 */
public final class GeneradorPdf {

	static final String AVISO = "Documento simulado sin validez fiscal";

	private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final Charset CP1252 = Charset.forName("windows-1252");
	private static final Color GRIS = new Color(0x55, 0x55, 0x55);
	private static final Color GRIS_CLARO = new Color(0xEE, 0xEE, 0xEE);

	private GeneradorPdf() {
	}

	/** {@code numeroRectificado}: numero completo de la factura que rectifica (solo para las rectificativas). */
	public static byte[] generar(FacturaResponse f, String numeroRectificado) {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		Document documento = new Document(PageSize.A4, 40, 40, 40, 40);
		try {
			PdfWriter.getInstance(documento, salida);
			documento.addTitle("Factura " + limpiar(f.numeroCompleto()));
			documento.addCreator("Vibora (simulacion)");
			documento.open();

			BaseFont normal = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
			BaseFont negrita = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
			Font texto = new Font(normal, 10);
			Font textoNegrita = new Font(negrita, 10);
			Font etiqueta = new Font(negrita, 8, Font.NORMAL, GRIS);
			Font titulo = new Font(negrita, 20);

			boolean rectificativa = f.tipo() == TipoFactura.RECTIFICATIVA;
			Paragraph cabecera = new Paragraph(rectificativa ? "FACTURA RECTIFICATIVA" : "FACTURA", titulo);
			cabecera.setSpacingAfter(6);
			documento.add(cabecera);
			documento.add(new Paragraph("Número: " + limpiar(f.numeroCompleto()), textoNegrita));
			documento.add(new Paragraph("Fecha: " + FECHA.format(f.emitidaEn().atZone(MADRID)), texto));
			if (rectificativa && numeroRectificado != null) {
				documento.add(new Paragraph("Rectifica a la factura " + limpiar(numeroRectificado), texto));
			}

			PdfPTable partes = new PdfPTable(2);
			partes.setWidthPercentage(100);
			partes.setWidths(new float[] { 50, 50 });
			partes.setSpacingBefore(18);
			partes.setSpacingAfter(18);
			partes.addCell(bloque("EMISOR", etiqueta, texto, f.emisor().razonSocial(), "NIF: " + nulo(f.emisor().nif()),
					f.emisor().direccion()));
			partes.addCell(bloque("RECEPTOR", etiqueta, texto, f.receptor().nombre(),
					f.receptor().nif() == null ? null : "NIF: " + f.receptor().nif(), f.receptor().direccion()));
			documento.add(partes);

			PdfPTable conceptos = new PdfPTable(2);
			conceptos.setWidthPercentage(100);
			conceptos.setWidths(new float[] { 75, 25 });
			conceptos.addCell(cabeceraTabla("Concepto", textoNegrita, Element.ALIGN_LEFT));
			conceptos.addCell(cabeceraTabla("Base imponible", textoNegrita, Element.ALIGN_RIGHT));
			conceptos.addCell(celda(f.concepto(), texto, Element.ALIGN_LEFT, Rectangle.BOTTOM));
			conceptos.addCell(celda(euros(f.base()), texto, Element.ALIGN_RIGHT, Rectangle.BOTTOM));
			documento.add(conceptos);

			PdfPTable totales = new PdfPTable(2);
			totales.setWidthPercentage(45);
			totales.setHorizontalAlignment(Element.ALIGN_RIGHT);
			totales.setWidths(new float[] { 55, 45 });
			totales.setSpacingBefore(14);
			totales.addCell(celda("Base imponible", texto, Element.ALIGN_LEFT, Rectangle.NO_BORDER));
			totales.addCell(celda(euros(f.base()), texto, Element.ALIGN_RIGHT, Rectangle.NO_BORDER));
			totales.addCell(celda("IVA (" + porcentaje(f.tipoIva()) + "%)", texto, Element.ALIGN_LEFT, Rectangle.NO_BORDER));
			totales.addCell(celda(euros(f.cuotaIva()), texto, Element.ALIGN_RIGHT, Rectangle.NO_BORDER));
			totales.addCell(celda("TOTAL", textoNegrita, Element.ALIGN_LEFT, Rectangle.TOP));
			totales.addCell(celda(euros(f.total()), textoNegrita, Element.ALIGN_RIGHT, Rectangle.TOP));
			documento.add(totales);

			PdfPTable aviso = new PdfPTable(1);
			aviso.setWidthPercentage(100);
			aviso.setSpacingBefore(40);
			PdfPCell celdaAviso = celda(AVISO, textoNegrita, Element.ALIGN_CENTER, Rectangle.BOX);
			celdaAviso.setBackgroundColor(GRIS_CLARO);
			celdaAviso.setPadding(8);
			aviso.addCell(celdaAviso);
			documento.add(aviso);
		} catch (DocumentException | IOException ex) {
			throw new IllegalStateException("No se pudo generar el PDF de la factura", ex);
		} finally {
			documento.close();
		}
		return salida.toByteArray();
	}

	private static PdfPCell bloque(String rotulo, Font fuenteRotulo, Font fuente, String... lineas) {
		PdfPCell celda = new PdfPCell();
		celda.setBorder(Rectangle.BOX);
		celda.setBorderColor(GRIS);
		celda.setPadding(8);
		celda.addElement(new Paragraph(rotulo, fuenteRotulo));
		for (String linea : lineas) {
			if (linea != null && !linea.isBlank()) {
				celda.addElement(new Paragraph(limpiar(linea), fuente));
			}
		}
		return celda;
	}

	private static PdfPCell cabeceraTabla(String texto, Font fuente, int alineacion) {
		PdfPCell celda = celda(texto, fuente, alineacion, Rectangle.BOTTOM);
		celda.setBackgroundColor(GRIS_CLARO);
		return celda;
	}

	private static PdfPCell celda(String texto, Font fuente, int alineacion, int borde) {
		PdfPCell celda = new PdfPCell(new Phrase(limpiar(texto), fuente));
		celda.setBorder(borde);
		celda.setHorizontalAlignment(alineacion);
		celda.setPadding(5);
		return celda;
	}

	/** "1.234,56 €" (miles con punto, decimales con coma); los negativos llevan el signo menos delante. */
	static String euros(BigDecimal importe) {
		DecimalFormatSymbols simbolos = new DecimalFormatSymbols(Locale.ROOT);
		simbolos.setDecimalSeparator(',');
		simbolos.setGroupingSeparator('.');
		DecimalFormat formato = new DecimalFormat("#,##0.00", simbolos);
		formato.setRoundingMode(RoundingMode.HALF_UP);
		return formato.format(importe) + " €";
	}

	/** 21.00 -> "21", 10.50 -> "10,5". */
	private static String porcentaje(BigDecimal tipo) {
		return tipo.stripTrailingZeros().toPlainString().replace('.', ',');
	}

	private static String nulo(String texto) {
		return texto == null ? "" : texto;
	}

	/**
	 * Deja el texto listo para una fuente WinAnsi: sin caracteres de control, con un espacio por cada salto o tabulador y
	 * "?" por cada caracter que CP1252 no tiene (un par sustituto, como un emoji, cuenta como uno).
	 */
	static String limpiar(String texto) {
		if (texto == null) {
			return "";
		}
		CharsetEncoder codificador = CP1252.newEncoder();
		StringBuilder resultado = new StringBuilder(texto.length());
		texto.codePoints().forEach(cp -> {
			if (Character.isISOControl(cp) || Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
				resultado.append(' ');
			} else if (codificador.canEncode(new String(Character.toChars(cp)))) {
				resultado.appendCodePoint(cp);
			} else {
				resultado.append('?');
			}
		});
		return resultado.toString().replaceAll(" {2,}", " ").trim();
	}
}
