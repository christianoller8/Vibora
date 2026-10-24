package com.vibora.factura;

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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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
 * Base de los tests de facturacion con commits REALES (no @Transactional): los datos se confirman al crearlos y se
 * borran en @AfterEach. Reloj real (las reservas son dentro de 30 dias; el club abre de 08:00 a 23:00 y las casillas son
 * de 90 minutos desde las 08:00, asi que hay 10 por dia: 0..9).
 * <p>
 * LIMPIEZA: las facturas son inmutables (un trigger rechaza DELETE), asi que no se pueden borrar con deleteAllInBatch.
 * Se vacian con {@code TRUNCATE factura, factura_serie}: TRUNCATE no dispara el trigger de UPDATE/DELETE y no deja
 * ningun estado que restaurar (a diferencia de desactivar el trigger con ALTER TABLE, que, si un test se cortase a medias,
 * dejaria la tabla sin proteccion para el resto). Va PRIMERO porque las facturas apuntan a reservas, usuarios y clubes.
 */
@SpringBootTest(properties = "vibora.jwt.secret=secreto-de-test-con-al-menos-32-bytes-ok!")
@AutoConfigureMockMvc
abstract class FacturaSinTransaccionBase extends PostgresTestBase {

	static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
	static final String PASSWORD = "contraseña-segura";
	static final Pattern NUMERO = Pattern.compile("\"numero\":(\\d+)");
	static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired ClubRepository clubs;
	@Autowired UsuarioRepository usuarios;
	@Autowired PistaRepository pistas;
	@Autowired TarifaFranjaRepository tarifas;
	@Autowired ReservaRepository reservas;
	@Autowired ParticipanteRepository participantes;
	@Autowired BloqueoPistaRepository bloqueos;
	@Autowired CargoRepository cargos;
	@Autowired PagoRepository pagos;
	@Autowired NotificacionRepository notificaciones;
	@Autowired PasswordEncoder passwordEncoder;

	Club club;
	Pista pista;
	LocalDate fecha;
	Persona admin;

	record Persona(Long id, String token) {
	}

	@BeforeEach
	void crearDatosBase() throws Exception {
		club = clubs.saveAndFlush(TestData.club());
		jdbc.update("update club set razon_social = 'Pádel Víbora S.L.', nif = 'B12345674', "
				+ "direccion_fiscal = 'Calle Álvaro Cunqueiro 5, Pontevedra' where id = ?", club.getId());
		pista = pistas.saveAndFlush(TestData.pista(club, "Pista 1"));
		fecha = LocalDate.now(MADRID).plusDays(30);
		for (DayOfWeek dia : DayOfWeek.values()) {
			TarifaFranja t = TestData.tarifa(club, dia, TipoTarifa.NORMAL);
			t.setHoraInicio(LocalTime.of(8, 0));
			t.setHoraFin(LocalTime.of(23, 0));
			t.setPrecioHora(new BigDecimal("30.00"));
			tarifas.saveAndFlush(t);
		}
		admin = persona("admin@vibora.com", Rol.ADMIN_CLUB, club);
	}

	@AfterEach
	void limpiarBase() {
		jdbc.execute("TRUNCATE factura, factura_serie");
		// orden de FK: notificacion / pago -> cargo -> participante / bloqueo -> reserva -> tarifa / pista -> usuario -> club
		notificaciones.deleteAllInBatch();
		pagos.deleteAllInBatch();
		cargos.deleteAllInBatch();
		participantes.deleteAllInBatch();
		bloqueos.deleteAllInBatch();
		reservas.deleteAllInBatch();
		tarifas.deleteAllInBatch();
		pistas.deleteAllInBatch();
		usuarios.deleteAllInBatch();
		clubs.deleteAllInBatch();
	}

	Persona persona(String email, Rol rol, Club c) throws Exception {
		Usuario u = TestData.usuario(email, rol, c);
		u.setApellidos(email.substring(0, email.indexOf('@')));
		u.setPasswordHash(passwordEncoder.encode(PASSWORD));
		u = usuarios.saveAndFlush(u);
		return new Persona(u.getId(), "Bearer " + login(email));
	}

	private String login(String email) throws Exception {
		String cuerpo = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
				.andReturn().getResponse().getContentAsString();
		Matcher m = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(cuerpo);
		assertThat(m.find()).as("login de %s: %s", email, cuerpo).isTrue();
		return m.group(1);
	}

	/** Casilla k de la rejilla del dia (08:00 local + k * 90 minutos). */
	Instant casilla(int k) {
		return fecha.atTime(8, 0).atZone(MADRID).toInstant().plusSeconds(k * 90L * 60);
	}

	/** Reserva la casilla k como el organizador (por la API) y devuelve el id de la reserva. */
	long reservaDe(Persona organizador, int k) throws Exception {
		MvcResult r = mvc.perform(post("/api/clubes/" + club.getId() + "/pistas/" + pista.getId() + "/reservas")
				.header("Authorization", organizador.token()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"inicio\":\"" + casilla(k) + "\"}")).andReturn();
		assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
		return idDe(r.getResponse().getContentAsString());
	}

	static long idDe(String cuerpo) {
		Matcher m = ID.matcher(cuerpo);
		assertThat(m.find()).as(cuerpo).isTrue();
		return Long.parseLong(m.group(1));
	}

	static int numeroDe(String cuerpo) {
		Matcher m = NUMERO.matcher(cuerpo);
		assertThat(m.find()).as(cuerpo).isTrue();
		return Integer.parseInt(m.group(1));
	}

	// ---- llamadas HTTP (devuelven la respuesta entera) ----

	MvcResult facturar(Persona p, long reservaId) throws Exception {
		return mvc.perform(post("/api/reservas/" + reservaId + "/factura").header("Authorization", p.token())).andReturn();
	}

	MvcResult cancelar(Persona p, long reservaId) throws Exception {
		return mvc.perform(post("/api/reservas/" + reservaId + "/cancelar").header("Authorization", p.token())).andReturn();
	}

	// ---- estado de la BD ----

	long contarFacturas() {
		return jdbc.queryForObject("select count(*) from factura", Long.class);
	}

	/** Numeros de factura del club y anio, ordenados. */
	List<Integer> numeros(int anio) {
		return jdbc.queryForList("select numero from factura where club_id = ? and serie = ? order by numero", Integer.class,
				club.getId(), "F" + anio);
	}

	int contador(int anio) {
		List<Integer> filas = jdbc.queryForList("select ultimo_numero from factura_serie where club_id = ? and anio = ?",
				Integer.class, club.getId(), anio);
		return filas.isEmpty() ? 0 : filas.get(0);
	}

	int anioActual() {
		return LocalDate.now(MADRID).getYear();
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
				resultados.add(f.get(90, TimeUnit.SECONDS));
			}
			return resultados;
		} finally {
			pool.shutdownNow();
		}
	}
}
