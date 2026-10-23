package com.vibora.pista;

import com.vibora.PostgresTestBase;
import com.vibora.TestData;
import com.vibora.club.Club;
import com.vibora.club.ClubRepository;
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
 * Concurrencia REAL (sin @Transactional, commits de verdad): N hilos crean a la vez una pista con el MISMO nombre en
 * el mismo club. Entre el "existe?" y el insert hay una carrera que solo la restriccion UNIQUE (club_id, nombre) de
 * PostgreSQL resuelve: exactamente un 201, el resto 409 y NUNCA un 500; y una unica fila.
 */
@SpringBootTest(properties = "vibora.jwt.secret=secreto-de-test-con-al-menos-32-bytes-ok!")
@AutoConfigureMockMvc
class PistaConcurrenciaTests extends PostgresTestBase {

	private static final int HILOS = 6;
	private static final String NOMBRE = "Pista Central";

	@Autowired MockMvc mvc;
	@Autowired ClubRepository clubs;
	@Autowired UsuarioRepository usuarios;
	@Autowired PistaRepository pistas;
	@Autowired PasswordEncoder passwordEncoder;

	private Club club;
	private String token;

	@BeforeEach
	void crearDatos() throws Exception {
		club = clubs.saveAndFlush(TestData.club());
		var admin = TestData.usuario("admin-concurrente@vibora.com", Rol.ADMIN_CLUB, club);
		admin.setPasswordHash(passwordEncoder.encode("contraseña-segura"));
		usuarios.saveAndFlush(admin);
		token = "Bearer " + login(admin.getEmail());
	}

	@AfterEach
	void limpiar() {
		// orden de FK: pista -> usuario -> club
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

	@RepeatedTest(3)
	void hilosCreandoLaMismaPista_exactamenteUnoGana_yNuncaUn500() throws Exception {
		CyclicBarrier salida = new CyclicBarrier(HILOS);
		ExecutorService pool = Executors.newFixedThreadPool(HILOS);
		List<Future<Integer>> resultados = new ArrayList<>();
		try {
			for (int i = 0; i < HILOS; i++) {
				resultados.add(pool.submit(() -> {
					salida.await(30, TimeUnit.SECONDS); // todos salen a la vez
					try {
						return mvc.perform(post("/api/clubes/" + club.getId() + "/pistas")
										.header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
										.content("{\"nombre\":\"" + NOMBRE + "\",\"tipo\":\"INTERIOR\"}"))
								.andReturn().getResponse().getStatus();
					} catch (jakarta.servlet.ServletException ex) {
						return 500; // MockMvc relanza lo que un servidor real convertiria en un 500
					}
				}));
			}
			List<Integer> estados = new ArrayList<>();
			for (Future<Integer> f : resultados) {
				estados.add(f.get(60, TimeUnit.SECONDS));
			}

			assertThat(estados).as("ningun 5xx: %s", estados).noneMatch(s -> s >= 500);
			assertThat(estados.stream().filter(s -> s == 201).count()).as("201: %s", estados).isEqualTo(1);
			assertThat(estados.stream().filter(s -> s == 409).count()).as("409: %s", estados).isEqualTo(HILOS - 1);
		} finally {
			pool.shutdownNow();
		}

		assertThat(pistas.findByClubIdOrderByNombre(club.getId())).extracting(Pista::getNombre)
				.containsExactly(NOMBRE);
	}
}
