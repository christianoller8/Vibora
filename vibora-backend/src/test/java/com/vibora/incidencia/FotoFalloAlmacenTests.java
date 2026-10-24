package com.vibora.incidencia;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** El directorio del almacen es un FICHERO normal: escribir siempre falla. Si no se puede guardar, no se persiste nada. */
class FotoFalloAlmacenTests extends IncidenciaSinTransaccionBase {

	static final Path FICHERO = ficheroNormal();

	private static Path ficheroNormal() {
		try {
			return Files.createTempFile("vibora-test-no-es-carpeta-", ".txt");
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	@DynamicPropertySource
	static void propiedades(DynamicPropertyRegistry registro) {
		registro.add("vibora.almacen.directorio", FICHERO::toString);
	}

	@Test
	void siNoSePuedeEscribirElFichero_500ConElFormatoDeErrores_yNoQuedaFilaNiEvento() throws Exception {
		long incidencia = incidenciaDe(jugador, "Red rota");

		mvc.perform(multipart("/api/incidencias/" + incidencia + "/fotos")
						.file(new MockMultipartFile("file", "a.png", "image/png", Imagenes.png(10, 10, false)))
						.header("Authorization", jugador.token()))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error").value("No se pudo guardar la imagen; inténtalo de nuevo"));

		assertThat(fotos.count()).isZero();
		assertThat(eventos.findByIncidenciaIdOrderByCreadoEnAscIdAsc(incidencia)).extracting(IncidenciaEvento::getTipo)
				.containsExactly(TipoEventoIncidencia.CREADA);
	}
}
