package com.vibora.incidencia;

import com.vibora.TestData;
import com.vibora.bloqueo.BloqueoPista;
import com.vibora.bloqueo.BloqueoPistaRepository;
import com.vibora.cargo.CargoRepository;
import com.vibora.pista.Pista;
import com.vibora.reserva.EstadoReserva;
import com.vibora.reserva.Reserva;
import com.vibora.tarifa.TarifaFranja;
import com.vibora.tarifa.TarifaFranjaRepository;
import com.vibora.tarifa.TipoTarifa;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base de los tests de bloqueo de pistas (rollback por test, reloj en 2030-01-01T00:00Z). Club A con "Pista 1" y
 * "Pista 2", tarifa plana todos los dias de 08:00 a 23:00 (para poder reservar por la API) y las personas de
 * {@link IncidenciaApiTestBase}. Hora de Madrid en enero = UTC+1, asi que la rejilla de casillas de 90 minutos cae en
 * 07:00Z, 08:30Z, 10:00Z, 11:30Z, 13:00Z, 14:30Z, 16:00Z, 17:30Z, 19:00Z y 20:30Z.
 */
abstract class BloqueoApiTestBase extends IncidenciaApiTestBase {

	@Autowired TarifaFranjaRepository tarifas;
	@Autowired BloqueoPistaRepository bloqueos;
	@Autowired CargoRepository cargos;

	Pista pista2;

	@BeforeEach
	void prepararBloqueos() {
		pista2 = pistas.saveAndFlush(TestData.pista(club, "Pista 2"));
		for (DayOfWeek dia : DayOfWeek.values()) {
			TarifaFranja t = TestData.tarifa(club, dia, TipoTarifa.NORMAL);
			t.setHoraInicio(LocalTime.of(8, 0));
			t.setHoraFin(LocalTime.of(23, 0));
			t.setPrecioHora(new BigDecimal("20.00"));
			tarifas.saveAndFlush(t);
		}
	}

	static Instant z(String iso) {
		return Instant.parse(iso);
	}

	// ---- datos ----

	/** Reserva confirmada de 90 minutos que empieza en el instante dado; el organizador es el puesto 1. */
	Reserva reservaEn(Persona organizador, Pista donde, String inicio) {
		return reservaEn(organizador, donde, inicio, EstadoReserva.CONFIRMADA);
	}

	Reserva reservaEn(Persona organizador, Pista donde, String inicio, EstadoReserva estado) {
		Instant desde = z(inicio);
		Reserva r = reservas.saveAndFlush(
				TestData.reserva(donde, organizador.usuario(), desde, desde.plusSeconds(90 * 60), estado));
		participantes.saveAndFlush(TestData.participante(r, organizador.usuario(), 1));
		refrescar(); // la coleccion 'participantes' de la reserva ya cargada no se entera de las altas: se vuelve a leer
		return r;
	}

	void anadirJugador(Reserva r, Persona p, int puesto) {
		participantes.saveAndFlush(TestData.participante(r, p.usuario(), puesto));
		refrescar();
	}

	BloqueoPista bloqueoPorId(long id) {
		refrescar();
		return bloqueos.findById(id).orElseThrow();
	}

	// ---- llamadas HTTP ----

	String rutaBloqueos(Pista donde) {
		return "/api/clubes/" + donde.getClub().getId() + "/pistas/" + donde.getId() + "/bloqueos";
	}

	/** Cuerpo JSON de una peticion de bloqueo; los parametros nulos se omiten. */
	static String cuerpo(String motivo, String desde, String hasta, Long incidenciaId) {
		return json("motivo", texto(motivo), "desde", texto(desde), "hasta", texto(hasta), "incidenciaId",
				incidenciaId == null ? null : incidenciaId.toString());
	}

	ResultActions bloquear(Persona p, Pista donde, String cuerpoJson) throws Exception {
		return mvc.perform(post(rutaBloqueos(donde)).header("Authorization", p.token())
				.contentType(MediaType.APPLICATION_JSON).content(cuerpoJson));
	}

	ResultActions bloquear(Persona p, Pista donde, String desde, String hasta) throws Exception {
		return bloquear(p, donde, cuerpo("Reparar el cristal", desde, hasta, null));
	}

	long bloqueoCreado(Persona p, Pista donde, String desde, String hasta) throws Exception {
		return idDe(bloquear(p, donde, desde, hasta).andExpect(status().isCreated()));
	}

	ResultActions listarBloqueos(Persona p, Pista donde, String activos) throws Exception {
		var peticion = get(rutaBloqueos(donde)).header("Authorization", p.token());
		if (activos != null) {
			peticion.param("activos", activos);
		}
		return mvc.perform(peticion);
	}

	ResultActions levantar(Persona p, Pista donde, long bloqueoId) throws Exception {
		return mvc.perform(delete(rutaBloqueos(donde) + "/" + bloqueoId).header("Authorization", p.token()));
	}

	ResultActions afectadas(Persona p, Pista donde, long bloqueoId) throws Exception {
		return mvc.perform(get(rutaBloqueos(donde) + "/" + bloqueoId + "/afectadas").header("Authorization", p.token()));
	}

	ResultActions cancelarAfectadas(Persona p, Pista donde, long bloqueoId) throws Exception {
		return mvc.perform(post(rutaBloqueos(donde) + "/" + bloqueoId + "/cancelar-afectadas")
				.header("Authorization", p.token()));
	}

	ResultActions bloquearDesdeIncidencia(Persona p, long incidencia) throws Exception {
		return mvc.perform(post("/api/incidencias/" + incidencia + "/bloquear-pista").header("Authorization", p.token()));
	}

	ResultActions reservar(Persona p, Pista donde, String inicio) throws Exception {
		return mvc.perform(post("/api/clubes/" + donde.getClub().getId() + "/pistas/" + donde.getId() + "/reservas")
				.header("Authorization", p.token()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"inicio\":\"" + inicio + "\"}"));
	}

	ResultActions agenda(Persona p, Pista donde, LocalDate fecha) throws Exception {
		return mvc.perform(get("/api/clubes/" + donde.getClub().getId() + "/pistas/" + donde.getId() + "/agenda")
				.param("fecha", fecha.toString()).header("Authorization", p.token()));
	}

	List<BloqueoPista> todosLosBloqueos() {
		refrescar();
		return bloqueos.findAll();
	}
}
