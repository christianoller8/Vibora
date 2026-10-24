package com.vibora.incidencia;

import com.vibora.PostgresTestBase;
import com.vibora.TestData;
import com.vibora.bloqueo.BloqueoPistaRepository;
import com.vibora.cargo.CargoRepository;
import com.vibora.cargo.PagoRepository;
import com.vibora.club.Club;
import com.vibora.club.ClubRepository;
import com.vibora.notificacion.NotificacionRepository;
import com.vibora.partido.ParticipanteRepository;
import com.vibora.pista.Pista;
import com.vibora.pista.PistaRepository;
import com.vibora.reserva.EstadoReserva;
import com.vibora.reserva.Reserva;
import com.vibora.reserva.ReservaRepository;
import com.vibora.tarifa.TarifaFranjaRepository;
import com.vibora.usuario.Rol;
import com.vibora.usuario.Usuario;
import com.vibora.usuario.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base de los tests de incidencias con commits REALES (no @Transactional): los datos se confirman al crearlos y se
 * borran en @AfterEach en orden de FK. Reloj real. Cada llamada HTTP corre en su propia transaccion y su propia
 * conexion, asi que las carreras son de verdad.
 */
@SpringBootTest(properties = "vibora.jwt.secret=secreto-de-test-con-al-menos-32-bytes-ok!")
@AutoConfigureMockMvc
abstract class IncidenciaSinTransaccionBase extends PostgresTestBase {

	static final String PASSWORD = "contraseña-segura";
	static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

	@Autowired MockMvc mvc;
	@Autowired ClubRepository clubs;
	@Autowired UsuarioRepository usuarios;
	@Autowired PistaRepository pistas;
	@Autowired TarifaFranjaRepository tarifas;
	@Autowired ReservaRepository reservas;
	@Autowired ParticipanteRepository participantes;
	@Autowired IncidenciaRepository incidencias;
	@Autowired IncidenciaEventoRepository eventos;
	@Autowired IncidenciaFotoRepository fotos;
	@Autowired BloqueoPistaRepository bloqueos;
	@Autowired CargoRepository cargos;
	@Autowired PagoRepository pagos;
	@Autowired NotificacionRepository notificaciones;
	@Autowired PasswordEncoder passwordEncoder;

	Club club;
	Pista pista;
	int reservasCreadas;

	record Persona(Long id, String token) {
	}

	Persona admin;
	Persona admin2;
	Persona mant1;
	Persona mant2;
	Persona jugador; // ha reservado en el club

	@BeforeEach
	void crearDatosBase() throws Exception {
		club = clubs.saveAndFlush(TestData.club());
		pista = pistas.saveAndFlush(TestData.pista(club, "Pista 1"));
		admin = persona("admin@vibora.com", Rol.ADMIN_CLUB, club);
		admin2 = persona("admin2@vibora.com", Rol.ADMIN_CLUB, club);
		mant1 = persona("mant1@vibora.com", Rol.MANTENIMIENTO, club);
		mant2 = persona("mant2@vibora.com", Rol.MANTENIMIENTO, club);
		jugador = persona("jugador@vibora.com", Rol.JUGADOR, null);
		haJugado(jugador);
	}

	@AfterEach
	void limpiarBase() {
		// orden de FK: bloqueo_pista / incidencia_foto / notificacion / incidencia_evento -> incidencia -> pago / cargo
		// -> participante -> reserva -> tarifa / pista -> usuario -> club
		bloqueos.deleteAllInBatch();
		fotos.deleteAllInBatch();
		notificaciones.deleteAllInBatch();
		eventos.deleteAllInBatch();
		incidencias.deleteAllInBatch();
		pagos.deleteAllInBatch();
		cargos.deleteAllInBatch();
		participantes.deleteAllInBatch();
		reservas.deleteAllInBatch();
		tarifas.deleteAllInBatch();
		pistas.deleteAllInBatch();
		usuarios.deleteAllInBatch();
		clubs.deleteAllInBatch();
	}

	Persona persona(String email, Rol rol, Club c) throws Exception {
		Usuario u = TestData.usuario(email, rol, c);
		u.setApellidos(email.substring(0, email.indexOf('@'))); // nombres distintos: se distinguen en los textos
		u.setPasswordHash(passwordEncoder.encode(PASSWORD));
		u = usuarios.saveAndFlush(u);
		return new Persona(u.getId(), "Bearer " + login(email));
	}

	void haJugado(Persona p) {
		Instant inicio = TestData.T0.plus(Duration.ofHours(3L * reservasCreadas++));
		Reserva r = reservas.saveAndFlush(TestData.reserva(pista, usuarios.findById(p.id()).orElseThrow(), inicio,
				inicio.plus(Duration.ofMinutes(90)), EstadoReserva.CONFIRMADA));
		participantes.saveAndFlush(TestData.participante(r, usuarios.findById(p.id()).orElseThrow(), 1));
	}

	private String login(String email) throws Exception {
		String cuerpo = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
				.andReturn().getResponse().getContentAsString();
		Matcher m = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(cuerpo);
		assertThat(m.find()).as("login de %s: %s", email, cuerpo).isTrue();
		return m.group(1);
	}

	// ---- llamadas HTTP (devuelven el codigo de estado) ----

	int reportar(Persona p, String titulo) throws Exception {
		return mvc.perform(post("/api/clubes/" + club.getId() + "/incidencias").header("Authorization", p.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"%s\",\"descripcion\":\"d\",\"categoria\":\"RED\"}".formatted(titulo)))
				.andReturn().getResponse().getStatus();
	}

	long incidenciaDe(Persona p, String titulo) throws Exception {
		String cuerpo = mvc.perform(post("/api/clubes/" + club.getId() + "/incidencias").header("Authorization", p.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"%s\",\"descripcion\":\"d\",\"categoria\":\"RED\"}".formatted(titulo)))
				.andReturn().getResponse().getContentAsString();
		Matcher m = ID.matcher(cuerpo);
		assertThat(m.find()).as("incidencia: %s", cuerpo).isTrue();
		return Long.parseLong(m.group(1));
	}

	int asignar(Persona actor, long incidenciaId, Long usuarioId) throws Exception {
		return mvc.perform(post("/api/incidencias/" + incidenciaId + "/asignar").header("Authorization", actor.token())
				.contentType(MediaType.APPLICATION_JSON).content("{\"usuarioId\":" + usuarioId + "}"))
				.andReturn().getResponse().getStatus();
	}

	int estado(Persona actor, long incidenciaId, String estado) throws Exception {
		return mvc.perform(post("/api/incidencias/" + incidenciaId + "/estado").header("Authorization", actor.token())
				.contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"" + estado + "\"}"))
				.andReturn().getResponse().getStatus();
	}

	/** Todas las tareas salen a la vez (barrera) en hilos distintos; devuelve sus resultados en el mismo orden. */
	static <T> List<T> lanzarALaVez(List<Callable<T>> tareas) throws Exception {
		CyclicBarrier salida = new CyclicBarrier(tareas.size());
		ExecutorService pool = Executors.newFixedThreadPool(tareas.size());
		try {
			List<Future<T>> futuros = new ArrayList<>();
			for (Callable<T> tarea : tareas) {
				futuros.add(pool.submit(() -> {
					salida.await(30, TimeUnit.SECONDS);
					return tarea.call();
				}));
			}
			List<T> resultados = new ArrayList<>();
			for (Future<T> f : futuros) {
				resultados.add(f.get(60, TimeUnit.SECONDS));
			}
			return resultados;
		} finally {
			pool.shutdownNow();
		}
	}
}
