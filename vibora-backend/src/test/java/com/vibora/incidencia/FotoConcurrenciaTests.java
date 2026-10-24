package com.vibora.incidencia;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * Concurrencia REAL de las fotos (commits de verdad, una conexion por peticion). El limite de 4 fotos es "contar y
 * luego insertar": solo aguanta porque cada subida bloquea primero la fila de la incidencia (FOR UPDATE).
 */
class FotoConcurrenciaTests extends IncidenciaSinTransaccionBase {

	static final Path CARPETA = AlmacenDeTest.carpeta("vibora-test-concurrencia-");

	@DynamicPropertySource
	static void propiedades(DynamicPropertyRegistry registro) {
		registro.add("vibora.almacen.directorio", CARPETA::toString);
	}

	@AfterEach
	void limpiarFicheros() {
		AlmacenDeTest.vaciar(CARPETA);
	}

	private static long cuantos(List<Integer> estados, int codigo) {
		return estados.stream().filter(s -> s == codigo).count();
	}

	private int subir(Persona p, long incidencia) throws Exception {
		return mvc.perform(multipart("/api/incidencias/" + incidencia + "/fotos")
						.file(new MockMultipartFile("file", "a.png", "image/png", Imagenes.png(16, 16, true)))
						.header("Authorization", p.token()))
				.andReturn().getResponse().getStatus();
	}

	private int borrar(Persona p, long incidencia, long foto) throws Exception {
		return mvc.perform(delete("/api/incidencias/" + incidencia + "/fotos/" + foto).header("Authorization", p.token()))
				.andReturn().getResponse().getStatus();
	}

	@RepeatedTest(3)
	void seisSubidasAlaVezConDosExistentes_exactamenteDosEntran_yQuedanCuatroFilasYCuatroFicheros() throws Exception {
		long incidencia = incidenciaDe(jugador, "Red rota");
		assertThat(subir(jugador, incidencia)).isEqualTo(201);
		assertThat(subir(admin, incidencia)).isEqualTo(201);

		List<Callable<Integer>> tareas = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			Persona quien = i % 2 == 0 ? jugador : admin;
			tareas.add(() -> subir(quien, incidencia));
		}
		List<Integer> estados = lanzarALaVez(tareas);

		assertThat(cuantos(estados, 201)).as("201: %s", estados).isEqualTo(2);
		assertThat(cuantos(estados, 409)).as("409: %s", estados).isEqualTo(4);
		assertThat(fotos.countByIncidenciaId(incidencia)).isEqualTo(4);
		List<String> clavesEnBd = fotos.findAll().stream().map(IncidenciaFoto::getClaveAlmacen).sorted().toList();
		assertThat(AlmacenDeTest.ficheros(CARPETA)).as("un fichero por fila, y ninguno de mas").isEqualTo(clavesEnBd);
		assertThat(eventos.findByIncidenciaIdOrderByCreadoEnAscIdAsc(incidencia).stream()
				.filter(e -> e.getTipo() == TipoEventoIncidencia.FOTO_ANADIDA)).hasSize(4);
	}

	@RepeatedTest(3)
	void dosBorradosDeLaMismaFotoAlaVez_unoGana204_elOtro404_yUnSoloEvento() throws Exception {
		long incidencia = incidenciaDe(jugador, "Red rota");
		assertThat(subir(jugador, incidencia)).isEqualTo(201);
		long foto = fotos.findAll().get(0).getId();

		List<Integer> estados = lanzarALaVez(List.<Callable<Integer>>of(
				() -> borrar(jugador, incidencia, foto),
				() -> borrar(admin, incidencia, foto)));

		assertThat(cuantos(estados, 204)).as("204: %s", estados).isEqualTo(1);
		assertThat(cuantos(estados, 404)).as("404: %s", estados).isEqualTo(1);
		assertThat(fotos.count()).isZero();
		assertThat(AlmacenDeTest.ficheros(CARPETA)).isEmpty();
		assertThat(eventos.findByIncidenciaIdOrderByCreadoEnAscIdAsc(incidencia).stream()
				.filter(e -> e.getTipo() == TipoEventoIncidencia.FOTO_ELIMINADA)).hasSize(1);
	}

	@RepeatedTest(3)
	void subidasYBorradosAlaVez_elLimiteNuncaSeSupera_yFilasYFicherosCuadran() throws Exception {
		long incidencia = incidenciaDe(jugador, "Red rota");
		for (int i = 0; i < 3; i++) {
			assertThat(subir(jugador, incidencia)).isEqualTo(201);
		}
		long aBorrar = fotos.findAll().get(0).getId();

		List<Callable<Integer>> tareas = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			tareas.add(() -> subir(jugador, incidencia));
		}
		tareas.add(() -> borrar(admin, incidencia, aBorrar));
		List<Integer> estados = lanzarALaVez(tareas);

		assertThat(estados).as("estados: %s", estados).allMatch(s -> s == 201 || s == 409 || s == 204);
		assertThat(fotos.countByIncidenciaId(incidencia)).isBetween(3L, 4L);
		List<String> clavesEnBd = fotos.findAll().stream().map(IncidenciaFoto::getClaveAlmacen).sorted().toList();
		assertThat(AlmacenDeTest.ficheros(CARPETA)).isEqualTo(clavesEnBd);
	}
}
