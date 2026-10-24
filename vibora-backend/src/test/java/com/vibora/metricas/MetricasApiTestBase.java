package com.vibora.metricas;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.vibora.ApiTestBase;
import com.vibora.RelojMutable;
import com.vibora.RelojTestConfig;
import com.vibora.TestData;
import com.vibora.bloqueo.BloqueoPista;
import com.vibora.bloqueo.BloqueoPistaRepository;
import com.vibora.club.Club;
import com.vibora.pista.Pista;
import com.vibora.pista.PistaRepository;
import com.vibora.reserva.EstadoReserva;
import com.vibora.reserva.Reserva;
import com.vibora.reserva.ReservaRepository;
import com.vibora.usuario.Rol;
import com.vibora.usuario.Usuario;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Base de los tests de metricas (rollback por test). Reloj en 2030-01-01T00:00Z (en Madrid ya es el 1 de enero a las
 * 01:00). El club por defecto abre de 08:00 a 23:00 y las casillas duran 90 minutos: 10 casillas al dia por pista,
 * a las 08:00, 09:30, 11:00, 12:30, 14:00, 15:30, 17:00, 18:30, 20:00 y 21:30. Los datos historicos se insertan directamente
 * (la API no deja reservar en el pasado); las consultas siempre van por HTTP con el login real.
 */
@Import(RelojTestConfig.class)
abstract class MetricasApiTestBase extends ApiTestBase {

	static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
	static final Duration CASILLA = Duration.ofMinutes(90);
	static final String[] HORAS = { "08:00", "09:30", "11:00", "12:30", "14:00", "15:30", "17:00", "18:30", "20:00", "21:30" };
	static final String[] RECURSOS = { "resumen", "ingresos", "ocupacion", "franjas", "cancelaciones" };

	@Autowired RelojMutable reloj;
	@Autowired PistaRepository pistas;
	@Autowired ReservaRepository reservas;
	@Autowired BloqueoPistaRepository bloqueos;
	@Autowired JdbcTemplate jdbc;
	@Autowired EntityManager em;

	Club club;
	Usuario organizador;
	String admin;

	@BeforeEach
	void prepararMetricas() throws Exception {
		reloj.fijar(RelojTestConfig.AHORA_INICIAL);
		club = crearClub("Club A");
		admin = bearerAdminDe(club, "admin@a.com");
		organizador = crearUsuario("org@a.com", Rol.JUGADOR, null);
	}

	// ---- datos ----

	static Instant local(LocalDate dia, String hora) {
		return dia.atTime(LocalTime.parse(hora)).atZone(MADRID).toInstant();
	}

	static LocalDate d(int anio, int mes, int dia) {
		return LocalDate.of(anio, mes, dia);
	}

	Pista pista(Club c, String nombre) {
		return pistas.saveAndFlush(TestData.pista(c, nombre));
	}

	Pista pistaInactiva(Club c, String nombre) {
		Pista p = TestData.pista(c, nombre);
		p.setActiva(false);
		return pistas.saveAndFlush(p);
	}

	Reserva reserva(Pista p, Instant inicio, EstadoReserva estado, String precio) {
		Reserva r = TestData.reserva(p, organizador, inicio, inicio.plus(CASILLA), estado);
		r.setPrecioTotal(new BigDecimal(precio));
		return reservas.saveAndFlush(r);
	}

	Reserva reserva(Pista p, LocalDate dia, String hora, EstadoReserva estado, String precio) {
		return reserva(p, local(dia, hora), estado, precio);
	}

	Reserva confirmada(Pista p, LocalDate dia, String hora, String precio) {
		return reserva(p, dia, hora, EstadoReserva.CONFIRMADA, precio);
	}

	Reserva cancelada(Pista p, LocalDate dia, String hora, String precio) {
		return reserva(p, dia, hora, EstadoReserva.CANCELADA, precio);
	}

	/** Bloqueo con rango [desde, hasta) ({@code hasta} nulo = sin fin) y, si se paso, levantado en ese instante. */
	BloqueoPista bloqueo(Pista p, Instant desde, Instant hasta, Instant levantadoEn) {
		BloqueoPista b = new BloqueoPista();
		b.setPista(p);
		b.setMotivo("mantenimiento");
		b.setDesde(desde);
		b.setHasta(hasta);
		b.setCreadoPor(organizador);
		b.setCreadoEn(Instant.parse("2029-01-01T00:00:00Z"));
		b.setLevantadoEn(levantadoEn);
		return bloqueos.saveAndFlush(b);
	}

	// ---- HTTP ----

	ResultActions pedir(String token, Club c, String recurso, String... clavesValores) throws Exception {
		return pedir(token, c.getId(), recurso, clavesValores);
	}

	ResultActions pedir(String token, Long clubId, String recurso, String... clavesValores) throws Exception {
		MockHttpServletRequestBuilder peticion = get("/api/clubes/" + clubId + "/metricas/" + recurso);
		if (token != null) {
			peticion.header("Authorization", token);
		}
		for (int i = 0; i < clavesValores.length; i += 2) {
			peticion.param(clavesValores[i], clavesValores[i + 1]);
		}
		return mvc.perform(peticion);
	}

	/** Pide como administrador del club por defecto. */
	ResultActions comoAdmin(String recurso, String... clavesValores) throws Exception {
		return pedir(admin, club, recurso, clavesValores);
	}

	/** Rango de fechas como parametros. */
	static String[] rango(LocalDate desde, LocalDate hasta, String... mas) {
		String[] r = new String[4 + mas.length];
		r[0] = "desde";
		r[1] = desde.toString();
		r[2] = "hasta";
		r[3] = hasta.toString();
		System.arraycopy(mas, 0, r, 4, mas.length);
		return r;
	}

	static String cuerpo(ResultActions r) throws Exception {
		return r.andReturn().getResponse().getContentAsString();
	}

	static DocumentContext json(ResultActions r) throws Exception {
		return JsonPath.parse(cuerpo(r));
	}

	/** Cualquier numero del JSON (entero o decimal) como BigDecimal exacto. */
	static BigDecimal num(Object valor) {
		return valor == null ? null : new BigDecimal(valor.toString());
	}
}
