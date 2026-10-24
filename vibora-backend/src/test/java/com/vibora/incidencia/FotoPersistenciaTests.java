package com.vibora.incidencia;

import com.vibora.almacen.AlmacenFicheros;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * Coherencia entre la fila y el fichero con COMMITS REALES (los tests con rollback por test no pueden ver que pasa
 * "despues de confirmar"): el fichero huerfano se borra si la transaccion no se confirma, y al borrar una foto el
 * fichero desaparece una vez confirmado el borrado.
 */
class FotoPersistenciaTests extends IncidenciaSinTransaccionBase {

	static final Path CARPETA = AlmacenDeTest.carpeta("vibora-test-persistencia-");

	@DynamicPropertySource
	static void propiedades(DynamicPropertyRegistry registro) {
		registro.add("vibora.almacen.directorio", CARPETA::toString);
	}

	@Autowired AlmacenFicheros almacen;
	@Autowired FotoService fotoService;
	@Autowired PlatformTransactionManager transacciones;

	@AfterEach
	void limpiarFicheros() {
		SecurityContextHolder.clearContext();
		AlmacenDeTest.vaciar(CARPETA);
	}

	private int subir(Persona p, long incidencia) throws Exception {
		return mvc.perform(multipart("/api/incidencias/" + incidencia + "/fotos")
						.file(new MockMultipartFile("file", "a.png", "image/png", Imagenes.png(10, 10, false)))
						.header("Authorization", p.token()))
				.andReturn().getResponse().getStatus();
	}

	private void autenticarComo(String email, String rol) {
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(email, null, List.of(new SimpleGrantedAuthority("ROLE_" + rol))));
	}

	@Test
	void alBorrarUnaFoto_elFicheroSeBorraTrasConfirmarLaTransaccion() throws Exception {
		long incidencia = incidenciaDe(jugador, "Red rota");
		assertThat(subir(jugador, incidencia)).isEqualTo(201);
		assertThat(AlmacenDeTest.ficheros(CARPETA)).hasSize(1);
		long fotoId = fotos.findAll().get(0).getId();

		int estado = mvc.perform(delete("/api/incidencias/" + incidencia + "/fotos/" + fotoId)
				.header("Authorization", jugador.token())).andReturn().getResponse().getStatus();

		assertThat(estado).isEqualTo(204);
		assertThat(fotos.count()).isZero();
		assertThat(AlmacenDeTest.ficheros(CARPETA)).isEmpty();
	}

	@Test
	void siLaTransaccionConfirma_elFicheroYLaFilaSeQuedan() throws Exception {
		long incidencia = incidenciaDe(jugador, "Red rota");
		autenticarComo("jugador@vibora.com", "JUGADOR");

		new TransactionTemplate(transacciones).executeWithoutResult(
				status -> fotoService.subir(incidencia, "a.png", Imagenes.png(10, 10, false)));

		assertThat(fotos.count()).isEqualTo(1);
		assertThat(AlmacenDeTest.ficheros(CARPETA)).hasSize(1);
		assertThat(almacen.existe(fotos.findAll().get(0).getClaveAlmacen())).isTrue();
	}

	@Test
	void siLaTransaccionSeDeshaceTrasEscribirElFichero_seBorraElFicheroHuerfano_yNoQuedaFila() throws Exception {
		long incidencia = incidenciaDe(jugador, "Red rota");
		autenticarComo("jugador@vibora.com", "JUGADOR");

		new TransactionTemplate(transacciones).executeWithoutResult(status -> {
			fotoService.subir(incidencia, "a.png", Imagenes.png(10, 10, false));
			assertThat(AlmacenDeTest.ficheros(CARPETA)).as("el fichero ya esta en disco dentro de la transaccion").hasSize(1);
			status.setRollbackOnly(); // p. ej. falla algo posterior
		});

		assertThat(fotos.count()).isZero();
		assertThat(AlmacenDeTest.ficheros(CARPETA)).as("el huerfano se ha borrado").isEmpty();
		assertThat(eventos.findByIncidenciaIdOrderByCreadoEnAscIdAsc(incidencia)).extracting(IncidenciaEvento::getTipo)
				.containsExactly(TipoEventoIncidencia.CREADA);
	}

	@Test
	void siSeLanzaUnaExcepcionTrasEscribirElFichero_tambienSeBorraElHuerfano() throws Exception {
		long incidencia = incidenciaDe(jugador, "Red rota");
		autenticarComo("jugador@vibora.com", "JUGADOR");

		try {
			new TransactionTemplate(transacciones).executeWithoutResult(status -> {
				fotoService.subir(incidencia, "a.png", Imagenes.png(10, 10, false));
				throw new IllegalStateException("fallo posterior");
			});
		} catch (IllegalStateException esperada) {
			assertThat(esperada).hasMessage("fallo posterior");
		}

		assertThat(fotos.count()).isZero();
		assertThat(AlmacenDeTest.ficheros(CARPETA)).isEmpty();
	}

	@Test
	void siSeDeshaceUnBorrado_noSePierdeNiLaFilaNiElFichero() throws Exception {
		long incidencia = incidenciaDe(jugador, "Red rota");
		assertThat(subir(jugador, incidencia)).isEqualTo(201);
		long fotoId = fotos.findAll().get(0).getId();
		autenticarComo("jugador@vibora.com", "JUGADOR");

		new TransactionTemplate(transacciones).executeWithoutResult(status -> {
			fotoService.eliminar(incidencia, fotoId);
			status.setRollbackOnly();
		});

		assertThat(fotos.count()).isEqualTo(1);
		assertThat(AlmacenDeTest.ficheros(CARPETA)).hasSize(1);
	}
}
