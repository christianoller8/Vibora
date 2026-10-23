package com.vibora.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Latidos (heart-beat) del broker simple: el CONNECTED del servidor declara 10 s / 10 s cuando el cliente tambien
 * los pide, para que las conexiones muertas (red caida, portatil dormido) se detecten y no se acumulen.
 */
class WebSocketLatidoTests extends WebSocketTestBase {

	private static final long DIEZ_SEGUNDOS = 10_000;

	/** Conecta pidiendo latidos y devuelve la cabecera heart-beat del CONNECTED. */
	private long[] latidosDeLaRespuesta(long[] pedidos) throws Exception {
		ThreadPoolTaskScheduler planificador = new ThreadPoolTaskScheduler();
		planificador.setThreadNamePrefix("test-latido-");
		planificador.initialize();
		try {
			cliente.setTaskScheduler(planificador);
			cliente.setDefaultHeartbeat(pedidos);
			Jug beto = jugador("beto@vibora.test");
			CompletableFuture<long[]> respuesta = new CompletableFuture<>();
			StompSession sesion = cliente.connectAsync(url(), cabecerasHandshake(ORIGEN_PERMITIDO),
					cabecerasConnect("Bearer " + beto.token()), new StompSessionHandlerAdapter() {
						@Override
						public void afterConnected(StompSession sesion, StompHeaders conectado) {
							respuesta.complete(conectado.getHeartbeat());
						}
					}).get(ESPERA_SEGUNDOS, TimeUnit.SECONDS);
			long[] valores = respuesta.get(ESPERA_SEGUNDOS, TimeUnit.SECONDS);
			sesion.disconnect();
			return valores;
		} finally {
			cliente.stop();
			planificador.shutdown();
		}
	}

	@Test
	void connected_declaraLosLatidosDelServidor_cuandoElClienteLosPide() throws Exception {
		assertThat(latidosDeLaRespuesta(new long[] { DIEZ_SEGUNDOS, DIEZ_SEGUNDOS }))
				.containsExactly(DIEZ_SEGUNDOS, DIEZ_SEGUNDOS);
	}
}
