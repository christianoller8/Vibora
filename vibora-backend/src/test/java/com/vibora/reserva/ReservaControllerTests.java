package com.vibora.reserva;

import com.vibora.ApiTestBase;
import com.vibora.RelojMutable;
import com.vibora.RelojTestConfig;
import com.vibora.TestData;
import com.vibora.club.Club;
import com.vibora.pista.Pista;
import com.vibora.pista.PistaRepository;
import com.vibora.tarifa.TarifaFranja;
import com.vibora.tarifa.TarifaFranjaRepository;
import com.vibora.tarifa.TipoTarifa;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reloj fijado en 2030-01-01T00:00Z. Todas las reservas son el lunes 2030-01-07 (hora de Madrid, UTC+1).
 * Club 08:00-23:00 y duracion 90 min => rejilla 08:00, 09:30, 11:00, 12:30, 14:00, 15:30, 17:00, 18:30, 20:00, 21:30.
 */
@Import(RelojTestConfig.class)
class ReservaControllerTests extends ApiTestBase {

	private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
	private static final LocalDate LUNES = LocalDate.of(2030, 1, 7);
	private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

	@Autowired RelojMutable reloj;
	@Autowired PistaRepository pistas;
	@Autowired TarifaFranjaRepository tarifas;
	@Autowired ReservaRepository reservas;
	@Autowired EntityManager em;

	Club club;
	Pista pista;

	@BeforeEach
	void preparar() {
		reloj.fijar(RelojTestConfig.AHORA_INICIAL);
		club = crearClub("Club A");
		pista = pistas.saveAndFlush(TestData.pista(club, "Pista 1"));
	}

	// ---- ayudas ----

	private static Instant local(LocalDate fecha, int hora, int minuto) {
		return fecha.atTime(hora, minuto).atZone(MADRID).toInstant();
	}

	private static Instant lunes(int hora, int minuto) {
		return local(LUNES, hora, minuto);
	}

	private TarifaFranja tarifa(Club c, DayOfWeek dia, int horaIni, int horaFin, String precio) {
		TarifaFranja t = TestData.tarifa(c, dia, TipoTarifa.NORMAL);
		t.setHoraInicio(LocalTime.of(horaIni, 0));
		t.setHoraFin(LocalTime.of(horaFin, 0));
		t.setPrecioHora(new BigDecimal(precio));
		return tarifas.saveAndFlush(t);
	}

	/** Tarifa unica de lunes 08:00-23:00 (90 min = 1.5 * precioHora). */
	private TarifaFranja tarifaPlana(String precioHora) {
		return tarifa(club, DayOfWeek.MONDAY, 8, 23, precioHora);
	}

	private ResultActions reservar(String token, Club c, Pista p, Instant inicio) throws Exception {
		return mvc.perform(post("/api/clubes/" + c.getId() + "/pistas/" + p.getId() + "/reservas")
				.header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"inicio\":\"" + inicio + "\"}"));
	}

	private ResultActions reservar(String token, Instant inicio) throws Exception {
		return reservar(token, club, pista, inicio);
	}

	private long idDe(ResultActions resultado) throws Exception {
		Matcher m = ID.matcher(resultado.andReturn().getResponse().getContentAsString());
		assertThat(m.find()).isTrue();
		return Long.parseLong(m.group(1));
	}

	private long reservaCreada(String token, Instant inicio) throws Exception {
		return idDe(reservar(token, inicio).andExpect(status().isCreated()));
	}

	private ResultActions cancelar(String token, long id) throws Exception {
		return mvc.perform(post("/api/reservas/" + id + "/cancelar").header("Authorization", token));
	}

	private ResultActions agenda(String token, Pista p, LocalDate fecha) throws Exception {
		return mvc.perform(get("/api/clubes/" + p.getClub().getId() + "/pistas/" + p.getId() + "/agenda")
				.param("fecha", fecha.toString()).header("Authorization", token));
	}

	// ---- Crear ----

	@Test
	void reservar_caminoFeliz_201_precioCongelado() throws Exception {
		tarifaPlana("20.00");
		String token = bearerJugador("j1@vibora.com");

		reservar(token, lunes(11, 0))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.pistaId").value(pista.getId()))
				.andExpect(jsonPath("$.pistaNombre").value("Pista 1"))
				.andExpect(jsonPath("$.clubId").value(club.getId()))
				.andExpect(jsonPath("$.clubNombre").value("Club A"))
				.andExpect(jsonPath("$.inicio").value("2030-01-07T10:00:00Z"))
				.andExpect(jsonPath("$.fin").value("2030-01-07T11:30:00Z"))
				.andExpect(jsonPath("$.estado").value("CONFIRMADA"))
				.andExpect(jsonPath("$.precioTotal").value(30.0));

		em.flush();
		em.clear();
		List<Reserva> guardadas = reservas.findAll();
		assertThat(guardadas).singleElement().satisfies(r -> {
			assertThat(r.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
			assertThat(r.getPrecioTotal()).isEqualByComparingTo("30.00");
			assertThat(r.getInicio()).isEqualTo(lunes(11, 0));
			assertThat(r.getFin()).isEqualTo(lunes(12, 30));
			assertThat(r.getUsuario().getEmail()).isEqualTo("j1@vibora.com");
		});
	}

	@Test
	void reservar_tramoQueCruzaDosTarifas_precioProrrateado() throws Exception {
		tarifa(club, DayOfWeek.MONDAY, 8, 18, "12.00");
		tarifa(club, DayOfWeek.MONDAY, 18, 23, "24.00");
		String token = bearerJugador("j1@vibora.com");

		// 17:00-18:30: 60 min a 12 EUR/h + 30 min a 24 EUR/h = 24.00
		reservar(token, lunes(17, 0))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.precioTotal").value(24.0));
	}

	@Test
	void reservar_primeraYUltimaCasillaDelDia_ok() throws Exception {
		tarifaPlana("20.00");
		String token = bearerJugador("j1@vibora.com");

		reservar(token, lunes(8, 0)).andExpect(status().isCreated());
		reservar(token, lunes(21, 30)).andExpect(status().isCreated()); // termina justo a la hora de cierre
	}

	@Test
	void reservar_fueraDeRejilla_400() throws Exception {
		tarifaPlana("20.00");
		String token = bearerJugador("j1@vibora.com");

		reservar(token, lunes(11, 15)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").isNotEmpty());
		reservar(token, lunes(12, 0)).andExpect(status().isBadRequest());
		reservar(token, lunes(11, 0).plusSeconds(30)).andExpect(status().isBadRequest());
		assertThat(reservas.count()).isZero();
	}

	@Test
	void reservar_fueraDelHorarioDelClub_400() throws Exception {
		tarifaPlana("20.00");
		String token = bearerJugador("j1@vibora.com");

		reservar(token, lunes(6, 30)).andExpect(status().isBadRequest()); // antes de abrir
		reservar(token, lunes(23, 0)).andExpect(status().isBadRequest()); // despues de cerrar
		reservar(token, lunes(22, 0)).andExpect(status().isBadRequest()); // acabaria a las 23:30
		assertThat(reservas.count()).isZero();
	}

	@Test
	void reservar_enElPasadoOAhoraMismo_400() throws Exception {
		tarifaPlana("20.00");
		String token = bearerJugador("j1@vibora.com");

		reloj.fijar(lunes(11, 0).plusSeconds(1));
		reservar(token, lunes(11, 0)).andExpect(status().isBadRequest());
		reloj.fijar(lunes(11, 0)); // estrictamente posterior a "ahora"
		reservar(token, lunes(11, 0)).andExpect(status().isBadRequest());
		reloj.fijar(lunes(11, 0).minusSeconds(1));
		reservar(token, lunes(11, 0)).andExpect(status().isCreated());
	}

	@Test
	void reservar_sinTarifaQueCubra_400() throws Exception {
		String token = bearerJugador("j1@vibora.com");

		reservar(token, lunes(11, 0)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("No hay tarifa definida para esa franja horaria"));

		tarifa(club, DayOfWeek.MONDAY, 8, 12, "10.00"); // 11:00-12:30 queda a medias sin cubrir
		reservar(token, lunes(11, 0)).andExpect(status().isBadRequest());
		assertThat(reservas.count()).isZero();
	}

	@Test
	void reservar_mismoHorarioOtroUsuario_409() throws Exception {
		tarifaPlana("20.00");
		String t1 = bearerJugador("j1@vibora.com");
		String t2 = bearerJugador("j2@vibora.com");
		reservar(t1, lunes(11, 0)).andExpect(status().isCreated());

		reservar(t2, lunes(11, 0)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("La pista ya está reservada en ese horario"));
		assertThat(reservas.count()).isEqualTo(1);
	}

	@Test
	void reservar_casillasContiguas_ok() throws Exception {
		tarifaPlana("20.00");
		String t1 = bearerJugador("j1@vibora.com");
		String t2 = bearerJugador("j2@vibora.com");
		reservar(t1, lunes(11, 0)).andExpect(status().isCreated());

		reservar(t2, lunes(12, 30)).andExpect(status().isCreated()); // empieza justo cuando acaba la otra
		reservar(t2, lunes(9, 30)).andExpect(status().isCreated()); // acaba justo cuando empieza la otra
		assertThat(reservas.count()).isEqualTo(3);
	}

	@Test
	void reservar_mismoHorarioOtraPista_ok() throws Exception {
		tarifaPlana("20.00");
		Pista otra = pistas.saveAndFlush(TestData.pista(club, "Pista 2"));
		String t1 = bearerJugador("j1@vibora.com");
		String t2 = bearerJugador("j2@vibora.com");
		reservar(t1, lunes(11, 0)).andExpect(status().isCreated());

		reservar(t2, club, otra, lunes(11, 0)).andExpect(status().isCreated());
	}

	@Test
	void reservar_pistaInactiva_409() throws Exception {
		tarifaPlana("20.00");
		pista.setActiva(false);
		pistas.saveAndFlush(pista);

		reservar(bearerJugador("j1@vibora.com"), lunes(11, 0)).andExpect(status().isConflict());
		assertThat(reservas.count()).isZero();
	}

	@Test
	void reservar_rutaIncorrecta_404() throws Exception {
		tarifaPlana("20.00");
		Club otroClub = crearClub("Club B");
		Pista deOtro = pistas.saveAndFlush(TestData.pista(otroClub, "Pista B"));
		String token = bearerJugador("j1@vibora.com");

		reservar(token, club, deOtro, lunes(11, 0)).andExpect(status().isNotFound()); // pista de otro club
		mvc.perform(post("/api/clubes/999999/pistas/" + pista.getId() + "/reservas").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"inicio\":\"" + lunes(11, 0) + "\"}"))
				.andExpect(status().isNotFound());
		mvc.perform(post("/api/clubes/" + club.getId() + "/pistas/999999/reservas").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"inicio\":\"" + lunes(11, 0) + "\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void reservar_sinToken_401() throws Exception {
		mvc.perform(post("/api/clubes/" + club.getId() + "/pistas/" + pista.getId() + "/reservas")
				.contentType(MediaType.APPLICATION_JSON).content("{\"inicio\":\"" + lunes(11, 0) + "\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void reservar_bodyInvalido_400() throws Exception {
		String token = bearerJugador("j1@vibora.com");
		String ruta = "/api/clubes/" + club.getId() + "/pistas/" + pista.getId() + "/reservas";

		for (String body : new String[] { "{}", "{\"inicio\":null}", "{\"inicio\":\"mañana\"}" }) {
			mvc.perform(post(ruta).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isBadRequest());
		}
	}

	@Test
	void cambiarLaTarifaDespues_noAlteraElPrecioDeLaReservaExistente() throws Exception {
		TarifaFranja t = tarifaPlana("20.00");
		String token = bearerJugador("j1@vibora.com");
		reservar(token, lunes(11, 0)).andExpect(status().isCreated());

		t.setPrecioHora(new BigDecimal("100.00"));
		tarifas.saveAndFlush(t);
		em.clear();

		assertThat(reservas.findAll()).singleElement()
				.satisfies(r -> assertThat(r.getPrecioTotal()).isEqualByComparingTo("30.00"));
		mvc.perform(get("/api/reservas/mias").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].precioTotal").value(30.0));
		// y una reserva nueva ya usa el precio nuevo
		reservar(token, lunes(12, 30)).andExpect(status().isCreated()).andExpect(jsonPath("$.precioTotal").value(150.0));
	}

	// ---- Agenda ----

	@Test
	void agenda_reflejaReservadasCanceladasYPasadas_yOcultaUsuarios() throws Exception {
		tarifaPlana("20.00");
		String token = bearerJugador("j1@vibora.com");
		reservaCreada(token, lunes(11, 0));
		long canceladaId = reservaCreada(token, lunes(12, 30));
		cancelar(token, canceladaId).andExpect(status().isOk());
		reloj.fijar(lunes(10, 0)); // 08:00 y 09:30 ya han pasado

		String cuerpo = agenda(bearerJugador("j2@vibora.com"), pista, LUNES)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(10))
				.andExpect(jsonPath("$[0].inicio").value("2030-01-07T07:00:00Z"))
				.andExpect(jsonPath("$[0].fin").value("2030-01-07T08:30:00Z"))
				.andExpect(jsonPath("$[0].disponible").value(false)) // pasada
				.andExpect(jsonPath("$[0].precio").value(30.0))
				.andExpect(jsonPath("$[1].disponible").value(false)) // pasada
				.andExpect(jsonPath("$[2].inicio").value("2030-01-07T10:00:00Z"))
				.andExpect(jsonPath("$[2].disponible").value(false)) // reservada
				.andExpect(jsonPath("$[3].disponible").value(true)) // reserva cancelada: libre
				.andExpect(jsonPath("$[4].disponible").value(true))
				.andExpect(jsonPath("$[9].fin").value("2030-01-07T22:00:00Z"))
				.andExpect(jsonPath("$[2].usuario").doesNotExist())
				.andExpect(jsonPath("$[2].usuarioId").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		assertThat(cuerpo).doesNotContain("j1@vibora.com").doesNotContain("García");
	}

	@Test
	void agenda_diaSinTarifa_precioNulo() throws Exception {
		tarifaPlana("20.00"); // solo lunes
		String token = bearerJugador("j1@vibora.com");

		agenda(token, pista, LUNES.plusDays(1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(10))
				.andExpect(jsonPath("$[0].disponible").value(true))
				.andExpect(jsonPath("$[0].precio").value(nullValue()));
	}

	@Test
	void agenda_pistaInactiva_todoNoDisponible() throws Exception {
		tarifaPlana("20.00");
		pista.setActiva(false);
		pistas.saveAndFlush(pista);

		agenda(bearerJugador("j1@vibora.com"), pista, LUNES)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].disponible").value(false))
				.andExpect(jsonPath("$[5].disponible").value(false));
	}

	@Test
	void agenda_rutaFechaYAutenticacion() throws Exception {
		String token = bearerJugador("j1@vibora.com");
		Club otroClub = crearClub("Club B");
		Pista deOtro = pistas.saveAndFlush(TestData.pista(otroClub, "Pista B"));

		mvc.perform(get("/api/clubes/" + club.getId() + "/pistas/" + deOtro.getId() + "/agenda")
				.param("fecha", "2030-01-07").header("Authorization", token)).andExpect(status().isNotFound());
		mvc.perform(get("/api/clubes/999999/pistas/" + pista.getId() + "/agenda")
				.param("fecha", "2030-01-07").header("Authorization", token)).andExpect(status().isNotFound());
		mvc.perform(get("/api/clubes/" + club.getId() + "/pistas/" + pista.getId() + "/agenda")
				.param("fecha", "no-es-fecha").header("Authorization", token)).andExpect(status().isBadRequest());
		mvc.perform(get("/api/clubes/" + club.getId() + "/pistas/" + pista.getId() + "/agenda")
				.header("Authorization", token)).andExpect(status().isBadRequest());
		mvc.perform(get("/api/clubes/" + club.getId() + "/pistas/" + pista.getId() + "/agenda")
				.param("fecha", "2030-01-07")).andExpect(status().isUnauthorized());
	}

	// ---- Mis reservas ----

	@Test
	void mias_soloLasPropias_masNuevaPrimera_conCanceladas() throws Exception {
		tarifaPlana("20.00");
		String a = bearerJugador("ja@vibora.com");
		String b = bearerJugador("jb@vibora.com");
		long primera = reservaCreada(a, lunes(11, 0));
		long segunda = reservaCreada(a, lunes(14, 0));
		reservaCreada(b, lunes(17, 0));
		cancelar(a, primera).andExpect(status().isOk());

		mvc.perform(get("/api/reservas/mias").header("Authorization", a))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].id").value(segunda))
				.andExpect(jsonPath("$[0].estado").value("CONFIRMADA"))
				.andExpect(jsonPath("$[1].id").value(primera))
				.andExpect(jsonPath("$[1].estado").value("CANCELADA"))
				.andExpect(jsonPath("$[1].pistaNombre").value("Pista 1"))
				.andExpect(jsonPath("$[0].clubNombre").value("Club A"))
				.andExpect(jsonPath("$[1].clubNombre").value("Club A"));
		mvc.perform(get("/api/reservas/mias").header("Authorization", b))
				.andExpect(jsonPath("$.length()").value(1));
		mvc.perform(get("/api/reservas/mias")).andExpect(status().isUnauthorized());
	}

	@Test
	void mias_noHaceUnaConsultaPorReserva() throws Exception {
		tarifaPlana("20.00");
		Club otroClub = crearClub("Club B");
		Pista otraPista = pistas.saveAndFlush(TestData.pista(otroClub, "Pista X"));
		tarifa(otroClub, DayOfWeek.MONDAY, 8, 23, "10.00");
		String token = bearerJugador("ja@vibora.com");
		reservaCreada(token, lunes(11, 0));
		reservaCreada(token, lunes(14, 0));
		reservaCreada(token, lunes(17, 0));
		idDe(reservar(token, otroClub, otraPista, lunes(11, 0)).andExpect(status().isCreated()));
		em.flush();
		em.clear();

		Statistics estadisticas = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
		estadisticas.setStatisticsEnabled(true);
		estadisticas.clear();
		mvc.perform(get("/api/reservas/mias").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(4));
		long consultas = estadisticas.getPrepareStatementCount();
		estadisticas.setStatisticsEnabled(false);

		// Con carga perezosa serian 1 (reservas) + 2 (pistas) + 2 (clubes) + las de autenticacion; con fetch join, constante
		assertThat(consultas).isLessThanOrEqualTo(2);
	}

	// ---- Cancelar ----

	@Test
	void cancelar_propietarioEnPlazo_200_yLaCasillaVuelveAEstarLibre() throws Exception {
		tarifaPlana("20.00");
		String duenyo = bearerJugador("j1@vibora.com");
		String otro = bearerJugador("j2@vibora.com");
		long id = reservaCreada(duenyo, lunes(11, 0));
		reservar(otro, lunes(11, 0)).andExpect(status().isConflict());

		cancelar(duenyo, id)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.clubNombre").value("Club A"))
				.andExpect(jsonPath("$.estado").value("CANCELADA"));

		assertThat(reservas.findById(id)).get().satisfies(r -> assertThat(r.getEstado()).isEqualTo(EstadoReserva.CANCELADA));
		reservar(otro, lunes(11, 0)).andExpect(status().isCreated());
		assertThat(reservas.count()).isEqualTo(2); // la cancelada se conserva
	}

	@Test
	void cancelar_justoEnElLimiteDe24Horas_ok_yUnSegundoDespues409() throws Exception {
		tarifaPlana("20.00");
		String token = bearerJugador("j1@vibora.com");
		long id = reservaCreada(token, lunes(11, 0));

		reloj.fijar(lunes(11, 0).minus(Duration.ofHours(24)).plusSeconds(1));
		cancelar(token, id).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Fuera del plazo de cancelación"));

		reloj.fijar(lunes(11, 0).minus(Duration.ofHours(24)));
		cancelar(token, id).andExpect(status().isOk());
	}

	@Test
	void cancelar_propietarioFueraDePlazo_409_yNoCambiaNada() throws Exception {
		tarifaPlana("20.00");
		String token = bearerJugador("j1@vibora.com");
		long id = reservaCreada(token, lunes(11, 0));
		reloj.fijar(lunes(11, 0).minus(Duration.ofHours(2)));

		cancelar(token, id).andExpect(status().isConflict());

		assertThat(reservas.findById(id)).get().satisfies(r -> assertThat(r.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA));
	}

	@Test
	void cancelar_otroJugador_403() throws Exception {
		tarifaPlana("20.00");
		long id = reservaCreada(bearerJugador("j1@vibora.com"), lunes(11, 0));

		cancelar(bearerJugador("j2@vibora.com"), id).andExpect(status().isForbidden());

		assertThat(reservas.findById(id)).get().satisfies(r -> assertThat(r.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA));
	}

	@Test
	void cancelar_adminDelClub_cuandoQuiera_antesDelInicio() throws Exception {
		tarifaPlana("20.00");
		String admin = bearerAdminDe(club, "admin@vibora.com");
		long id = reservaCreada(bearerJugador("j1@vibora.com"), lunes(11, 0));
		reloj.fijar(lunes(11, 0).minus(Duration.ofMinutes(10))); // muy dentro del plazo del jugador

		cancelar(admin, id).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADA"));
	}

	@Test
	void cancelar_adminDelClub_despuesDelInicio_409() throws Exception {
		tarifaPlana("20.00");
		String admin = bearerAdminDe(club, "admin@vibora.com");
		long id = reservaCreada(bearerJugador("j1@vibora.com"), lunes(11, 0));
		reloj.fijar(lunes(11, 30));

		cancelar(admin, id).andExpect(status().isConflict());
	}

	@Test
	void cancelar_adminDeOtroClub_403() throws Exception {
		tarifaPlana("20.00");
		Club otroClub = crearClub("Club B");
		String ajeno = bearerAdminDe(otroClub, "adminb@vibora.com");
		long id = reservaCreada(bearerJugador("j1@vibora.com"), lunes(11, 0));

		cancelar(ajeno, id).andExpect(status().isForbidden());
	}

	@Test
	void cancelar_idempotente_yNoEncontradoYSinToken() throws Exception {
		tarifaPlana("20.00");
		String token = bearerJugador("j1@vibora.com");
		long id = reservaCreada(token, lunes(11, 0));

		cancelar(token, id).andExpect(status().isOk());
		reloj.fijar(lunes(11, 0).minus(Duration.ofHours(1))); // ya fuera de plazo, pero ya estaba cancelada
		cancelar(token, id).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADA"));

		cancelar(token, 999999).andExpect(status().isNotFound());
		mvc.perform(post("/api/reservas/" + id + "/cancelar")).andExpect(status().isUnauthorized());
	}

	// ---- Reservas del club (admin) ----

	@Test
	void reservasDelClub_soloAdminPropio_conResumenDeUsuario() throws Exception {
		tarifaPlana("20.00");
		String admin = bearerAdminDe(club, "admin@vibora.com");
		String jugador = bearerJugador("j1@vibora.com");
		reservaCreada(jugador, lunes(14, 0));
		long cancelada = reservaCreada(jugador, lunes(11, 0));
		cancelar(jugador, cancelada).andExpect(status().isOk());
		reservaCreada(jugador, local(LUNES.plusDays(7), 11, 0)); // otro dia: no debe salir

		String url = "/api/clubes/" + club.getId() + "/reservas";
		mvc.perform(get(url).param("fecha", "2030-01-07").header("Authorization", admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].id").value(cancelada)) // ordenadas por inicio
				.andExpect(jsonPath("$[0].estado").value("CANCELADA"))
				.andExpect(jsonPath("$[0].pistaNombre").value("Pista 1"))
				.andExpect(jsonPath("$[0].usuario.email").value("j1@vibora.com"))
				.andExpect(jsonPath("$[0].usuario.nombre").value("Ana"))
				.andExpect(jsonPath("$[0].usuario.apellidos").value("García"))
				.andExpect(jsonPath("$[0].usuario.id").isNumber())
				.andExpect(jsonPath("$[0].usuario.passwordHash").doesNotExist())
				.andExpect(jsonPath("$[0].usuario.rol").doesNotExist())
				.andExpect(jsonPath("$[1].estado").value("CONFIRMADA"));
	}

	@Test
	void reservasDelClub_permisosYErrores() throws Exception {
		Club otroClub = crearClub("Club B");
		String ajeno = bearerAdminDe(otroClub, "adminb@vibora.com");
		String jugador = bearerJugador("j1@vibora.com");
		String url = "/api/clubes/" + club.getId() + "/reservas";

		mvc.perform(get(url).param("fecha", "2030-01-07").header("Authorization", jugador)).andExpect(status().isForbidden());
		mvc.perform(get(url).param("fecha", "2030-01-07").header("Authorization", ajeno)).andExpect(status().isForbidden());
		mvc.perform(get(url).param("fecha", "2030-01-07")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/clubes/999999/reservas").param("fecha", "2030-01-07").header("Authorization", jugador))
				.andExpect(status().isNotFound());
	}
}
