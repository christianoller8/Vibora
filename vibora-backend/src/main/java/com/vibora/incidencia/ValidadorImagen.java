package com.vibora.incidencia;

import com.vibora.common.exception.ArchivoDemasiadoGrandeException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Validacion y saneado de las imagenes que suben los usuarios. NUNCA se fia del Content-Type ni del nombre que
 * declara el cliente: el tipo sale de los primeros bytes (JPEG, PNG o WebP; cualquier otra cosa se rechaza).
 * <p>
 * Orden de las defensas: (1) tamano maximo del fichero; (2) tipo por bytes magicos; (3) dimensiones leidas de la
 * CABECERA, sin decodificar, con un tope de 25 megapixeles (una "bomba de descompresion" es un fichero minusculo que
 * declara decenas de miles de pixeles por lado); (4) JPEG y PNG se decodifican y se VUELVEN A CODIFICAR con ImageIO,
 * lo que elimina EXIF, GPS, XMP, comentarios, perfiles y cualquier carga escondida antes de IEND/EOI (ficheros
 * "poligloto"). Los pixeles son lo unico que sobrevive.
 * <p>
 * Al quitar el EXIF se perderia la orientacion con la que el movil guarda las fotos (casi todas las fotos de movil
 * son "apaisadas con etiqueta"): por eso, antes de descartarlo, se lee la etiqueta de orientacion de los JPEG y se
 * aplica a los pixeles.
 * <p>
 * LIMITE CONOCIDO: el JDK no incluye decodificador de WebP, asi que un WebP se valida (firma RIFF/WEBP, trozo de
 * imagen reconocido, dimensiones de la cabecera, longitud coherente; se descarta lo que haya despues del final del
 * RIFF) y se guarda TAL CUAL. Sus metadatos propios (trozos EXIF/XMP, si los hay) NO se eliminan.
 */
public final class ValidadorImagen {

	public static final int TAMANO_MAXIMO = 5 * 1024 * 1024;
	public static final long PIXELES_MAXIMOS = 25_000_000L;
	public static final String FORMATO_NO_PERMITIDO = "Formato de imagen no permitido";
	public static final String DEMASIADOS_PIXELES = "La imagen es demasiado grande (máximo 25 megapíxeles)";
	public static final String DEMASIADO_PESADA = "La imagen supera el tamaño máximo de 5 MB";

	private static final String JPEG = "image/jpeg";
	private static final String PNG = "image/png";
	private static final String WEBP = "image/webp";
	private static final byte[] FIRMA_PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };
	private static final float CALIDAD_JPEG = 0.9f;

	/** Imagen ya verificada: el tipo es el detectado por los bytes y los bytes son los que se guardan. */
	public record Imagen(String tipoContenido, byte[] bytes) {
	}

	private ValidadorImagen() {
	}

	public static Imagen procesar(byte[] original) {
		if (original.length > TAMANO_MAXIMO) {
			throw new ArchivoDemasiadoGrandeException(DEMASIADO_PESADA);
		}
		String tipo = detectarTipo(original);
		if (tipo == null) {
			throw formatoNoPermitido();
		}
		return tipo.equals(WEBP) ? procesarWebp(original) : recodificar(original, tipo);
	}

	// ---- deteccion por bytes magicos ----

	private static String detectarTipo(byte[] b) {
		if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
			return JPEG;
		}
		if (b.length >= FIRMA_PNG.length && Arrays.equals(Arrays.copyOf(b, FIRMA_PNG.length), FIRMA_PNG)) {
			return PNG;
		}
		if (b.length >= 20 && ascii(b, 0, "RIFF") && ascii(b, 8, "WEBP")
				&& (ascii(b, 12, "VP8 ") || ascii(b, 12, "VP8L") || ascii(b, 12, "VP8X"))) {
			return WEBP;
		}
		return null;
	}

	private static boolean ascii(byte[] b, int desde, String texto) {
		for (int i = 0; i < texto.length(); i++) {
			if (b[desde + i] != (byte) texto.charAt(i)) {
				return false;
			}
		}
		return true;
	}

	// ---- JPEG y PNG: cabecera -> limite de pixeles -> decodificar -> recodificar ----

	private static Imagen recodificar(byte[] original, String tipo) {
		BufferedImage imagen = decodificar(original, tipo);
		if (tipo.equals(JPEG)) {
			imagen = orientar(imagen, orientacionExif(original));
		}
		byte[] bytes = codificar(imagen, tipo);
		if (bytes.length > TAMANO_MAXIMO) {
			throw new ArchivoDemasiadoGrandeException(DEMASIADO_PESADA);
		}
		return new Imagen(tipo, bytes);
	}

	private static BufferedImage decodificar(byte[] original, String tipo) {
		// En memoria, nunca con la cache en disco que usa ImageIO por defecto para los flujos
		try (ImageInputStream flujo = new MemoryCacheImageInputStream(new ByteArrayInputStream(original))) {
			Iterator<ImageReader> lectores = ImageIO.getImageReaders(flujo);
			if (!lectores.hasNext()) {
				throw formatoNoPermitido();
			}
			ImageReader lector = lectores.next();
			try {
				if (!lector.getFormatName().equalsIgnoreCase(tipo.substring("image/".length()))) {
					throw formatoNoPermitido();
				}
				lector.setInput(flujo, false, true);
				long ancho = lector.getWidth(0); // solo la cabecera
				long alto = lector.getHeight(0);
				if (ancho <= 0 || alto <= 0) {
					throw formatoNoPermitido();
				}
				if (ancho * alto > PIXELES_MAXIMOS) {
					throw new IllegalArgumentException(DEMASIADOS_PIXELES);
				}
				return lector.read(0);
			} finally {
				lector.dispose();
			}
		} catch (IOException | RuntimeException ex) {
			if (ex instanceof IllegalArgumentException && DEMASIADOS_PIXELES.equals(ex.getMessage())
					|| ex instanceof IllegalArgumentException && FORMATO_NO_PERMITIDO.equals(ex.getMessage())) {
				throw (RuntimeException) ex;
			}
			throw formatoNoPermitido(); // fichero corrupto o truncado
		}
	}

	private static byte[] codificar(BufferedImage imagen, String tipo) {
		boolean jpeg = tipo.equals(JPEG);
		ImageWriter escritor = ImageIO.getImageWritersByFormatName(jpeg ? "jpeg" : "png").next();
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		try (MemoryCacheImageOutputStream flujo = new MemoryCacheImageOutputStream(salida)) {
			escritor.setOutput(flujo);
			ImageWriteParam parametros = escritor.getDefaultWriteParam();
			if (jpeg) {
				parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
				parametros.setCompressionQuality(CALIDAD_JPEG);
			}
			// metadatos nulos: el escritor genera solo los minimos (JFIF / IHDR+IDAT+IEND)
			escritor.write(null, new IIOImage(imagen, null, null), parametros);
		} catch (IOException | RuntimeException ex) {
			throw formatoNoPermitido();
		} finally {
			escritor.dispose();
		}
		return salida.toByteArray();
	}

	// ---- orientacion EXIF (solo JPEG) ----

	/** Etiqueta 0x0112 del IFD0 del segmento APP1/Exif; 1 (normal) si no hay o no se puede leer. */
	static int orientacionExif(byte[] jpeg) {
		int i = 2; // tras SOI
		while (i + 4 <= jpeg.length && (jpeg[i] & 0xFF) == 0xFF) {
			int marcador = jpeg[i + 1] & 0xFF;
			if (marcador == 0xDA || marcador == 0xD9) {
				break; // inicio de los datos de imagen o fin: ya no hay mas segmentos de cabecera
			}
			int longitud = ((jpeg[i + 2] & 0xFF) << 8) | (jpeg[i + 3] & 0xFF);
			int finSegmento = i + 2 + longitud;
			if (longitud < 2 || finSegmento > jpeg.length) {
				break;
			}
			if (marcador == 0xE1 && longitud >= 8 && ascii(jpeg, i + 4, "Exif") && jpeg[i + 8] == 0 && jpeg[i + 9] == 0) {
				return orientacionTiff(jpeg, i + 10, finSegmento);
			}
			i = finSegmento;
		}
		return 1;
	}

	private static int orientacionTiff(byte[] b, int tiff, int fin) {
		if (tiff + 8 > fin) {
			return 1;
		}
		boolean grande;
		if (b[tiff] == 'M' && b[tiff + 1] == 'M') {
			grande = true;
		} else if (b[tiff] == 'I' && b[tiff + 1] == 'I') {
			grande = false;
		} else {
			return 1;
		}
		if (leer16(b, tiff + 2, grande) != 42) {
			return 1;
		}
		long ifd = tiff + (leer32(b, tiff + 4, grande) & 0xFFFFFFFFL);
		if (ifd < tiff || ifd + 2 > fin) {
			return 1;
		}
		int entradas = leer16(b, (int) ifd, grande);
		for (int e = 0; e < entradas; e++) {
			long entrada = ifd + 2 + 12L * e;
			if (entrada + 12 > fin) {
				return 1;
			}
			if (leer16(b, (int) entrada, grande) == 0x0112) {
				int valor = leer16(b, (int) entrada + 8, grande);
				return valor >= 1 && valor <= 8 ? valor : 1;
			}
		}
		return 1;
	}

	private static int leer16(byte[] b, int pos, boolean grande) {
		int a = b[pos] & 0xFF;
		int c = b[pos + 1] & 0xFF;
		return grande ? (a << 8) | c : (c << 8) | a;
	}

	private static int leer32(byte[] b, int pos, boolean grande) {
		return grande ? (leer16(b, pos, true) << 16) | leer16(b, pos + 2, true)
				: (leer16(b, pos + 2, false) << 16) | leer16(b, pos, false);
	}

	/** Aplica la orientacion EXIF (2..8) a los pixeles; 1 u otro valor deja la imagen como esta. */
	private static BufferedImage orientar(BufferedImage imagen, int orientacion) {
		if (orientacion < 2 || orientacion > 8) {
			return imagen;
		}
		int ancho = imagen.getWidth();
		int alto = imagen.getHeight();
		// AffineTransform(m00, m10, m01, m11, m02, m12): x' = m00*x + m01*y + m02, y' = m10*x + m11*y + m12
		AffineTransform t = switch (orientacion) {
			case 2 -> new AffineTransform(-1, 0, 0, 1, ancho, 0); // espejo horizontal
			case 3 -> new AffineTransform(-1, 0, 0, -1, ancho, alto); // 180 grados
			case 4 -> new AffineTransform(1, 0, 0, -1, 0, alto); // espejo vertical
			case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0); // transpuesta
			case 6 -> new AffineTransform(0, 1, -1, 0, alto, 0); // 90 grados horario
			case 7 -> new AffineTransform(0, -1, -1, 0, alto, ancho); // transversa
			default -> new AffineTransform(0, -1, 1, 0, 0, ancho); // 8: 90 grados antihorario
		};
		boolean intercambia = orientacion >= 5;
		BufferedImage destino = new BufferedImage(intercambia ? alto : ancho, intercambia ? ancho : alto,
				BufferedImage.TYPE_INT_RGB);
		Graphics2D g = destino.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.drawImage(imagen, t, null);
		g.dispose();
		return destino;
	}

	// ---- WebP: validar y guardar tal cual ----

	private static Imagen procesarWebp(byte[] b) {
		long finRiff = (leer32(b, 4, false) & 0xFFFFFFFFL) + 8;
		if (finRiff < 20 || finRiff > b.length) {
			throw formatoNoPermitido(); // el RIFF declara mas bytes de los que hay (fichero cortado)
		}
		long ancho;
		long alto;
		if (ascii(b, 12, "VP8X") && finRiff >= 30) {
			ancho = 1 + leer24(b, 24);
			alto = 1 + leer24(b, 27);
		} else if (ascii(b, 12, "VP8L") && finRiff >= 25 && (b[20] & 0xFF) == 0x2F) {
			int bits = leer32(b, 21, false);
			ancho = 1 + (bits & 0x3FFF);
			alto = 1 + ((bits >>> 14) & 0x3FFF);
		} else if (ascii(b, 12, "VP8 ") && finRiff >= 30 && (b[23] & 0xFF) == 0x9D && b[24] == 0x01 && b[25] == 0x2A) {
			ancho = leer16(b, 26, false) & 0x3FFF;
			alto = leer16(b, 28, false) & 0x3FFF;
		} else {
			throw formatoNoPermitido();
		}
		if (ancho <= 0 || alto <= 0) {
			throw formatoNoPermitido();
		}
		if (ancho * alto > PIXELES_MAXIMOS) {
			throw new IllegalArgumentException(DEMASIADOS_PIXELES);
		}
		// lo que haya tras el final del RIFF no es parte de la imagen
		return new Imagen(WEBP, finRiff == b.length ? b : Arrays.copyOf(b, (int) finRiff));
	}

	private static int leer24(byte[] b, int pos) {
		return (b[pos] & 0xFF) | ((b[pos + 1] & 0xFF) << 8) | ((b[pos + 2] & 0xFF) << 16);
	}

	private static IllegalArgumentException formatoNoPermitido() {
		return new IllegalArgumentException(FORMATO_NO_PERMITIDO);
	}
}
