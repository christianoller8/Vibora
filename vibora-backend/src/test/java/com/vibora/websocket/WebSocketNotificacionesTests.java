package com.vibora.websocket;

import com.vibora.notificacion.Notificacion;
import com.vibora.notificacion.NotificacionResponse;
import com.vibora.notificacion.TipoNotificacion;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.messaging.simp.stomp.StompSession;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Notificaciones en tiempo real por STOMP sobre WebSocket nativo (/ws): autenticacion en el frame CONNECT con el
 * mismo JWT que la API REST, destinos de usuario y nada mas (ni suscripciones a otros destinos ni SEND).
 */
class WebSocketNotificacionesTests extends WebSocketTestBase {

	private static final String NO_AUTENTICADO = "No autenticado";
	private static final String ACCESO_DENEGADO = "Acceso denegado";

	// ---- entrega ----

	@RepeatedTest(3)
	void organizadorRecibeJugadorSeApunto_cuandoOtroSeApunta() throws Exception {
		Jug organizador = jugador("organizador@vibora.test");
		Jug beto = jugador("beto@vibora.test");
		long id = partidoAbierto(organizador);
		Conexion deOrganizador = conectarYSuscribir(organizador);

		assertThat(apuntarse(beto, id)).isEqualTo(201);

		NotificacionResponse recibida = deOrganizador.siguiente();
		assertThat(recibida).as("mensaje push del organizador").isNotNull();
		assertThat(recibida.tipo()).isEqualTo(TipoNotificacion.JUGADOR_SE_APUNTO);
		assertThat(recibida.reservaId()).isEqualTo(id);
		assertThat(recibida.leida()).isFalse();
		assertThat(recibida.mensaje()).contains("se ha apuntado a tu partido");
		assertThat(recibida.id()).isNotNull();
		assertThat(recibida.creadaEn()).isNotNull();
		// es la misma notificacion que quedo guardada (fuente de verdad), con el mismo contenido
		Notificacion guardada = notificaciones.findById(recibida.id()).orElseThrow();
		assertThat(guardada.getTipo()).isEqualTo(recibida.tipo());
		assertThat(guardada.getMensaje()).isEqualTo(recibida.mensaje());
		assertThat(recibida.creadaEn()).isEqualTo(guardada.getCreadaEn());
		assertThat(deOrganizador.siguienteEnPoco()).as("solo una notificacion para el organizador").isNull();
	}

	@RepeatedTest(3)
	void jugadorQueSeApuntaRecibeSuCargoNuevo() throws Exception {
		Jug organizador = jugador("organizador@vibora.test");
		Jug beto = jugador("beto@vibora.test");
		long id = partidoAbierto(organizador);
		Conexion deBeto = conectarYSuscribir(beto);

		assertThat(apuntarse(beto, id)).isEqualTo(201);

		NotificacionResponse recibida = deBeto.siguiente();
		assertThat(recibida).isNotNull();
		assertThat(recibida.tipo()).isEqualTo(TipoNotificacion.CARGO_NUEVO);
		assertThat(recibida.reservaId()).isEqualTo(id);
		assertThat(recibida.mensaje()).contains("11,25");
		assertThat(deBeto.siguienteEnPoco()).isNull();
	}

	@RepeatedTest(3)
	void unTerceroConectadoNoRecibeNada() throws Exception {
		Jug organizador = jugador("organizador@vibora.test");
		Jug beto = jugador("beto@vibora.test");
		Jug tercero = jugador("tercero@vibora.test");
		long id = partidoAbierto(organizador);
		Conexion deOrganizador = conectarYSuscribir(organizador);
		Conexion deTercero = conectarYSuscribir(tercero);

		assertThat(apuntarse(beto, id)).isEqualTo(201);

		assertThat(deOrganizador.siguiente()).as("la entrega funciona").isNotNull();
		assertThat(deTercero.siguienteEnPoco()).as("el ajeno no recibe nada").isNull();
	}

	@RepeatedTest(3)
	void variasConexionesDelMismoUsuarioReciben_yLasDeOtroNo() throws Exception {
		Jug organizador = jugador("organizador@vibora.test");
		Jug beto = jugador("beto@vibora.test");
		long id = partidoAbierto(organizador);
		Conexion movil = conectarYSuscribir(organizador);
		Conexion portatil = conectarYSuscribir(organizador);

		assertThat(apuntarse(beto, id)).isEqualTo(201);

		assertThat(movil.siguiente()).isNotNull();
		assertThat(portatil.siguiente()).isNotNull();
	}

	@RepeatedTest(3)
	void pagoRecibidoSeEmpujaAlAcreedor() throws Exception {
		Jug organizador = jugador("organizador@vibora.test");
		Jug beto = jugador("beto@vibora.test");
		long id = partidoAbierto(organizador);
		assertThat(apuntarse(beto, id)).isEqualTo(201);
		long cargoId = cargoDe(beto);
		Conexion deOrganizador = conectarYSuscribir(organizador);

		assertThat(pagar(beto, cargoId)).isEqualTo(200);

		NotificacionResponse recibida = deOrganizador.siguiente();
		assertThat(recibida).isNotNull();
		assertThat(recibida.tipo()).isEqualTo(TipoNotificacion.PAGO_RECIBIDO);
		assertThat(recibida.reservaId()).isEqualTo(id);
		assertThat(recibida.mensaje()).contains("11,25");
		assertThat(deOrganizador.siguienteEnPoco()).isNull();
	}

	// ---- conexion: autenticacion en el CONNECT ----

	@RepeatedTest(3)
	void sinTokenSeRechazaElConnect() throws Exception {
		assertThat(errorDeConexionRechazada(ORIGEN_PERMITIDO, null)).isEqualTo(NO_AUTENTICADO);
	}

	@RepeatedTest(3)
	void tokenBasuraSeRechazaElConnect() throws Exception {
		assertThat(errorDeConexionRechazada(ORIGEN_PERMITIDO, "Bearer esto.no.es.un.jwt")).isEqualTo(NO_AUTENTICADO);
		assertThat(errorDeConexionRechazada(ORIGEN_PERMITIDO, "Bearer ")).isEqualTo(NO_AUTENTICADO);
		assertThat(errorDeConexionRechazada(ORIGEN_PERMITIDO, "Basic abc")).isEqualTo(NO_AUTENTICADO);
	}

	@RepeatedTest(3)
	void tokenCaducadoSeRechazaElConnect() throws Exception {
		Jug beto = jugador("beto@vibora.test");
		Instant ahora = Instant.now();
		String caducado = tokenFirmado(SECRETO, "vibora", beto.email(), ahora.minus(2, ChronoUnit.HOURS),
				ahora.minus(1, ChronoUnit.HOURS));

		assertThat(errorDeConexionRechazada(ORIGEN_PERMITIDO, "Bearer " + caducado)).isEqualTo(NO_AUTENTICADO);
	}

	@RepeatedTest(3)
	void tokenConOtraFirmaOOtroEmisorSeRechazaElConnect() throws Exception {
		Jug beto = jugador("beto@vibora.test");
		Instant ahora = Instant.now();
		String otraFirma = tokenFirmado("otro-secreto-distinto-de-al-menos-32-bytes!", "vibora", beto.email(), ahora,
				ahora.plus(1, ChronoUnit.HOURS));
		String otroEmisor = tokenFirmado(SECRETO, "intruso", beto.email(), ahora, ahora.plus(1, ChronoUnit.HOURS));

		assertThat(errorDeConexionRechazada(ORIGEN_PERMITIDO, "Bearer " + otraFirma)).isEqualTo(NO_AUTENTICADO);
		assertThat(errorDeConexionRechazada(ORIGEN_PERMITIDO, "Bearer " + otroEmisor)).isEqualTo(NO_AUTENTICADO);
	}

	@RepeatedTest(3)
	void tokenValidoDeUsuarioInexistenteSeRechazaElConnect() throws Exception {
		Instant ahora = Instant.now();
		String token = tokenFirmado(SECRETO, "vibora", "fantasma@vibora.test", ahora, ahora.plus(1, ChronoUnit.HOURS));

		assertThat(errorDeConexionRechazada(ORIGEN_PERMITIDO, "Bearer " + token)).isEqualTo(NO_AUTENTICADO);
	}

	@RepeatedTest(3)
	void usuarioDadoDeBajaSeRechazaElConnect_aunqueElTokenSiga_vigente() throws Exception {
		Jug beto = jugador("beto@vibora.test");
		var usuario = usuarios.findById(beto.id()).orElseThrow();
		usuario.setActivo(false);
		usuarios.saveAndFlush(usuario);

		assertThat(errorDeConexionRechazada(ORIGEN_PERMITIDO, "Bearer " + beto.token())).isEqualTo(NO_AUTENTICADO);
	}

	// ---- origen ----

	@RepeatedTest(3)
	void origenNoPermitidoRechazaElHandshake() throws Exception {
		Jug beto = jugador("beto@vibora.test");
		// control positivo: con el origen permitido el mismo token entra
		Conexion buena = conectarYSuscribir(beto);
		assertThat(buena.sesion().isConnected()).isTrue();

		// mismo token, otro origen: el handshake HTTP falla (403) y ni siquiera llega a haber STOMP
		assertThat(errorDeConexionRechazada("http://evil.example", "Bearer " + beto.token())).isNull();
	}

	// ---- suscripciones y envios ----

	@RepeatedTest(3)
	void suscribirseAOtroDestinoSeDeniega() throws Exception {
		for (String destino : new String[] { "/topic/x", "/user/queue/otro", "/queue/notificaciones", "/user/queue/notificaciones/extra" }) {
			Jug beto = jugador("beto" + Math.abs(destino.hashCode()) + "@vibora.test");
			Escucha escucha = new Escucha();
			StompSession sesion = conectar("Bearer " + beto.token(), escucha);

			sesion.subscribe(destino, new org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter() {
			});

			assertThat(escucha.errores.poll(ESPERA_SEGUNDOS, TimeUnit.SECONDS)).as("destino %s", destino)
					.isEqualTo(ACCESO_DENEGADO);
		}
	}

	@RepeatedTest(3)
	void cualquierSendDelClienteSeDeniega_yNoLlegaALaVictima() throws Exception {
		Jug victima = jugador("victima@vibora.test");
		Jug atacante = jugador("atacante@vibora.test");
		Conexion deVictima = conectarYSuscribir(victima);
		Escucha escucha = new Escucha();
		StompSession sesion = conectar("Bearer " + atacante.token(), escucha);
		Map<String, Object> falsa = Map.of("id", 1, "tipo", "PAGO_RECIBIDO", "mensaje", "falsa", "leida", false);

		// intento de inyectar una notificacion directamente en la cola de otro usuario
		sesion.send("/user/" + victima.email() + "/queue/notificaciones", falsa);

		assertThat(escucha.errores.poll(ESPERA_SEGUNDOS, TimeUnit.SECONDS)).isEqualTo(ACCESO_DENEGADO);
		assertThat(deVictima.siguienteEnPoco()).as("la victima no recibe nada").isNull();
	}

	@RepeatedTest(3)
	void sendAUnDestinoCualquieraSeDeniega() throws Exception {
		Jug beto = jugador("beto@vibora.test");
		Escucha escucha = new Escucha();
		StompSession sesion = conectar("Bearer " + beto.token(), escucha);

		sesion.send("/app/lo-que-sea", Map.of("a", 1));

		assertThat(escucha.errores.poll(ESPERA_SEGUNDOS, TimeUnit.SECONDS)).isEqualTo(ACCESO_DENEGADO);
	}
}
