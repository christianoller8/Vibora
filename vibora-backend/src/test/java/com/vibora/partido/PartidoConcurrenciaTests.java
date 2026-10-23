package com.vibora.partido;

import com.vibora.cargo.Cargo;
import com.vibora.cargo.EstadoCargo;
import com.vibora.notificacion.TipoNotificacion;
import org.junit.jupiter.api.RepeatedTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Concurrencia REAL (commits de verdad): el bloqueo de la fila de reserva serializa las altas y las restricciones
 * de la BD son la red de seguridad. Nunca debe aparecer un 500.
 */
class PartidoConcurrenciaTests extends PartidoSinTransaccionBase {

	private static List<Integer> lanzarALaVez(List<Callable<Integer>> tareas) throws Exception {
		CyclicBarrier salida = new CyclicBarrier(tareas.size());
		ExecutorService pool = Executors.newFixedThreadPool(tareas.size());
		try {
			List<Future<Integer>> futuros = new ArrayList<>();
			for (Callable<Integer> tarea : tareas) {
				futuros.add(pool.submit(() -> {
					salida.await(30, TimeUnit.SECONDS); // todos salen a la vez
					return tarea.call();
				}));
			}
			List<Integer> estados = new ArrayList<>();
			for (Future<Integer> f : futuros) {
				estados.add(f.get(60, TimeUnit.SECONDS));
			}
			return estados;
		} finally {
			pool.shutdownNow();
		}
	}

	@RepeatedTest(3)
	void seisJugadoresSeApuntanALaVez_entranTres_losOtrosTresReciben409() throws Exception {
		Jug organizador = jugador("organizador@vibora.com");
		long id = partidoAbierto(organizador);
		List<Jug> aspirantes = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			aspirantes.add(jugador("aspirante" + i + "@vibora.com"));
		}

		List<Callable<Integer>> tareas = aspirantes.stream()
				.<Callable<Integer>>map(j -> () -> apuntarse(j, id)).toList();
		List<Integer> estados = lanzarALaVez(tareas);

		assertThat(estados.stream().filter(s -> s == 201).count()).as("201: %s", estados).isEqualTo(3);
		assertThat(estados.stream().filter(s -> s == 409).count()).as("409: %s", estados).isEqualTo(3);
		assertThat(participantes.findAll()).hasSize(4)
				.extracting(p -> (int) p.getPuesto()).containsExactlyInAnyOrder(1, 2, 3, 4);
		List<Cargo> vivos = cargos.findAll().stream().filter(c -> c.getEstado() == EstadoCargo.PENDIENTE).toList();
		assertThat(vivos).hasSize(3);
		assertThat(vivos).allSatisfy(c -> assertThat(c.getImporte()).isEqualByComparingTo("11.25"));
		assertThat(notificaciones.findAll().stream().filter(n -> n.getTipo() == TipoNotificacion.PARTIDO_COMPLETO))
				.hasSize(1);
		assertThat(notificaciones.findAll().stream().filter(n -> n.getTipo() == TipoNotificacion.JUGADOR_SE_APUNTO))
				.hasSize(3);
	}

	@RepeatedTest(3)
	void elMismoJugadorSeApuntaVariasVecesALaVez_soloUnaEntra() throws Exception {
		Jug organizador = jugador("organizador@vibora.com");
		long id = partidoAbierto(organizador);
		Jug impaciente = jugador("impaciente@vibora.com");

		List<Callable<Integer>> tareas = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			tareas.add(() -> apuntarse(impaciente, id));
		}
		List<Integer> estados = lanzarALaVez(tareas);

		assertThat(estados.stream().filter(s -> s == 201).count()).as("201: %s", estados).isEqualTo(1);
		assertThat(estados.stream().filter(s -> s == 409).count()).as("409: %s", estados).isEqualTo(3);
		assertThat(participantes.findAll()).hasSize(2);
		assertThat(cargos.findAll()).hasSize(1);
	}

	@RepeatedTest(3)
	void variasLlamadasSimultaneasAPagar_unaSolaTieneExito_yHayUnSoloPago() throws Exception {
		Jug organizador = jugador("organizador@vibora.com");
		long id = partidoAbierto(organizador);
		Jug deudor = jugador("deudor@vibora.com");
		assertThat(apuntarse(deudor, id)).isEqualTo(201);
		long cargoId = cargos.findAll().get(0).getId();

		List<Callable<Integer>> tareas = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			tareas.add(() -> mvc.perform(post("/api/cargos/" + cargoId + "/pagar").header("Authorization", deudor.token()))
					.andReturn().getResponse().getStatus());
		}
		List<Integer> estados = lanzarALaVez(tareas);

		assertThat(estados.stream().filter(s -> s == 200).count()).as("200: %s", estados).isEqualTo(1);
		assertThat(estados.stream().filter(s -> s == 409).count()).as("409: %s", estados).isEqualTo(3);
		assertThat(pagos.count()).isEqualTo(1);
		assertThat(cargos.findById(cargoId)).get().satisfies(c -> assertThat(c.getEstado()).isEqualTo(EstadoCargo.PAGADO));
		assertThat(notificaciones.findAll().stream().filter(n -> n.getTipo() == TipoNotificacion.PAGO_RECIBIDO))
				.hasSize(1);
	}
}
