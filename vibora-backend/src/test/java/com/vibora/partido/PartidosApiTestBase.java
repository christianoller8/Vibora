package com.vibora.partido;

import com.vibora.ApiTestBase;
import com.vibora.RelojMutable;
import com.vibora.RelojTestConfig;
import com.vibora.TestData;
import com.vibora.cargo.Cargo;
import com.vibora.cargo.CargoRepository;
import com.vibora.cargo.PagoRepository;
import com.vibora.club.Club;
import com.vibora.notificacion.Notificacion;
import com.vibora.notificacion.NotificacionRepository;
import com.vibora.notificacion.TipoNotificacion;
import com.vibora.pista.Pista;
import com.vibora.pista.PistaRepository;
import com.vibora.reserva.ReservaRepository;
import com.vibora.tarifa.TarifaFranja;
import com.vibora.tarifa.TarifaFranjaRepository;
import com.vibora.tarifa.TipoTarifa;
import com.vibora.usuario.Rol;
import com.vibora.usuario.Usuario;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base de los tests de partidos/cargos/notificaciones. Reloj fijado en 2030-01-01T00:00Z; todas las reservas son
 * el lunes 2030-01-07 (Madrid, UTC+1). Con tarifaPlana("30.00") una reserva de 90 min cuesta 45.00 => cuota 11.25.
 * Todo pasa por HTTP; los repositorios solo se usan para comprobar el estado de la BD.
 */
@Import(RelojTestConfig.class)
abstract class PartidosApiTestBase extends ApiTestBase {

	static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
	static final LocalDate LUNES = LocalDate.of(2030, 1, 7);
	static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

	@Autowired RelojMutable reloj;
	@Autowired PistaRepository pistas;
	@Autowired TarifaFranjaRepository tarifas;
	@Autowired ReservaRepository reservas;
	@Autowired ParticipanteRepository participantes;
	@Autowired CargoRepository cargos;
	@Autowired PagoRepository pagos;
	@Autowired NotificacionRepository notificaciones;
	@Autowired EntityManager em;

	Club club;
	Pista pista;

	/** Usuario con su token. */
	record Jug(Usuario usuario, String token) {
		Long id() {
			return usuario.getId();
		}
	}

	@BeforeEach
	void prepararBase() {
		reloj.fijar(RelojTestConfig.AHORA_INICIAL);
		club = crearClub("Club A");
		pista = pistas.saveAndFlush(TestData.pista(club, "Pista 1"));
	}

	// ---- datos ----

	static Instant local(LocalDate fecha, int hora, int minuto) {
		return fecha.atTime(hora, minuto).atZone(MADRID).toInstant();
	}

	static Instant lunes(int hora, int minuto) {
		return local(LUNES, hora, minuto);
	}

	TarifaFranja tarifa(Club c, int horaIni, int horaFin, String precioHora) {
		TarifaFranja t = TestData.tarifa(c, DayOfWeek.MONDAY, TipoTarifa.NORMAL);
		t.setHoraInicio(LocalTime.of(horaIni, 0));
		t.setHoraFin(LocalTime.of(horaFin, 0));
		t.setPrecioHora(new BigDecimal(precioHora));
		return tarifas.saveAndFlush(t);
	}

	TarifaFranja tarifaPlana(String precioHora) {
		return tarifa(club, 8, 23, precioHora);
	}

	Jug jugador(String email, String nombre, String apellidos) throws Exception {
		Usuario u = TestData.usuario(email, Rol.JUGADOR, null);
		u.setNombre(nombre);
		u.setApellidos(apellidos);
		u.setPasswordHash(passwordEncoder.encode(PASSWORD));
		u = usuarios.saveAndFlush(u);
		return new Jug(u, bearer(email));
	}

	Jug jugador(String nombre) throws Exception {
		return jugador(nombre.toLowerCase() + "@vibora.com", nombre, "Apellido" + nombre);
	}

	// ---- llamadas HTTP ----

	ResultActions reservar(Jug j, Pista p, Instant inicio) throws Exception {
		return mvc.perform(post("/api/clubes/" + p.getClub().getId() + "/pistas/" + p.getId() + "/reservas")
				.header("Authorization", j.token()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"inicio\":\"" + inicio + "\"}"));
	}

	long idDe(ResultActions resultado) throws Exception {
		Matcher m = ID.matcher(resultado.andReturn().getResponse().getContentAsString());
		assertThat(m.find()).isTrue();
		return Long.parseLong(m.group(1));
	}

	long reservaCreada(Jug j, Instant inicio) throws Exception {
		return idDe(reservar(j, pista, inicio).andExpect(status().isCreated()));
	}

	ResultActions abrir(Jug j, long reservaId, boolean abierta) throws Exception {
		return mvc.perform(put("/api/reservas/" + reservaId + "/abierta").header("Authorization", j.token())
				.contentType(MediaType.APPLICATION_JSON).content("{\"abierta\":" + abierta + "}"));
	}

	/** Reserva nueva del organizador, ya marcada como partido abierto. */
	long partidoAbierto(Jug organizador, Instant inicio) throws Exception {
		long id = reservaCreada(organizador, inicio);
		abrir(organizador, id, true).andExpect(status().isOk());
		return id;
	}

	ResultActions apuntarse(Jug j, long reservaId) throws Exception {
		return mvc.perform(post("/api/reservas/" + reservaId + "/apuntarse").header("Authorization", j.token()));
	}

	ResultActions salir(Jug j, long reservaId) throws Exception {
		return mvc.perform(post("/api/reservas/" + reservaId + "/salir").header("Authorization", j.token()));
	}

	ResultActions cancelar(Jug j, long reservaId) throws Exception {
		return mvc.perform(post("/api/reservas/" + reservaId + "/cancelar").header("Authorization", j.token()));
	}

	ResultActions pagar(Jug j, long cargoId) throws Exception {
		return mvc.perform(post("/api/cargos/" + cargoId + "/pagar").header("Authorization", j.token()));
	}

	ResultActions partidos(Jug j) throws Exception {
		return mvc.perform(get("/api/partidos").header("Authorization", j.token()));
	}

	// ---- estado de la BD ----

	void refrescar() {
		em.flush();
		em.clear();
	}

	List<Cargo> cargosDe(long reservaId) {
		refrescar();
		return cargos.findAll().stream().filter(c -> c.getReserva().getId() == reservaId)
				.sorted((a, b) -> Long.compare(a.getId(), b.getId())).toList();
	}

	/** Cargo (cualquier estado) del deudor en la reserva; el unico, o el ultimo si hubo varios. */
	Cargo cargoDe(long reservaId, Jug deudor) {
		List<Cargo> propios = cargosDe(reservaId).stream()
				.filter(c -> c.getDeudor().getId().equals(deudor.id())).toList();
		assertThat(propios).as("cargos de %s en %s", deudor.usuario().getEmail(), reservaId).isNotEmpty();
		return propios.get(propios.size() - 1);
	}

	List<Notificacion> notifsDe(Jug j, TipoNotificacion tipo) {
		refrescar();
		return notificaciones.findAll().stream()
				.filter(n -> n.getUsuario().getId().equals(j.id()) && (tipo == null || n.getTipo() == tipo))
				.sorted((a, b) -> Long.compare(a.getId(), b.getId())).toList();
	}
}
