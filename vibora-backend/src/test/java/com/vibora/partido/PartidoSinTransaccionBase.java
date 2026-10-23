package com.vibora.partido;

import com.vibora.PostgresTestBase;
import com.vibora.TestData;
import com.vibora.cargo.CargoRepository;
import com.vibora.cargo.PagoRepository;
import com.vibora.club.Club;
import com.vibora.club.ClubRepository;
import com.vibora.notificacion.NotificacionRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Base de los tests con commits REALES (no @Transactional): los datos se crean ya confirmados y se borran en
 * @AfterEach en orden de FK. Reloj real: la casilla es dentro de 30 dias a las 11:00 (Madrid), 45.00 en total
 * (cuota 11.25).
 */
@SpringBootTest(properties = "vibora.jwt.secret=secreto-de-test-con-al-menos-32-bytes-ok!")
@AutoConfigureMockMvc
abstract class PartidoSinTransaccionBase extends PostgresTestBase {

	static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
	static final String PASSWORD = "contraseña-segura";

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

	Club club;
	Pista pista;
	Instant inicio;

	record Jug(Long id, String token) {
	}

	@BeforeEach
	void crearDatosBase() {
		club = clubs.saveAndFlush(TestData.club());
		pista = pistas.saveAndFlush(TestData.pista(club, "Pista 1"));
		LocalDate fecha = LocalDate.now(MADRID).plusDays(30);
		inicio = fecha.atTime(11, 0).atZone(MADRID).toInstant();
		TarifaFranja t = TestData.tarifa(club, fecha.getDayOfWeek(), TipoTarifa.NORMAL);
		t.setHoraInicio(LocalTime.of(8, 0));
		t.setHoraFin(LocalTime.of(23, 0));
		t.setPrecioHora(new BigDecimal("30.00"));
		tarifas.saveAndFlush(t);
	}

	@AfterEach
	void limpiarBase() {
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

	Jug jugador(String email) throws Exception {
		Usuario u = TestData.usuario(email, Rol.JUGADOR, null);
		u.setPasswordHash(passwordEncoder.encode(PASSWORD));
		u = usuarios.saveAndFlush(u);
		return new Jug(u.getId(), "Bearer " + login(email));
	}

	private String login(String email) throws Exception {
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
						.header("Authorization", organizador.token()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"inicio\":\"" + inicio + "\"}"))
				.andReturn().getResponse().getContentAsString();
		Matcher m = Pattern.compile("\"id\":(\\d+)").matcher(cuerpo);
		assertThat(m.find()).as("reserva: %s", cuerpo).isTrue();
		long id = Long.parseLong(m.group(1));
		int estado = mvc.perform(put("/api/reservas/" + id + "/abierta").header("Authorization", organizador.token())
				.contentType(MediaType.APPLICATION_JSON).content("{\"abierta\":true}")).andReturn().getResponse().getStatus();
		assertThat(estado).isEqualTo(200);
		return id;
	}

	int apuntarse(Jug j, long reservaId) throws Exception {
		return mvc.perform(post("/api/reservas/" + reservaId + "/apuntarse").header("Authorization", j.token()))
				.andReturn().getResponse().getStatus();
	}
}
