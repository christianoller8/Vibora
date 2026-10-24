package com.vibora.incidencia;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Ayudas para los tests con commits reales que escriben ficheros de verdad en una carpeta temporal propia. */
final class AlmacenDeTest {

	private AlmacenDeTest() {
	}

	static Path carpeta(String prefijo) {
		try {
			return Files.createTempDirectory(prefijo);
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** Nombres de los ficheros guardados (sin temporales de escritura en curso). */
	static List<String> ficheros(Path carpeta) {
		if (!Files.isDirectory(carpeta)) {
			return List.of();
		}
		try (Stream<Path> s = Files.list(carpeta)) {
			return s.map(p -> p.getFileName().toString()).sorted().toList();
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	static void vaciar(Path carpeta) {
		if (!Files.isDirectory(carpeta)) {
			return;
		}
		try (Stream<Path> s = Files.walk(carpeta)) {
			s.filter(p -> !p.equals(carpeta)).sorted(Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException ex) {
					throw new UncheckedIOException(ex);
				}
			});
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}
}
