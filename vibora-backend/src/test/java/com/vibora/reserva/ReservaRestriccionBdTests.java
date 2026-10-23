package com.vibora.reserva;

import com.vibora.PostgresTestBase;
import com.vibora.TestData;
import com.vibora.club.Club;
import com.vibora.club.ClubRepository;
import com.vibora.pista.Pista;
import com.vibora.pista.PistaRepository;
import com.vibora.tarifa.TarifaFranja;
import com.vibora.tarifa.TarifaFranjaRepository;
import com.vibora.tarifa.TipoTarifa;
import com.vibora.usuario.Rol;
import com.vibora.usuario.Usuario;
import com.vibora.usuario.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Demuestra que la RESTRICCION de la BD (no la comprobacion amable previa) se traduce en 409.
 * El repositorio es un spy que hace que la comprobacion previa diga "no hay solape" aunque exista una
 * reserva confirmada: la unica defensa que queda es reserva_sin_solape. No es @Transactional (commits reales).
 */
@SpringBootTest(properties = "vibora.jwt.secret=secreto-de-test-con-al-menos-32-bytes-ok!")
@AutoConfigureMockMvc
class ReservaRestriccionBdTests extends PostgresTestBase {

	private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

	@Autowired MockMvc mvc;
	@Autowired ClubRepository clubs;
	@Autowired UsuarioRepository usuarios;
	@Autowired PistaRepository pistas;
	@Autowired TarifaFranjaRepository tarifas;
	@MockitoSpyBean ReservaRepository reservas;
	@Autowired PasswordEncoder passwordEncoder;

	private Club club;
	private Pista pista;
	private Usuario dueño;
	private Instant inicio;
	private String token;

	@BeforeEach
	void crearDatos() throws Exception {
		club = clubs.saveAndFlush(TestData.club());
		pista = pistas.saveAndFlush(TestData.pista(club, "Pista 1"));
		LocalDate fecha = LocalDate.now(MADRID).plusDays(30);
		inicio = fecha.atTime(11, 0).atZone(MADRID).toInstant();
		TarifaFranja t = TestData.tarifa(club, fecha.getDayOfWeek(), TipoTarifa.NORMAL);
		t.setHoraInicio(LocalTime.of(8, 0));
		t.setHoraFin(LocalTime.of(23, 0));
		t.setPrecioHora(new BigDecimal("20.00"));
		tarifas.saveAndFlush(t);

		dueño = TestData.usuario("dueno-bd@vibora.com", Rol.JUGADOR, null);
		dueño.setPasswordHash(passwordEncoder.encode("contraseña-segura"));
		usuarios.saveAndFlush(dueño);
		Usuario otro = TestData.usuario("otro-bd@vibora.com", Rol.JUGADOR, null);
		otro.setPasswordHash(passwordEncoder.encode("contraseña-segura"));
		usuarios.saveAndFlush(otro);
		token = "Bearer " + login("otro-bd@vibora.com");
	}

	@AfterEach
	void limpiar() {
		reset(reservas);
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

	@Test
	void restriccionDeLaBdSeTraduceAConflicto_cuandoLaComprobacionPreviaNoVeNada() throws Exception {
		reservas.saveAndFlush(TestData.reserva(pista, dueño, inicio, inicio.plusSeconds(3600), EstadoReserva.CONFIRMADA));
		clearInvocations(reservas); // que verify() solo cuente lo que hace el servicio
		// la comprobacion amable "no ve" el solape: solo queda la restriccion de la BD
		doReturn(false).when(reservas).existsByPistaIdAndEstadoAndInicioLessThanAndFinGreaterThan(
				anyLong(), any(), any(), any());

		mvc.perform(post("/api/clubes/" + club.getId() + "/pistas/" + pista.getId() + "/reservas")
						.header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
						.content("{\"inicio\":\"" + inicio + "\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("La pista ya está reservada en ese horario"));

		// la comprobacion previa SI se ejecuto (y fue esquivada), y el intento llego a insertar
		verify(reservas).existsByPistaIdAndEstadoAndInicioLessThanAndFinGreaterThan(eq(pista.getId()),
				eq(EstadoReserva.CONFIRMADA), any(), any());
		verify(reservas).saveAndFlush(any(Reserva.class));

		long confirmadas = reservas.findAll().stream()
				.filter(r -> r.getEstado() == EstadoReserva.CONFIRMADA && r.getInicio().equals(inicio)).count();
		assertThat(confirmadas).isEqualTo(1);
	}
}
