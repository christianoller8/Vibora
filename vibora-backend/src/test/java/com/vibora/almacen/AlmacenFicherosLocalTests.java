package com.vibora.almacen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Almacen en disco: round-trip, creacion perezosa del directorio y resolucion segura de claves (sin salirse de la base). */
class AlmacenFicherosLocalTests {

	@TempDir
	Path temporal;

	Path base;
	AlmacenFicherosLocal almacen;

	@BeforeEach
	void preparar() {
		base = temporal.resolve("uploads"); // NO existe todavia: el almacen debe crearlo al guardar
		almacen = new AlmacenFicherosLocal(base);
	}

	private static byte[] leer(InputStream flujo) throws IOException {
		try (flujo) {
			return flujo.readAllBytes();
		}
	}

	private List<String> nombresEn(Path directorio) throws IOException {
		try (Stream<Path> s = Files.list(directorio)) {
			return s.map(p -> p.getFileName().toString()).sorted().toList();
		}
	}

	@Test
	void guardarCreaElDirectorioSiFalta_yAbrirDevuelveLosMismosBytes() throws Exception {
		assertThat(base).doesNotExist();
		byte[] contenido = { 1, 2, 3, (byte) 0xFF, 0 };

		almacen.guardar("11111111-2222-3333-4444-555555555555", contenido);

		assertThat(base).isDirectory();
		assertThat(leer(almacen.abrir("11111111-2222-3333-4444-555555555555"))).containsExactly(contenido);
		assertThat(almacen.existe("11111111-2222-3333-4444-555555555555")).isTrue();
		assertThat(nombresEn(base)).containsExactly("11111111-2222-3333-4444-555555555555"); // sin temporales
	}

	@Test
	void guardarDosVecesLaMismaClave_sustituyeElContenido() throws Exception {
		almacen.guardar("clave", new byte[] { 1 });
		almacen.guardar("clave", new byte[] { 2, 2 });

		assertThat(leer(almacen.abrir("clave"))).containsExactly(2, 2);
		assertThat(nombresEn(base)).containsExactly("clave");
	}

	@Test
	void unaClaveInexistente_noExiste_yAbrirLanzaFicheroNoEncontrado() {
		assertThat(almacen.existe("nada")).isFalse();
		assertThatThrownBy(() -> almacen.abrir("nada")).isInstanceOf(FicheroNoEncontradoException.class);
	}

	@Test
	void borrarEliminaElFichero_yBorrarUnoInexistenteNoFalla() {
		almacen.guardar("clave", new byte[] { 1 });

		almacen.borrar("clave");
		almacen.borrar("clave");
		almacen.borrar("nunca-existio");

		assertThat(almacen.existe("clave")).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "../fuera", "..\\fuera", "a/b", "a\\b", "/absoluta", "\\absoluta", "..", ".", "", "  ",
			"C:\\fuera", "C:fuera", "x\u0000y", "sub/../../fuera", "%2e%2e/fuera" })
	void lasClavesQueNoSonUnNombreSimple_seRechazanEnTodasLasOperaciones_yNoTocanNada(String clave) throws Exception {
		Files.createDirectories(base);
		Path testigo = temporal.resolve("fuera");
		Files.writeString(testigo, "no tocar");

		assertThatThrownBy(() -> almacen.guardar(clave, new byte[] { 1 })).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> almacen.abrir(clave)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> almacen.borrar(clave)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> almacen.existe(clave)).isInstanceOf(IllegalArgumentException.class);

		assertThat(Files.readString(testigo)).isEqualTo("no tocar");
		assertThat(nombresEn(base)).isEmpty();
		assertThat(nombresEn(temporal)).containsExactlyInAnyOrder("fuera", "uploads");
	}

	@Test
	void laClaveNulaSeRechaza() {
		assertThatThrownBy(() -> almacen.guardar(null, new byte[] { 1 })).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void unaClaveConPuntosEnElMedioEsUnNombreSimpleValido() throws Exception {
		almacen.guardar("foto.v2..png", new byte[] { 7 });

		assertThat(leer(almacen.abrir("foto.v2..png"))).containsExactly(7);
	}

	@Test
	void siNoSePuedeEscribir_fallaConUncheckedIOException_ySinDejarRestos() throws Exception {
		Path ficheroEnLugarDeDirectorio = temporal.resolve("no-es-directorio");
		Files.writeString(ficheroEnLugarDeDirectorio, "soy un fichero");
		AlmacenFicherosLocal roto = new AlmacenFicherosLocal(ficheroEnLugarDeDirectorio);

		assertThatThrownBy(() -> roto.guardar("clave", new byte[] { 1 })).isInstanceOf(UncheckedIOException.class);

		assertThat(Files.readString(ficheroEnLugarDeDirectorio)).isEqualTo("soy un fichero");
		assertThat(nombresEn(temporal)).containsExactly("no-es-directorio");
	}
}
