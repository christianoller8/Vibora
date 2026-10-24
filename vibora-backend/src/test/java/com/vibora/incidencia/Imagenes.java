package com.vibora.incidencia;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.zip.CRC32;

/** Imagenes diminutas (y trampas) generadas al vuelo para los tests de fotos: nada de ficheros binarios en el repo. */
final class Imagenes {

	static final String SECRETO = "SECRETO-GPS-40.4168N-3.7038W";

	private Imagenes() {
	}

	// ---- imagenes validas ----

	static BufferedImage lienzo(int ancho, int alto, int tipo) {
		BufferedImage img = new BufferedImage(ancho, alto, tipo);
		Graphics2D g = img.createGraphics();
		g.setColor(new Color(30, 140, 220));
		g.fillRect(0, 0, ancho, alto);
		g.setColor(new Color(240, 200, 40));
		g.fillRect(0, 0, Math.max(1, ancho / 2), Math.max(1, alto / 2));
		g.dispose();
		return img;
	}

	static byte[] jpeg(int ancho, int alto) {
		return codificar(lienzo(ancho, alto, BufferedImage.TYPE_INT_RGB), "jpeg", 0.9f);
	}

	/** Mitad izquierda roja y mitad derecha azul (para comprobar giros). */
	static byte[] jpegRojoIzquierdaAzulDerecha(int ancho, int alto) {
		BufferedImage img = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setColor(Color.RED);
		g.fillRect(0, 0, ancho / 2, alto);
		g.setColor(Color.BLUE);
		g.fillRect(ancho / 2, 0, ancho - ancho / 2, alto);
		g.dispose();
		return codificar(img, "jpeg", 0.95f);
	}

	static byte[] png(int ancho, int alto, boolean conAlfa) {
		return codificar(lienzo(ancho, alto, conAlfa ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB), "png", 1f);
	}

	static byte[] pngGris(int ancho, int alto) {
		return codificar(new BufferedImage(ancho, alto, BufferedImage.TYPE_BYTE_GRAY), "png", 1f);
	}

	/** Ruido aleatorio (casi incomprimible). Calidad baja a proposito para que el original sea pequeno. */
	static byte[] jpegRuido(int ancho, int alto, float calidad) {
		BufferedImage img = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
		Random azar = new Random(7);
		for (int y = 0; y < alto; y++) {
			for (int x = 0; x < ancho; x++) {
				img.setRGB(x, y, azar.nextInt(0x1000000));
			}
		}
		return codificar(img, "jpeg", calidad);
	}

	static byte[] codificar(BufferedImage img, String formato, float calidad) {
		try {
			ImageWriter escritor = ImageIO.getImageWritersByFormatName(formato).next();
			ByteArrayOutputStream salida = new ByteArrayOutputStream();
			try (MemoryCacheImageOutputStream flujo = new MemoryCacheImageOutputStream(salida)) {
				escritor.setOutput(flujo);
				ImageWriteParam parametros = escritor.getDefaultWriteParam();
				if (parametros.canWriteCompressed() && formato.equals("jpeg")) {
					parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
					parametros.setCompressionQuality(calidad);
				}
				escritor.write(null, new IIOImage(img, null, null), parametros);
			} finally {
				escritor.dispose();
			}
			return salida.toByteArray();
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	static BufferedImage leer(byte[] bytes) {
		try {
			return ImageIO.read(new ByteArrayInputStream(bytes));
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	// ---- trampas: metadatos y cargas ocultas ----

	/** Inserta tras SOI un segmento APP1/Exif con la orientacion dada y un texto "secreto" (como un GPS). */
	static byte[] conExif(byte[] jpeg, int orientacion, String secreto) {
		byte[] relleno = secreto.getBytes(StandardCharsets.US_ASCII);
		ByteBuffer tiff = ByteBuffer.allocate(8 + 2 + 12 + 4 + relleno.length).order(ByteOrder.BIG_ENDIAN);
		tiff.put((byte) 'M').put((byte) 'M').putShort((short) 42).putInt(8); // cabecera TIFF, IFD0 en el offset 8
		tiff.putShort((short) 1); // una entrada
		tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientacion).putShort((short) 0);
		tiff.putInt(0); // sin mas IFD
		tiff.put(relleno);
		return insertarApp1(jpeg, tiff.array());
	}

	/** APP1 "Exif" con basura como contenido TIFF: la orientacion no se puede leer y debe tratarse como 1. */
	static byte[] conExifMalformado(byte[] jpeg) {
		return insertarApp1(jpeg, ("XX-no-es-tiff-" + SECRETO).getBytes(StandardCharsets.US_ASCII));
	}

	private static byte[] insertarApp1(byte[] jpeg, byte[] tiff) {
		byte[] exif = "Exif\0\0".getBytes(StandardCharsets.ISO_8859_1);
		int longitud = 2 + exif.length + tiff.length;
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		salida.write(jpeg, 0, 2); // SOI
		salida.write(0xFF);
		salida.write(0xE1);
		salida.write(longitud >> 8);
		salida.write(longitud & 0xFF);
		salida.writeBytes(exif);
		salida.writeBytes(tiff);
		salida.write(jpeg, 2, jpeg.length - 2);
		return salida.toByteArray();
	}

	static byte[] conCargaTrasElFinal(byte[] imagen, String carga) {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		salida.writeBytes(imagen);
		salida.writeBytes(carga.getBytes(StandardCharsets.US_ASCII));
		return salida.toByteArray();
	}

	/** Inserta un trozo tEXt con el secreto justo despues de IHDR y deja basura tras IEND. */
	static byte[] pngConTextoYBasura(byte[] png, String secreto, String basura) {
		int finIhdr = 8 + 4 + 4 + 13 + 4;
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		salida.write(png, 0, finIhdr);
		salida.writeBytes(trozoPng("tEXt", ("Comment\0" + secreto).getBytes(StandardCharsets.ISO_8859_1)));
		salida.write(png, finIhdr, png.length - finIhdr);
		salida.writeBytes(basura.getBytes(StandardCharsets.US_ASCII));
		return salida.toByteArray();
	}

	/** PNG con solo la firma, IHDR (dimensiones inventadas) e IEND: sirve para probar el limite SIN decodificar nada. */
	static byte[] pngSoloCabecera(int ancho, int alto) {
		ByteBuffer ihdr = ByteBuffer.allocate(13);
		ihdr.putInt(ancho).putInt(alto).put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0);
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		salida.writeBytes(new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' });
		salida.writeBytes(trozoPng("IHDR", ihdr.array()));
		salida.writeBytes(trozoPng("IEND", new byte[0]));
		return salida.toByteArray();
	}

	private static byte[] trozoPng(String tipo, byte[] datos) {
		byte[] nombre = tipo.getBytes(StandardCharsets.US_ASCII);
		CRC32 crc = new CRC32();
		crc.update(nombre);
		crc.update(datos);
		ByteBuffer trozo = ByteBuffer.allocate(4 + 4 + datos.length + 4);
		trozo.putInt(datos.length).put(nombre).put(datos).putInt((int) crc.getValue());
		return trozo.array();
	}

	// ---- WebP hecho a mano (el JDK no sabe codificarlo ni decodificarlo: solo cabeceras coherentes) ----

	/** VP8L (sin perdida) con las dimensiones dadas. */
	static byte[] webpSinPerdida(int ancho, int alto) {
		ByteBuffer datos = ByteBuffer.allocate(5 + 8).order(ByteOrder.LITTLE_ENDIAN);
		datos.put((byte) 0x2F).putInt((ancho - 1) | ((alto - 1) << 14));
		return riff(trozoWebp("VP8L", datos.array()));
	}

	/** VP8 (con perdida) con las dimensiones dadas. */
	static byte[] webpConPerdida(int ancho, int alto) {
		ByteBuffer datos = ByteBuffer.allocate(10 + 6).order(ByteOrder.LITTLE_ENDIAN);
		datos.put((byte) 0x10).put((byte) 0).put((byte) 0); // etiqueta del fotograma: clave
		datos.put((byte) 0x9D).put((byte) 0x01).put((byte) 0x2A);
		datos.putShort((short) ancho).putShort((short) alto);
		return riff(trozoWebp("VP8 ", datos.array()));
	}

	/** VP8X (extendido) con el lienzo dado, seguido de un trozo VP8L cualquiera. */
	static byte[] webpExtendido(int ancho, int alto) {
		ByteBuffer cabecera = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN);
		cabecera.put((byte) 0x08); // flag de EXIF
		cabecera.put((byte) 0).put((byte) 0).put((byte) 0);
		cabecera.put((byte) ((ancho - 1) & 0xFF)).put((byte) (((ancho - 1) >> 8) & 0xFF)).put((byte) (((ancho - 1) >> 16) & 0xFF));
		cabecera.put((byte) ((alto - 1) & 0xFF)).put((byte) (((alto - 1) >> 8) & 0xFF)).put((byte) (((alto - 1) >> 16) & 0xFF));
		ByteArrayOutputStream trozos = new ByteArrayOutputStream();
		trozos.writeBytes(trozoWebp("VP8X", cabecera.array()));
		trozos.writeBytes(trozoWebp("VP8L", new byte[] { 0x2F, 0, 0, 0, 0, 0, 0, 0 }));
		trozos.writeBytes(trozoWebp("EXIF", SECRETO.getBytes(StandardCharsets.US_ASCII)));
		return riff(trozos.toByteArray());
	}

	private static byte[] trozoWebp(String tipo, byte[] datos) {
		ByteBuffer trozo = ByteBuffer.allocate(8 + datos.length + (datos.length % 2)).order(ByteOrder.LITTLE_ENDIAN);
		trozo.put(tipo.getBytes(StandardCharsets.US_ASCII)).putInt(datos.length).put(datos);
		return trozo.array();
	}

	static byte[] riff(byte[] trozos) {
		ByteBuffer riff = ByteBuffer.allocate(12 + trozos.length).order(ByteOrder.LITTLE_ENDIAN);
		riff.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(4 + trozos.length)
				.put("WEBP".getBytes(StandardCharsets.US_ASCII)).put(trozos);
		return riff.array();
	}

	// ---- ayudas de comprobacion ----

	static boolean contiene(byte[] bytes, String texto) {
		return indiceDe(bytes, texto.getBytes(StandardCharsets.ISO_8859_1)) >= 0;
	}

	/** Hay un marcador JPEG FF xx en el fichero (cualquier posicion). */
	static boolean tieneMarcadorJpeg(byte[] bytes, int marcador) {
		for (int i = 0; i + 1 < bytes.length; i++) {
			if ((bytes[i] & 0xFF) == 0xFF && (bytes[i + 1] & 0xFF) == marcador) {
				return true;
			}
		}
		return false;
	}

	private static int indiceDe(byte[] pajar, byte[] aguja) {
		for (int i = 0; i + aguja.length <= pajar.length; i++) {
			int j = 0;
			while (j < aguja.length && pajar[i + j] == aguja[j]) {
				j++;
			}
			if (j == aguja.length) {
				return i;
			}
		}
		return -1;
	}
}
