package com.vibora.websocket;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.vibora.PostgresTestBase;
import com.vibora.TestData;
import com.vibora.cargo.CargoRepository;
import com.vibora.cargo.PagoRepository;
import com.vibora.club.Club;
import com.vibora.club.ClubRepository;
import com.vibora.notificacion.NotificacionRepository;
import com.vibora.notificacion.NotificacionResponse;
import com.vibora.notificacion.TipoNotificacion;
import com.vibora.partido.ParticipanteRepository;
import com.vibora.pista.Pista;
import com.vibora.pista.PistaRepository;
import com.vibora.reserva.ReservaRepository;
import com.vibora.tarifa.TarifaFranja;
import com.vibora.tarifa.TarifaFranjaRepository;
import com.vibora.tarifa.TipoTarifa;
import com.vibora.usuario.Rol;
import com.vibora.usuario.Usuario;
import com.vibora.usuario.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Base de los tests de WebSocket: servidor real en un puerto aleatorio, commits REALES (los datos se crean ya
 * confirmados y se borran en @AfterEach en orden de FK), reloj real, REST por MockMvc y STOMP por el cliente de
 * Spring. Los tokens salen del endpoint de login real. Casilla: dentro de 30 dias a las 11:00 (Madrid), 45.00 en
 * total (cuota 11.25).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"vibora.jwt.secret=" + WebSocketTestBase.SECRETO,
		"vibora.websocket.origenes-permitidos=http://localhost:4200" })
@AutoConfigureMockMvc
abstract class WebSocketTestBase extends PostgresTestBase {

	static final String SECRETO = "secreto-de-test-con-al-menos-32-bytes-ok!";
	static final String PASSWORD = "contraseña-segura";
	static final String ORIGEN_PERMITIDO = "http://localhost:4200";
	static final String DESTINO = "/user/queue/notificaciones";
	static final long ESPERA_SEGUNDOS = 5;
	static final long ID_SONDA = -1L;
	static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

	@LocalServerPort int puerto;
	@Autowired MockMvc mvc;
	@Autowired ClubRepository clubs;
	@Autowired UsuarioRepository usuarios;
	@Autowired PistaRepository pistas;
	@Autowired TarifaFranjaRepository tarifas;
	@Autowired ReservaRepository reservas;
	@Autowired ParticipanteRepository participantes;
	@Autowired CargoRepository cargos;
	@Autowired PagoRepository pagos;
	@Autowired NotificacionRepository notificaciones;
	@Autowired PasswordEncoder passwordEncoder;
	@Autowired SimpMessagingTemplate mensajeria;

	Club club;
	Pista pista;
	Instant inicio;
	WebSocketStompClient cliente;
	final java.util.List<StompSession> abiertas = new java.util.concurrent.CopyOnWriteArrayList<>();

	record Jug(Long id, String email, String token) {
	}

	@BeforeEach
	void crearDatosBaseYCliente() {
		club = clubs.saveAndFlush(TestData.club());
		pista = pistas.saveAndFlush(TestData.pista(club, "Pista 1"));
		LocalDate fecha = LocalDate.now(MADRID).plusDays(30);
		inicio = fecha.atTime(11, 0).atZone(MADRID).toInstant();
		TarifaFranja t = TestData.tarifa(club, fecha.getDayOfWeek(), TipoTarifa.NORMAL);
		t.setHoraInicio(LocalTime.of(8, 0));
		t.setHoraFin(LocalTime.of(23, 0));
		t.setPrecioHora(new BigDecimal("30.00"));
		tarifas.saveAndFlush(t);

		cliente = new WebSocketStompClient(new StandardWebSocketClient());
		cliente.setMessageConverter(new JacksonJsonMessageConverter());
	}

	@AfterEach
	void limpiarBaseYCliente() {
		// cliente.stop() no cierra los sockets abiertos: se desconectan uno a uno para que no queden vivos entre tests
		for (StompSession sesion : abiertas) {
			try {
				sesion.disconnect();
			} catch (RuntimeException yaCerrada) {
				// IllegalStateException / MessageDeliveryException: el servidor la cerro (p. ej. tras un ERROR) o se
				// esta cerrando entre medias. Es una limpieza a mejor esfuerzo: nunca debe tumbar el @AfterEach.
			}
		}
		abiertas.clear();
		cliente.stop();
		// orden de FK: notificacion/pago -> cargo -> participante -> reserva -> tarifa/pista -> usuario -> club
		notificaciones.deleteAllInBatch();
		pagos.deleteAllInBatch();
		cargos.deleteAllInBatch();
		participantes.deleteAllInBatch();
		reservas.deleteAllInBatch();
		tarifas.deleteAllInBatch();
		pistas.deleteAllInBatch();
		usuarios.deleteAllInBatch();
		clubs.deleteAllInBatch();
	}

	// ---- datos y REST ----

	Jug jugador(String email) throws Exception {
		Usuario u = TestData.usuario(email, Rol.JUGADOR, null);
		u.setPasswordHash(passwordEncoder.encode(PASSWORD));
		u = usuarios.saveAndFlush(u);
		return new Jug(u.getId(), email, login(email));
	}

	String login(String email) throws Exception {
		String cuerpo = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
				.andReturn().getResponse().getContentAsString();
		Matcher m = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(cuerpo);
		assertThat(m.find()).as("login de %s: %s", email, cuerpo).isTrue();
		return m.group(1);
	}

	/** Reserva la casilla como organizador y la abre. Devuelve el id de la reserva. */
	long partidoAbierto(Jug organizador) throws Exception {
		String cuerpo = mvc.perform(post("/api/clubes/" + club.getId() + "/pistas/" + pista.getId() + "/reservas")
						.header("Authorization", "Bearer " + organizador.token()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"inicio\":\"" + inicio + "\"}"))
				.andReturn().getResponse().getContentAsString();
		Matcher m = Pattern.compile("\"id\":(\\d+)").matcher(cuerpo);
		assertThat(m.find()).as("reserva: %s", cuerpo).isTrue();
		long id = Long.parseLong(m.group(1));
		int estado = mvc.perform(put("/api/reservas/" + id + "/abierta").header("Authorization", "Bearer " + organizador.token())
				.contentType(MediaType.APPLICATION_JSON).content("{\"abierta\":true}")).andReturn().getResponse().getStatus();
		assertThat(estado).isEqualTo(200);
		return id;
	}

	int apuntarse(Jug j, long reservaId) throws Exception {
		return mvc.perform(post("/api/reservas/" + reservaId + "/apuntarse").header("Authorization", "Bearer " + j.token()))
				.andReturn().getResponse().getStatus();
	}

	/** Id del primer cargo (el mas reciente) que debe el jugador. */
	long cargoDe(Jug deudor) throws Exception {
		String cuerpo = mvc.perform(get("/api/cargos/mios").header("Authorization", "Bearer " + deudor.token()))
				.andReturn().getResponse().getContentAsString();
		Matcher m = Pattern.compile("\"id\":(\\d+)").matcher(cuerpo);
		assertThat(m.find()).as("cargos: %s", cuerpo).isTrue();
		return Long.parseLong(m.group(1));
	}

	int pagar(Jug deudor, long cargoId) throws Exception {
		return mvc.perform(post("/api/cargos/" + cargoId + "/pagar").header("Authorization", "Bearer " + deudor.token()))
				.andReturn().getResponse().getStatus();
	}

	/** JWT firmado a mano (mismo algoritmo) para casos que el login no puede producir. */
	static String tokenFirmado(String secreto, String emisor, String email, Instant emitido, Instant expira) {
		var clave = new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		var codificador = new NimbusJwtEncoder(new ImmutableSecret<>(clave));
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer(emisor).subject(email).claim("rol", "JUGADOR")
				.issuedAt(emitido).expiresAt(expira).build();
		return codificador.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
	}

	// ---- STOMP ----

	String url() {
		return "ws://localhost:" + puerto + "/ws";
	}

	static WebSocketHttpHeaders cabecerasHandshake(String origen) {
		WebSocketHttpHeaders cabeceras = new WebSocketHttpHeaders();
		if (origen != null) {
			cabeceras.setOrigin(origen);
		}
		return cabeceras;
	}

	static StompHeaders cabecerasConnect(String autorizacion) {
		StompHeaders cabeceras = new StompHeaders();
		if (autorizacion != null) {
			cabeceras.add("Authorization", autorizacion);
		}
		return cabeceras;
	}

	/** Recoge lo que el servidor le dice a una sesion: ERROR (cabecera message) y cierre/errores de transporte. */
	static final class Escucha extends StompSessionHandlerAdapter {
		final BlockingQueue<String> errores = new LinkedBlockingQueue<>();
		final CompletableFuture<Throwable> transporte = new CompletableFuture<>();

		@Override
		public void handleFrame(StompHeaders cabeceras, Object cuerpo) {
			errores.add(String.valueOf(cabeceras.getFirst("message")));
		}

		@Override
		public void handleTransportError(StompSession sesion, Throwable ex) {
			transporte.complete(ex);
		}

		@Override
		public void handleException(StompSession sesion, org.springframework.messaging.simp.stomp.StompCommand comando,
				StompHeaders cabeceras, byte[] cuerpo, Throwable ex) {
			errores.add("excepcion: " + ex);
		}
	}

	/** Una conexion STOMP abierta y suscrita a las notificaciones del usuario. */
	record Conexion(StompSession sesion, Escucha escucha, BlockingQueue<NotificacionResponse> recibidas) {
		NotificacionResponse siguiente() throws InterruptedException {
			return recibidas.poll(ESPERA_SEGUNDOS, TimeUnit.SECONDS);
		}

		NotificacionResponse siguienteEnPoco() throws InterruptedException {
			return recibidas.poll(1500, TimeUnit.MILLISECONDS);
		}
	}

	/** Conecta con el token (como lo haria el navegador en el frame CONNECT) o falla con la excepcion del intento. */
	StompSession conectar(String autorizacion, Escucha escucha) throws Exception {
		StompSession sesion = cliente
				.connectAsync(url(), cabecerasHandshake(ORIGEN_PERMITIDO), cabecerasConnect(autorizacion), escucha)
				.get(ESPERA_SEGUNDOS, TimeUnit.SECONDS);
		abiertas.add(sesion);
		return sesion;
	}

	/** Conecta y se suscribe a /user/queue/notificaciones; vuelve cuando la suscripcion ya esta registrada en el broker. */
	Conexion conectarYSuscribir(Jug j) throws Exception {
		Escucha escucha = new Escucha();
		StompSession sesion = conectar("Bearer " + j.token(), escucha);
		BlockingQueue<NotificacionResponse> cola = new LinkedBlockingQueue<>();
		CountDownLatch sondaRecibida = new CountDownLatch(1);
		sesion.subscribe(DESTINO, new StompFrameHandler() {
			@Override
			public Type getPayloadType(StompHeaders cabeceras) {
				return NotificacionResponse.class;
			}

			@Override
			public void handleFrame(StompHeaders cabeceras, Object cuerpo) {
				NotificacionResponse n = (NotificacionResponse) cuerpo;
				if (n.id() == ID_SONDA) {
					sondaRecibida.countDown();
				} else {
					cola.add(n);
				}
			}
		});
		// El broker simple no manda RECEIPT: para saber que la suscripcion ya esta registrada se le envia, desde el
		// servidor, una sonda (id -1, nunca llega a la cola) hasta que vuelve.
		NotificacionResponse sonda = new NotificacionResponse(ID_SONDA, TipoNotificacion.JUGADOR_SE_APUNTO, "sonda", null,
				false, Instant.now());
		long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(ESPERA_SEGUNDOS);
		while (!sondaRecibida.await(100, TimeUnit.MILLISECONDS)) {
			assertThat(System.nanoTime()).as("la suscripcion de %s no quedo registrada", j.email()).isLessThan(limite);
			mensajeria.convertAndSendToUser(j.email(), "/queue/notificaciones", sonda);
		}
		return new Conexion(sesion, escucha, cola);
	}

	/** Resultado de un intento de conexion que debe fallar: el ERROR de STOMP recibido (null si no hubo). */
	String errorDeConexionRechazada(String origen, String autorizacion) throws Exception {
		Escucha escucha = new Escucha();
		CompletableFuture<StompSession> intento = cliente.connectAsync(url(), cabecerasHandshake(origen),
				cabecerasConnect(autorizacion), escucha);
		try {
			StompSession sesion = intento.get(ESPERA_SEGUNDOS, TimeUnit.SECONDS);
			sesion.disconnect();
			throw new AssertionError("La conexion deberia haberse rechazado");
		} catch (ExecutionException ex) {
			return escucha.errores.poll(1, TimeUnit.SECONDS);
		} catch (TimeoutException ex) {
			throw new AssertionError("La conexion ni se acepto ni se rechazo en " + ESPERA_SEGUNDOS + " s", ex);
		}
	}

}
