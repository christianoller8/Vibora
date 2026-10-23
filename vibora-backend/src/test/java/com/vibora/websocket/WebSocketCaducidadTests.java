package com.vibora.websocket;

import com.vibora.RelojMutable;
import com.vibora.RelojTestConfig;
import com.vibora.auth.JwtService;
import com.vibora.notificacion.NotificacionCreada;
import com.vibora.notificacion.TipoNotificacion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El JWT se valida al conectar, pero un socket abierto vive mucho mas que el token: cuando el token caduca, el
 * servidor debe dejar de empujarle cosas y cerrarlo (ERROR "No autenticado" y cierre). Reloj mutable (arranca en 2030,
 * como los tokens emitidos con el); sin esperas a planificadores: el barrido se invoca a mano y el automatico queda
 * apagado por su intervalo.
 */
@Import(RelojTestConfig.class)
@TestPropertySource(properties = "vibora.websocket.intervalo-barrido=PT1H")
class WebSocketCaducidadTests extends WebSocketTestBase {

	private static final String NO_AUTENTICADO = "No autenticado";

	@Autowired RelojMutable reloj;
	@Autowired SesionesWs sesiones;
	@Autowired JwtService jwtService;
	@Autowired ApplicationEventPublisher eventos;
	@Autowired PlatformTransactionManager transacciones;

	@BeforeEach
	void registroVacio() throws InterruptedException {
		// los sockets del test anterior ya se desconectaron: se espera a que su baja llegue al registro
		esperar("registro vacio", () -> sesiones.cantidad() == 0);
	}

	@AfterEach
	void restaurarReloj() {
		reloj.fijar(RelojTestConfig.AHORA_INICIAL);
	}

	private Instant caducidadDe(Jug j) {
		return jwtService.decodificar(j.token()).getExpiresAt();
	}

	private long ids = 100;

	/**
	 * Los sockets de otros tests de la misma clase/contexto siguen abiertos (el cliente de la base no los cierra), asi
	 * que cada test usa su propio email para contar solo sus sesiones.
	 */
	private static String unico(String nombre) {
		return nombre + "-" + java.util.UUID.randomUUID() + "@vibora.test";
	}

	/** Publica la notificacion dentro de una transaccion confirmada: dispara el push igual que el flujo real. */
	private void notificar(String email) {
		new TransactionTemplate(transacciones).executeWithoutResult(estado -> eventos.publishEvent(new NotificacionCreada(
				ids++, 0L, email, TipoNotificacion.JUGADOR_SE_APUNTO, "aviso " + ids, null, Instant.now())));
	}

	private static void esperar(String que, BooleanSupplier condicion) throws InterruptedException {
		long limite = System.nanoTime() + Duration.ofSeconds(10).toNanos();
		while (!condicion.getAsBoolean()) {
			assertThat(System.nanoTime()).as("no ocurrio a tiempo: %s", que).isLessThan(limite);
			Thread.sleep(25);
		}
	}

	@Test
	void tokenVigente_recibePush_yElBarridoNoLaToca() throws Exception {
		Jug beto = jugador(unico("beto"));
		Conexion conexion = conectarYSuscribir(beto);
		assertThat(sesiones.cantidadDe(beto.email())).isEqualTo(1);

		notificar(beto.email());
		assertThat(conexion.siguiente()).as("push con token vigente").isNotNull();

		reloj.fijar(caducidadDe(beto).minusSeconds(1));
		sesiones.barrer();

		assertThat(conexion.sesion().isConnected()).isTrue();
		assertThat(sesiones.cantidadDe(beto.email())).isEqualTo(1);
		notificar(beto.email());
		assertThat(conexion.siguiente()).as("sigue entregando justo antes de caducar").isNotNull();
	}

	@Test
	void tokenCaducado_elBarridoCierraLaSesion_yDespuesNoSeEntregaNada() throws Exception {
		Jug beto = jugador(unico("beto"));
		Conexion conexion = conectarYSuscribir(beto);
		reloj.fijar(caducidadDe(beto)); // en el instante exacto de exp ya no es valido

		sesiones.barrer();

		assertThat(conexion.escucha().errores.poll(ESPERA_SEGUNDOS, java.util.concurrent.TimeUnit.SECONDS))
				.as("el cliente es avisado antes del cierre").isEqualTo(NO_AUTENTICADO);
		esperar("cierre de la sesion", () -> !conexion.sesion().isConnected());
		esperar("limpieza del registro", () -> sesiones.cantidadDe(beto.email()) == 0);

		notificar(beto.email());
		assertThat(conexion.siguienteEnPoco()).as("nada llega tras la caducidad").isNull();
	}

	@Test
	void barrido_soloCierraLasCaducadas() throws Exception {
		Jug ana = jugador(unico("ana"));
		reloj.fijar(RelojTestConfig.AHORA_INICIAL.plus(Duration.ofMinutes(30)));
		Jug beto = jugador(unico("beto")); // su token caduca 30 min despues que el de ana
		Conexion deAna = conectarYSuscribir(ana);
		Conexion deBeto = conectarYSuscribir(beto);
		assertThat(caducidadDe(beto)).isAfter(caducidadDe(ana));

		reloj.fijar(caducidadDe(ana).plusSeconds(1));
		sesiones.barrer();

		esperar("cierre de ana", () -> !deAna.sesion().isConnected());
		assertThat(deBeto.sesion().isConnected()).as("la de beto sigue abierta").isTrue();
		assertThat(sesiones.cantidadDe(beto.email())).isEqualTo(1);
		notificar(beto.email());
		assertThat(deBeto.siguiente()).as("beto sigue recibiendo").isNotNull();
	}

	@Test
	void elPushNuncaVaAUnaSesionCaducada_aunqueNoHayaPasadoElBarrido() throws Exception {
		Jug beto = jugador(unico("beto"));
		Conexion conexion = conectarYSuscribir(beto);
		reloj.fijar(caducidadDe(beto).plusSeconds(5));

		notificar(beto.email()); // sin barrido previo

		assertThat(conexion.siguienteEnPoco()).as("no se empuja a un token caducado").isNull();
		esperar("cierre de la sesion", () -> !conexion.sesion().isConnected());
		esperar("limpieza del registro", () -> sesiones.cantidadDe(beto.email()) == 0);
	}

	@Test
	void unaSesionCaducadaNoImpideEntregarALaVigenteDelMismoUsuario() throws Exception {
		Jug beto = jugador(unico("beto"));
		Conexion vieja = conectarYSuscribir(beto);
		reloj.fijar(RelojTestConfig.AHORA_INICIAL.plus(Duration.ofMinutes(30)));
		Jug betoDeNuevo = new Jug(beto.id(), beto.email(), login(beto.email()));
		Conexion nueva = conectarYSuscribir(betoDeNuevo);

		reloj.fijar(caducidadDe(beto).plusSeconds(1));
		notificar(beto.email());

		assertThat(nueva.siguiente()).as("la sesion con token vigente recibe").isNotNull();
		assertThat(vieja.siguienteEnPoco()).as("la caducada no").isNull();
		esperar("cierre de la vieja", () -> !vieja.sesion().isConnected());
		assertThat(nueva.sesion().isConnected()).isTrue();
	}

	@Test
	void alDesconectar_laSesionSale_delRegistro() throws Exception {
		Jug beto = jugador(unico("beto"));
		int antes = sesiones.cantidad();
		Conexion conexion = conectarYSuscribir(beto);
		assertThat(sesiones.cantidad()).isEqualTo(antes + 1);

		conexion.sesion().disconnect();

		esperar("limpieza del registro", () -> sesiones.cantidad() == antes);
	}

	@Test
	void sinCierreLimpio_tambienSale_delRegistro() throws Exception {
		int antes = sesiones.cantidad();
		// WebSocket crudo: se abre y se corta sin DISCONNECT de STOMP (ni siquiera llega a autenticarse)
		WebSocketSession cruda = new StandardWebSocketClient().execute(new TextWebSocketHandler() {
		}, cabecerasHandshake(ORIGEN_PERMITIDO), URI.create(url())).get(ESPERA_SEGUNDOS, TimeUnit.SECONDS);
		esperar("alta en el registro", () -> sesiones.cantidad() == antes + 1);

		cruda.close(CloseStatus.GOING_AWAY);

		esperar("limpieza del registro", () -> sesiones.cantidad() == antes);
	}
}
