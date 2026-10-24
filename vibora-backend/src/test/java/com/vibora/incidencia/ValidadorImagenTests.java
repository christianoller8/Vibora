package com.vibora.incidencia;

import com.vibora.common.exception.ArchivoDemasiadoGrandeException;
import com.vibora.incidencia.ValidadorImagen.Imagen;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El tipo se decide por los BYTES, nunca por lo que diga el cliente; JPEG/PNG se recodifican y WebP se guarda tal cual. */
class ValidadorImagenTests {

	private static void assertFormatoNoPermitido(byte[] bytes) {
		assertThatThrownBy(() -> ValidadorImagen.procesar(bytes))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Formato de imagen no permitido");
	}

	// ---- imagenes validas ----

	@Test
	void unJpegValido_seAcepta_conSuTipoRealYSeRecodifica() {
		byte[] original = Imagenes.jpeg(40, 30);

		Imagen imagen = ValidadorImagen.procesar(original);

		assertThat(imagen.tipoContenido()).isEqualTo("image/jpeg");
		BufferedImage leida = Imagenes.leer(imagen.bytes());
		assertThat(leida.getWidth()).isEqualTo(40);
		assertThat(leida.getHeight()).isEqualTo(30);
		assertThat(imagen.bytes()[0] & 0xFF).isEqualTo(0xFF);
		assertThat(imagen.bytes()[1] & 0xFF).isEqualTo(0xD8);
	}

	@Test
	void unPngValido_seAcepta_conSuTipoReal_yConservaElAlfa() {
		Imagen imagen = ValidadorImagen.procesar(Imagenes.png(20, 10, true));

		assertThat(imagen.tipoContenido()).isEqualTo("image/png");
		BufferedImage leida = Imagenes.leer(imagen.bytes());
		assertThat(leida.getWidth()).isEqualTo(20);
		assertThat(leida.getHeight()).isEqualTo(10);
		assertThat(leida.getColorModel().hasAlpha()).isTrue();
	}

	@Test
	void unWebpValido_seAceptaTalCual() {
		byte[] original = Imagenes.webpSinPerdida(1, 1);

		Imagen imagen = ValidadorImagen.procesar(original);

		assertThat(imagen.tipoContenido()).isEqualTo("image/webp");
		assertThat(imagen.bytes()).containsExactly(original);
	}

	@Test
	void unWebpConBytesTrasElFinalDelRiff_seGuardaSinEsaCola() {
		byte[] original = Imagenes.webpSinPerdida(1, 1);
		byte[] conCola = Imagenes.conCargaTrasElFinal(original, "<script>alert(1)</script>");

		Imagen imagen = ValidadorImagen.procesar(conCola);

		assertThat(imagen.bytes()).containsExactly(original);
		assertThat(Imagenes.contiene(imagen.bytes(), "script")).isFalse();
	}

	@Test
	void unWebpCuyoRiffDeclaraMasDeLoQueHay_seRechaza() {
		byte[] entero = Imagenes.webpSinPerdida(1, 1);

		assertFormatoNoPermitido(Arrays.copyOf(entero, entero.length - 4));
	}

	// ---- lo que NO es una imagen permitida ----

	static Stream<byte[]> noPermitidos() {
		byte[] riffDeAudio = Imagenes.webpSinPerdida(1, 1).clone();
		System.arraycopy("WAVE".getBytes(StandardCharsets.US_ASCII), 0, riffDeAudio, 8, 4);
		byte[] riffWebpSinTrozoValido = Imagenes.webpSinPerdida(1, 1).clone();
		System.arraycopy("JUNK".getBytes(StandardCharsets.US_ASCII), 0, riffWebpSinTrozoValido, 12, 4);
		byte[] jpegTruncado = Arrays.copyOf(Imagenes.jpeg(40, 30), 30);
		return Stream.of(
				new byte[0], // vacio
				new byte[] { (byte) 0xFF }, // 1 byte
				"GIF89a\u0001\u0000\u0001\u0000".getBytes(StandardCharsets.ISO_8859_1), // GIF
				"<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8),
				"<?xml version=\"1.0\"?><svg/>".getBytes(StandardCharsets.UTF_8),
				"<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8),
				"esto es solo texto, aunque se llame foto.png".getBytes(StandardCharsets.UTF_8),
				new byte[] { 0x7F, 'E', 'L', 'F', 2, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0 }, // ejecutable de Linux
				new byte[] { 'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0, 4, 0, 0, 0 }, // ejecutable de Windows
				"%PDF-1.7\n1 0 obj".getBytes(StandardCharsets.US_ASCII),
				"BM".getBytes(StandardCharsets.US_ASCII), // BMP
				new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' }, // firma PNG sin nada mas
				jpegTruncado, // firma JPEG, pero cortado antes de los datos
				new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3, 4, 5, 6, 7, 8 }, // firma JPEG y basura
				riffDeAudio, // RIFF/WAVE
				riffWebpSinTrozoValido, // RIFF/WEBP pero sin VP8, VP8L ni VP8X
				"RIFF".getBytes(StandardCharsets.US_ASCII));
	}

	@ParameterizedTest
	@MethodSource("noPermitidos")
	void loQueNoEsJpegPngNiWebp_seRechaza(byte[] bytes) {
		assertFormatoNoPermitido(bytes);
	}

	@Test
	void unPngConLaFirmaCorrectaPeroDatosCorruptos_seRechaza() {
		byte[] png = Imagenes.png(20, 10, false);
		byte[] corrupto = Arrays.copyOf(png, png.length);
		for (int i = 60; i < corrupto.length - 20; i++) {
			corrupto[i] = (byte) (corrupto[i] ^ 0x5A);
		}

		assertFormatoNoPermitido(corrupto);
	}

	// ---- privacidad: se pierde todo lo que no son pixeles ----

	@Test
	void alRecodificarUnJpeg_desapareceElExifConSuGps() {
		byte[] conExif = Imagenes.conExif(Imagenes.jpeg(40, 30), 1, Imagenes.SECRETO);
		assertThat(Imagenes.contiene(conExif, Imagenes.SECRETO)).as("el original lo lleva").isTrue();
		assertThat(Imagenes.tieneMarcadorJpeg(conExif, 0xE1)).isTrue();

		Imagen imagen = ValidadorImagen.procesar(conExif);

		assertThat(Imagenes.contiene(imagen.bytes(), Imagenes.SECRETO)).isFalse();
		assertThat(Imagenes.contiene(imagen.bytes(), "Exif")).isFalse();
		assertThat(Imagenes.tieneMarcadorJpeg(imagen.bytes(), 0xE1)).as("sin segmento APP1").isFalse();
		assertThat(Imagenes.leer(imagen.bytes()).getWidth()).isEqualTo(40);
	}

	@Test
	void unJpegConUnaCargaTrasElFinal_pierdeLaCarga() {
		byte[] poligloto = Imagenes.conCargaTrasElFinal(Imagenes.jpeg(40, 30), "<script>alert('xss')</script>PK\u0003\u0004");

		Imagen imagen = ValidadorImagen.procesar(poligloto);

		assertThat(Imagenes.contiene(imagen.bytes(), "script")).isFalse();
		assertThat(Imagenes.contiene(imagen.bytes(), "PK")).isFalse();
	}

	@Test
	void unPngConTextoYBasuraTrasIend_pierdeAmbos() {
		byte[] sucio = Imagenes.pngConTextoYBasura(Imagenes.png(20, 10, false), Imagenes.SECRETO, "<script>alert(1)</script>");
		assertThat(Imagenes.contiene(sucio, Imagenes.SECRETO)).isTrue();

		Imagen imagen = ValidadorImagen.procesar(sucio);

		assertThat(Imagenes.contiene(imagen.bytes(), Imagenes.SECRETO)).isFalse();
		assertThat(Imagenes.contiene(imagen.bytes(), "tEXt")).isFalse();
		assertThat(Imagenes.contiene(imagen.bytes(), "script")).isFalse();
		assertThat(Imagenes.leer(imagen.bytes()).getWidth()).isEqualTo(20);
	}

	@Test
	void unWebp_conservaSuExif_limiteConocido() {
		// No hay decodificador de WebP en el JDK: se guarda tal cual y los metadatos del propio WebP NO se eliminan.
		byte[] conExif = Imagenes.webpExtendido(100, 100);

		Imagen imagen = ValidadorImagen.procesar(conExif);

		assertThat(imagen.bytes()).containsExactly(conExif);
		assertThat(Imagenes.contiene(imagen.bytes(), Imagenes.SECRETO)).isTrue();
	}

	// ---- orientacion EXIF: al quitar el EXIF hay que aplicarla a los pixeles ----

	@ParameterizedTest
	@CsvSource({ "1,16,8", "2,16,8", "3,16,8", "4,16,8", "5,8,16", "6,8,16", "7,8,16", "8,8,16" })
	void laOrientacionExifSeAplicaALosPixeles_yLasDimensionesSeIntercambianSiHaceFalta(int orientacion, int ancho, int alto) {
		byte[] jpeg = Imagenes.conExif(Imagenes.jpegRojoIzquierdaAzulDerecha(16, 8), orientacion, "x");

		BufferedImage leida = Imagenes.leer(ValidadorImagen.procesar(jpeg).bytes());

		assertThat(leida.getWidth()).isEqualTo(ancho);
		assertThat(leida.getHeight()).isEqualTo(alto);
	}

	@Test
	void orientacion6_giraNoventaGradosEnSentidoHorario_laIzquierdaRojaPasaArriba() {
		byte[] jpeg = Imagenes.conExif(Imagenes.jpegRojoIzquierdaAzulDerecha(16, 8), 6, "x");

		BufferedImage leida = Imagenes.leer(ValidadorImagen.procesar(jpeg).bytes());

		assertThat(esRojo(leida.getRGB(4, 2))).as("arriba, rojo").isTrue();
		assertThat(esAzul(leida.getRGB(4, 13))).as("abajo, azul").isTrue();
	}

	@Test
	void orientacion8_giraNoventaGradosEnSentidoAntihorario_laIzquierdaRojaPasaAbajo() {
		byte[] jpeg = Imagenes.conExif(Imagenes.jpegRojoIzquierdaAzulDerecha(16, 8), 8, "x");

		BufferedImage leida = Imagenes.leer(ValidadorImagen.procesar(jpeg).bytes());

		assertThat(esAzul(leida.getRGB(4, 2))).as("arriba, azul").isTrue();
		assertThat(esRojo(leida.getRGB(4, 13))).as("abajo, rojo").isTrue();
	}

	@Test
	void orientacion3_giraCientoOchentaGrados_laIzquierdaRojaPasaALaDerecha() {
		byte[] jpeg = Imagenes.conExif(Imagenes.jpegRojoIzquierdaAzulDerecha(16, 8), 3, "x");

		BufferedImage leida = Imagenes.leer(ValidadorImagen.procesar(jpeg).bytes());

		assertThat(esAzul(leida.getRGB(3, 4))).as("izquierda, azul").isTrue();
		assertThat(esRojo(leida.getRGB(12, 4))).as("derecha, rojo").isTrue();
	}

	@Test
	void orientacion2_espejaHorizontalmente() {
		byte[] jpeg = Imagenes.conExif(Imagenes.jpegRojoIzquierdaAzulDerecha(16, 8), 2, "x");

		BufferedImage leida = Imagenes.leer(ValidadorImagen.procesar(jpeg).bytes());

		assertThat(esAzul(leida.getRGB(3, 4))).isTrue();
		assertThat(esRojo(leida.getRGB(12, 4))).isTrue();
	}

	@Test
	void unExifIlegible_seTrataComoOrientacionNormal_ySeSigueQuitando() {
		byte[] jpeg = Imagenes.conExifMalformado(Imagenes.jpegRojoIzquierdaAzulDerecha(16, 8));

		Imagen imagen = ValidadorImagen.procesar(jpeg);

		BufferedImage leida = Imagenes.leer(imagen.bytes());
		assertThat(leida.getWidth()).isEqualTo(16);
		assertThat(esRojo(leida.getRGB(3, 4))).isTrue();
		assertThat(Imagenes.contiene(imagen.bytes(), Imagenes.SECRETO)).isFalse();
	}

	private static boolean esRojo(int rgb) {
		return ((rgb >> 16) & 0xFF) > 180 && (rgb & 0xFF) < 90;
	}

	private static boolean esAzul(int rgb) {
		return (rgb & 0xFF) > 180 && ((rgb >> 16) & 0xFF) < 90;
	}

	// ---- bombas de descompresion: el limite se comprueba con la CABECERA, antes de decodificar ----

	@Test
	void unPngCuyaCabeceraDeclaraVeinteMilPorVeinteMil_seRechazaSinDecodificar() {
		byte[] bomba = Imagenes.pngSoloCabecera(20_000, 20_000);

		assertThat(bomba.length).isLessThan(100);
		assertThatThrownBy(() -> ValidadorImagen.procesar(bomba))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("megapíxeles");
	}

	@Test
	void elLimiteSonVeinticincoMegapixeles_exactamenteEseTamanoPasa_unoMasNo() {
		Imagen enElLimite = ValidadorImagen.procesar(Imagenes.pngGris(5_000, 5_000));
		assertThat(enElLimite.tipoContenido()).isEqualTo("image/png");

		assertThatThrownBy(() -> ValidadorImagen.procesar(Imagenes.pngGris(5_001, 5_000)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("megapíxeles");
	}

	@Test
	void unWebpExtendidoConLienzoEnorme_seRechaza() {
		assertThatThrownBy(() -> ValidadorImagen.procesar(Imagenes.webpExtendido(10_000, 10_000)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("megapíxeles");
		assertThat(ValidadorImagen.procesar(Imagenes.webpExtendido(100, 100)).tipoContenido()).isEqualTo("image/webp");
	}

	@Test
	void unWebpSinPerdidaOConPerdidaConDimensionesEnormes_seRechaza() {
		assertThatThrownBy(() -> ValidadorImagen.procesar(Imagenes.webpSinPerdida(16_000, 16_000)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("megapíxeles");
		assertThatThrownBy(() -> ValidadorImagen.procesar(Imagenes.webpConPerdida(16_000, 16_000)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("megapíxeles");
		assertThat(ValidadorImagen.procesar(Imagenes.webpConPerdida(640, 480)).tipoContenido()).isEqualTo("image/webp");
		assertThat(ValidadorImagen.procesar(Imagenes.webpSinPerdida(640, 480)).tipoContenido()).isEqualTo("image/webp");
	}

	// ---- tamano ----

	@Test
	void masDeCincoMegas_lanzaArchivoDemasiadoGrande_sinDecodificar() {
		byte[] enorme = new byte[ValidadorImagen.TAMANO_MAXIMO + 1];
		enorme[0] = (byte) 0xFF;
		enorme[1] = (byte) 0xD8;
		enorme[2] = (byte) 0xFF;

		assertThatThrownBy(() -> ValidadorImagen.procesar(enorme)).isInstanceOf(ArchivoDemasiadoGrandeException.class);
	}

	@Test
	void siAlRecodificarSuperaLosCincoMegas_lanzaArchivoDemasiadoGrande() {
		// ruido a calidad baja: el original cabe, pero la recodificacion a calidad alta lo hincha
		byte[] original = Imagenes.jpegRuido(3_000, 3_000, 0.3f);
		assertThat(original.length).as("el original debe caber").isLessThan(ValidadorImagen.TAMANO_MAXIMO)
				.isGreaterThan(2_000_000); // ~2,5 MB; recodificado a calidad 0,9 pasa de 6 MB

		assertThatThrownBy(() -> ValidadorImagen.procesar(original)).isInstanceOf(ArchivoDemasiadoGrandeException.class);
	}
}
