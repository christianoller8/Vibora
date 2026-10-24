package com.vibora.incidencia;

import com.vibora.TestData;
import com.vibora.bloqueo.BloqueoPista;
import com.vibora.cargo.Cargo;
import com.vibora.cargo.EstadoCargo;
import com.vibora.notificacion.Notificacion;
import com.vibora.notificacion.TipoNotificacion;
import com.vibora.reserva.EstadoReserva;
import com.vibora.reserva.Reserva;
import com.vibora.tarifa.TarifaFranja;
import com.vibora.tarifa.TipoTarifa;
import com.vibora.usuario.Rol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Concurrencia REAL del bloqueo de pistas (commits de verdad, una conexion por peticion, reloj real: las reservas son
 * dentro de 30 dias). Lo que se prueba:
 * <ul>
 * <li>reservar contra crear un bloqueo: la pista se serializa con un bloqueo compartido (reservas) frente a uno
 * exclusivo (bloqueo), asi que las "afectadas" que devuelve el bloqueo son EXACTAMENTE las reservas confirmadas que
 * solapan, y ninguna reserva entra despues;</li>
 * <li>cancelar las afectadas dos veces a la vez (o desde dos bloqueos que solapan): se cancela cada reserva una vez,
 * un reembolso y un aviso por jugador, sin interbloqueos ni 500;</li>
 * <li>crear un bloqueo para una incidencia mientras otra persona la rechaza: nunca queda un bloqueo activo ligado a una
 * incidencia terminada.</li>
 * </ul>
 */
class BloqueoConcurrenciaTests extends IncidenciaSinTransaccionBase {

	private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
	private static final Pattern ID_AFECTADA = Pattern.compile("\\{\"id\":(\\d+),\"inicio\"");
	private static final String MENSAJE = "La pista no está disponible por mantenimiento";

	LocalDate fecha;
	List<Persona> jugadores = new ArrayList<>();

	@BeforeEach
	void prepararReservas() throws Exception {
		fecha = LocalDate.now(MADRID).plusDays(30);
		for (DayOfWeek dia : DayOfWeek.values()) {
			TarifaFranja t = TestData.tarifa(club, dia, TipoTarifa.NORMAL);
			t.setHoraInicio(LocalTime.of(8, 0));
			t.setHoraFin(LocalTime.of(23, 0));
			t.setPrecioHora(new BigDecimal("20.00"));
			tarifas.saveAndFlush(t);
		}
		jugadores.clear();
		for (int i = 0; i < 6; i++) {
			jugadores.add(persona("reservista" + i + "@vibora.com", Rol.JUGADOR, null));
		}
	}

	/** Casilla k de la rejilla del dia (08:00 local + k * 90 minutos). */
	private Instant casilla(int k) {
		return fecha.atTime(8, 0).atZone(MADRID).toInstant().plusSeconds(k * 90L * 60);
	}

	private int reservar(Persona p, Instant inicio) throws Exception {
		return mvc.perform(post("/api/clubes/" + club.getId() + "/pistas/" + pista.getId() + "/reservas")
						.header("Authorization", p.token()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"inicio\":\"" + inicio + "\"}"))
				.andReturn().getResponse().getStatus();
	}

	private MvcResult bloquear(Persona p, String cuerpo) throws Exception {
		return mvc.perform(post("/api/clubes/" + club.getId() + "/pistas/" + pista.getId() + "/bloqueos")
				.header("Authorization", p.token()).contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andReturn();
	}

	private String cuerpo(Instant desde, Instant hasta, Long incidencia) {
		return "{\"motivo\":\"Reparar el cristal\",\"desde\":\"" + desde + "\",\"hasta\":\"" + hasta + "\""
				+ (incidencia == null ? "" : ",\"incidenciaId\":" + incidencia) + "}";
	}

	private Set<Long> idsDeAfectadas(String cuerpoRespuesta) {
		String afectadas = cuerpoRespuesta.substring(cuerpoRespuesta.indexOf("\"afectadas\":"));
		Set<Long> ids = new HashSet<>();
		Matcher m = ID_AFECTADA.matcher(afectadas);
		while (m.find()) {
			ids.add(Long.parseLong(m.group(1)));
		}
		return ids;
	}

	private int cancelarAfectadas(Persona p, long bloqueo) throws Exception {
		return cancelarAfectadasRespuesta(p, bloqueo).getResponse().getStatus();
	}

	private MvcResult cancelarAfectadasRespuesta(Persona p, long bloqueo) throws Exception {
		return mvc.perform(post("/api/clubes/" + club.getId() + "/pistas/" + pista.getId() + "/bloqueos/" + bloqueo
				+ "/cancelar-afectadas").header("Authorization", p.token())).andReturn();
	}

	private static int canceladasEn(MvcResult r) throws Exception {
		Matcher m = Pattern.compile("\"canceladas\":(\\d+)").matcher(r.getResponse().getContentAsString());
		assertThat(m.find()).as(r.getResponse().getContentAsString()).isTrue();
		return Integer.parseInt(m.group(1));
	}

	private static long idBloqueo(MvcResult r) throws Exception {
		Matcher m = ID.matcher(r.getResponse().getContentAsString());
		assertThat(m.find()).as(r.getResponse().getContentAsString()).isTrue();
		return Long.parseLong(m.group(1));
	}

	/** Reserva futura con organizador, un segundo jugador y un cargo (pendiente o pagado) del segundo al organizador. */
	private Reserva reservaConCargo(int k, Persona organizador, Persona deudor, EstadoCargo estado) {
		Instant inicio = casilla(k);
		Reserva r = reservas.saveAndFlush(
				TestData.reserva(pista, usuarios.findById(organizador.id()).orElseThrow(), inicio, inicio.plusSeconds(90 * 60),
						EstadoReserva.CONFIRMADA));
		participantes.saveAndFlush(TestData.participante(r, usuarios.findById(organizador.id()).orElseThrow(), 1));
		participantes.saveAndFlush(TestData.participante(r, usuarios.findById(deudor.id()).orElseThrow(), 2));
		cargos.saveAndFlush(TestData.cargo(r, usuarios.findById(deudor.id()).orElseThrow(),
				usuarios.findById(organizador.id()).orElseThrow(), "5.00", estado));
		return r;
	}

	private List<Notificacion> avisosDeCancelacion() {
		return notificaciones.findAll().stream().filter(n -> n.getTipo() == TipoNotificacion.RESERVA_CANCELADA).toList();
	}

	// ---- (1) reservar contra crear el bloqueo ----

	@RepeatedTest(3)
	void seisReservasCompitiendoConUnBloqueo_lasAfectadasSonExactamenteLasReservasSolapadas_yNingunaEntraDespues() throws Exception {
		Instant desde = casilla(0);
		Instant hasta = casilla(6); // las casillas 0..5 quedan bloqueadas
		List<Callable<Object>> tareas = new ArrayList<>();
		for (int k = 0; k < 6; k++) {
			Persona quien = jugadores.get(k);
			int casilla = k;
			tareas.add(() -> reservar(quien, casilla(casilla)));
		}
		tareas.add(() -> bloquear(admin, cuerpo(desde, hasta, null)));

		List<Object> resultados = lanzarALaVez(tareas);

		MvcResult creacion = (MvcResult) resultados.get(6);
		assertThat(creacion.getResponse().getStatus()).as(creacion.getResponse().getContentAsString()).isEqualTo(201);
		List<Integer> estados = resultados.subList(0, 6).stream().map(o -> (Integer) o).toList();
		assertThat(estados).as("estados de las reservas: %s", estados).allMatch(s -> s == 201 || s == 409);

		Set<Long> afectadas = idsDeAfectadas(creacion.getResponse().getContentAsString());
		Set<Long> solapadasEnBd = new HashSet<>();
		for (Reserva r : reservas.findAll()) {
			if (r.getPista().getId().equals(pista.getId()) && r.getEstado() == EstadoReserva.CONFIRMADA
					&& r.getInicio().isBefore(hasta) && r.getFin().isAfter(desde)) {
				solapadasEnBd.add(r.getId());
			}
		}
		assertThat(afectadas).as("afectadas devueltas = confirmadas que solapan en la BD").isEqualTo(solapadasEnBd);
		assertThat(estados.stream().filter(s -> s == 201).count()).as("cada 201 esta en la lista")
				.isEqualTo((long) afectadas.size());

		// ya confirmado el bloqueo: nada mas entra en el rango (ni siquiera en una casilla libre)...
		for (int k = 0; k < 6; k++) {
			Persona quien = jugadores.get(k);
			int casilla = k;
			MvcResult rechazada = mvc.perform(post("/api/clubes/" + club.getId() + "/pistas/" + pista.getId() + "/reservas")
							.header("Authorization", quien.token()).contentType(MediaType.APPLICATION_JSON)
							.content("{\"inicio\":\"" + casilla(casilla) + "\"}")).andReturn();
			assertThat(rechazada.getResponse().getStatus()).isEqualTo(409);
			assertThat(rechazada.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains(MENSAJE);
		}
		// ... y fuera del rango la pista sigue reservable
		assertThat(reservar(jugadores.get(0), casilla(6))).isEqualTo(201);
	}

	// ---- (2) cancelar las afectadas dos veces a la vez ----

	@RepeatedTest(3)
	void cancelarAfectadasDosVecesAlaVez_cadaReservaSeCancelaUnaVez_unReembolsoYUnAvisoPorJugador() throws Exception {
		List<Long> creadas = new ArrayList<>();
		for (int k = 0; k < 4; k++) {
			creadas.add(reservaConCargo(k, jugadores.get(k), jugadores.get(4), k % 2 == 0 ? EstadoCargo.PENDIENTE : EstadoCargo.PAGADO).getId());
		}
		MvcResult creado = bloquear(admin, cuerpo(casilla(0), casilla(4), null));
		assertThat(creado.getResponse().getStatus()).isEqualTo(201);
		assertThat(idsDeAfectadas(creado.getResponse().getContentAsString())).hasSize(4);
		long bloqueo = idBloqueo(creado);

		List<MvcResult> respuestas = lanzarALaVez(List.<Callable<MvcResult>>of(
				() -> cancelarAfectadasRespuesta(admin, bloqueo),
				() -> cancelarAfectadasRespuesta(admin2, bloqueo)));

		assertThat(respuestas).extracting(r -> r.getResponse().getStatus()).as("sin 500 ni interbloqueos").containsOnly(200);
		int total = canceladasEn(respuestas.get(0)) + canceladasEn(respuestas.get(1));
		assertThat(total).as("entre las dos cancelan cada reserva una sola vez").isEqualTo(4);
		assertThat(reservas.findAllById(creadas)).hasSize(4).allMatch(r -> r.getEstado() == EstadoReserva.CANCELADA);
		assertThat(cargos.findAll()).extracting(Cargo::getEstado).containsExactlyInAnyOrder(EstadoCargo.ANULADO,
				EstadoCargo.REEMBOLSADO, EstadoCargo.ANULADO, EstadoCargo.REEMBOLSADO);
		List<Notificacion> avisos = avisosDeCancelacion();
		assertThat(avisos).as("2 jugadores por reserva, un aviso cada uno").hasSize(8);
		assertThat(avisos.stream().map(n -> n.getUsuario().getId() + ":" + n.getReserva().getId()).distinct()).hasSize(8);
	}

	@RepeatedTest(3)
	void cancelarAfectadasDeDosBloqueosQueSolapan_aLaVez_noHayDobleReembolsoNiDobleAvisoNiInterbloqueo() throws Exception {
		List<Long> creadas = new ArrayList<>();
		for (int k = 0; k < 6; k++) {
			creadas.add(reservaConCargo(k, jugadores.get(k), jugadores.get((k + 1) % 6),
					k % 2 == 0 ? EstadoCargo.PENDIENTE : EstadoCargo.PAGADO).getId());
		}
		long primero = idBloqueo(bloquear(admin, cuerpo(casilla(0), casilla(4), null))); // casillas 0..3
		long segundo = idBloqueo(bloquear(admin, cuerpo(casilla(2), casilla(6), null))); // casillas 2..5 (2 y 3 en comun)

		List<MvcResult> respuestas = lanzarALaVez(List.<Callable<MvcResult>>of(
				() -> cancelarAfectadasRespuesta(admin, primero),
				() -> cancelarAfectadasRespuesta(admin2, segundo)));

		assertThat(respuestas).extracting(r -> r.getResponse().getStatus()).containsOnly(200);
		assertThat(canceladasEn(respuestas.get(0)) + canceladasEn(respuestas.get(1))).isEqualTo(6);
		assertThat(reservas.findAllById(creadas)).hasSize(6).allMatch(r -> r.getEstado() == EstadoReserva.CANCELADA);
		assertThat(cargos.findAll()).extracting(Cargo::getEstado).containsOnly(EstadoCargo.ANULADO, EstadoCargo.REEMBOLSADO);
		assertThat(cargos.findAll().stream().filter(c -> c.getEstado() == EstadoCargo.REEMBOLSADO)).hasSize(3);
		List<Notificacion> avisos = avisosDeCancelacion();
		assertThat(avisos).hasSize(12);
		assertThat(avisos.stream().map(n -> n.getUsuario().getId() + ":" + n.getReserva().getId()).distinct()).hasSize(12);
	}

	// ---- (3) bloquear por una incidencia mientras se rechaza ----

	@RepeatedTest(3)
	void bloquearPorUnaIncidenciaMientrasSeRechaza_nuncaQuedaUnBloqueoActivoLigadoAUnaIncidenciaTerminada() throws Exception {
		long incidencia = idDe(mvc.perform(post("/api/clubes/" + club.getId() + "/incidencias")
						.header("Authorization", jugador.token()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"titulo\":\"Cristal\",\"descripcion\":\"d\",\"categoria\":\"CRISTAL\",\"pistaId\":" + pista.getId() + "}"))
				.andReturn().getResponse().getContentAsString());

		List<Integer> estados = lanzarALaVez(List.<Callable<Integer>>of(
				() -> bloquear(admin, cuerpo(casilla(0), casilla(6), incidencia)).getResponse().getStatus(),
				() -> mvc.perform(post("/api/incidencias/" + incidencia + "/estado").header("Authorization", admin2.token())
								.contentType(MediaType.APPLICATION_JSON)
								.content("{\"estado\":\"RECHAZADA\",\"comentario\":\"No procede\"}"))
						.andReturn().getResponse().getStatus()));

		assertThat(estados.get(1)).as("rechazo: %s", estados).isEqualTo(200);
		assertThat(estados.get(0)).as("bloqueo: %s", estados).isIn(201, 409);
		assertThat(incidencias.findById(incidencia).orElseThrow().getEstado()).isEqualTo(EstadoIncidencia.RECHAZADA);
		List<BloqueoPista> sinLevantar = bloqueos.findAll().stream().filter(b -> b.getLevantadoEn() == null).toList();
		assertThat(sinLevantar).as("ningun bloqueo activo de una incidencia rechazada").isEmpty();
		List<IncidenciaEvento> historial = eventos.findByIncidenciaIdOrderByCreadoEnAscIdAsc(incidencia);
		long creados = historial.stream().filter(e -> e.getTipo() == TipoEventoIncidencia.BLOQUEO_CREADO).count();
		long levantados = historial.stream().filter(e -> e.getTipo() == TipoEventoIncidencia.BLOQUEO_LEVANTADO).count();
		assertThat(creados).isEqualTo(estados.get(0) == 201 ? 1 : 0);
		assertThat(levantados).as("se levanta exactamente lo que se creo").isEqualTo(creados);
	}

	private static long idDe(String cuerpo) {
		Matcher m = ID.matcher(cuerpo);
		assertThat(m.find()).as(cuerpo).isTrue();
		return Long.parseLong(m.group(1));
	}
}
