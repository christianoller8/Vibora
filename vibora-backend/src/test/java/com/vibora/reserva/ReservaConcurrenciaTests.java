package com.vibora.reserva;

import com.vibora.PostgresTestBase;
import com.vibora.TestData;
import com.vibora.club.Club;
import com.vibora.club.ClubRepository;
import com.vibora.partido.ParticipanteRepository;
import com.vibora.pista.Pista;
import com.vibora.pista.PistaRepository;
import com.vibora.tarifa.TarifaFranja;
import com.vibora.tarifa.TarifaFranjaRepository;
import com.vibora.tarifa.TipoTarifa;
import com.vibora.usuario.Rol;
import com.vibora.usuario.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
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
 * Concurrencia REAL: NO es @Transactional (necesita commits de verdad). N hilos reservan a la vez
 * la MISMA casilla; solo la restriccion EXCLUDE de PostgreSQL puede garantizar exactamente un ganador.
 * Los datos se crean ya confirmados y se borran en @AfterEach (en orden de FK) para no afectar al resto de tests.
 */
@SpringBootTest(properties = "vibora.jwt.secret=secreto-de-test-con-al-menos-32-bytes-ok!")
@AutoConfigureMockMvc
class ReservaConcurrenciaTests extends PostgresTestBase {

	private static final int HILOS = 8;
	private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

	@Autowired MockMvc mvc;
	@Autowired ClubRepository clubs;
	@Autowired UsuarioRepository usuarios;
	@Autowired PistaRepository pistas;
	@Autowired TarifaFranjaRepository tarifas;
	@Autowired ReservaRepository reservas;
	@Autowired ParticipanteRepository participantes;
	@Autowired PasswordEncoder passwordEncoder;

	private Club club;
	private Pista pista;
	private Instant inicio;
	private final List<String> tokens = new ArrayList<>();

	@BeforeEach
	void crearDatos() throws Exception {
		club = clubs.saveAndFlush(TestData.club());
		pista = pistas.saveAndFlush(TestData.pista(club, "Pista 1"));

		// una casilla de la rejilla (11:00 local) dentro de 30 dias, con su tarifa
		LocalDate fecha = LocalDate.now(MADRID).plusDays(30);
		inicio = fecha.atTime(11, 0).atZone(MADRID).toInstant();
		TarifaFranja t = TestData.tarifa(club, fecha.getDayOfWeek(), TipoTarifa.NORMAL);
		t.setHoraInicio(LocalTime.of(8, 0));
		t.setHoraFin(LocalTime.of(23, 0));
		t.setPrecioHora(new BigDecimal("20.00"));
		tarifas.saveAndFlush(t);

		tokens.clear();
		for (int i = 0; i < HILOS; i++) {
			String email = "concurrente" + i + "@vibora.com";
			var u = TestData.usuario(email, Rol.JUGADOR, null);
			u.setPasswordHash(passwordEncoder.encode("contraseña-segura"));
			usuarios.saveAndFlush(u);
			tokens.add("Bearer " + login(email));
		}
	}

	@AfterEach
	void limpiar() {
		// orden de FK: participante -> reserva -> tarifa_franja/pista -> usuario -> club
		participantes.deleteAllInBatch(); // crear() inserta al organizador como participante
		reservas.deleteAllInBatch();
		tarifas.deleteAllInBatch();
		pistas.deleteAllInBatch();
		usuarios.deleteAllInBatch();
		clubs.deleteAllInBatch();
	}

	private String login(String email) throws Exception {
		String cuerpo = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"%s\",\"password\":\"contraseña-segura\"}".formatted(email)))
				.andReturn().getResponse().getContentAsString();
		Matcher m = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(cuerpo);
		assertThat(m.find()).as("login de %s: %s", email, cuerpo).isTrue();
		return m.group(1);
	}

	@RepeatedTest(5)
	void hilosReservandoLaMismaCasilla_exactamenteUnoGana() throws Exception {
		CyclicBarrier salida = new CyclicBarrier(HILOS);
		ExecutorService pool = Executors.newFixedThreadPool(HILOS);
		List<Future<Integer>> resultados = new ArrayList<>();
		String ruta = "/api/clubes/" + club.getId() + "/pistas/" + pista.getId() + "/reservas";
		try {
			for (String token : tokens) {
				resultados.add(pool.submit(() -> {
					salida.await(30, TimeUnit.SECONDS); // todos salen a la vez
					return mvc.perform(post(ruta).header("Authorization", token)
									.contentType(MediaType.APPLICATION_JSON)
									.content("{\"inicio\":\"" + inicio + "\"}"))
							.andReturn().getResponse().getStatus();
				}));
			}
			List<Integer> estados = new ArrayList<>();
			for (Future<Integer> f : resultados) {
				estados.add(f.get(60, TimeUnit.SECONDS));
			}

			assertThat(estados.stream().filter(s -> s == 201).count()).as("201: %s", estados).isEqualTo(1);
			assertThat(estados.stream().filter(s -> s == 409).count()).as("409: %s", estados).isEqualTo(HILOS - 1);
		} finally {
			pool.shutdownNow();
		}

		List<Reserva> confirmadas = reservas.findAll().stream()
				.filter(r -> r.getEstado() == EstadoReserva.CONFIRMADA).toList();
		assertThat(confirmadas).hasSize(1);
		assertThat(confirmadas.get(0).getInicio()).isEqualTo(inicio);
	}
}
