package com.vibora.incidencia;

import com.vibora.TestData;
import com.vibora.cargo.Cargo;
import com.vibora.cargo.EstadoCargo;
import com.vibora.notificacion.Notificacion;
import com.vibora.notificacion.TipoNotificacion;
import com.vibora.reserva.EstadoReserva;
import com.vibora.reserva.Reserva;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Que hace un bloqueo: reservas rechazadas, agenda marcada y cancelacion en cascada de las afectadas. */
class BloqueoEfectosTests extends BloqueoApiTestBase {

	private static final String MENSAJE = "La pista no está disponible por mantenimiento";
	private static final LocalDate DIA = LocalDate.of(2030, 1, 1);

	// ---- reservar ----

	@Test
	void conUnBloqueoActivo_reservarEnEseHorario_409_ySinReservaCreada() throws Exception {
		bloqueoCreado(admin, pista, null, null);
		long antes = reservas.count();

		reservar(otro, pista, "2030-01-01T13:00:00Z").andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value(MENSAJE));

		assertThat(reservas.count()).isEqualTo(antes);
	}

	@Test
	void unBloqueoSoloAfectaALaPistaBloqueada() throws Exception {
		bloqueoCreado(admin, pista, null, null);

		reservar(otro, pista2, "2030-01-01T13:00:00Z").andExpect(status().isCreated());
	}

	@Test
	void elRangoEsSemiabierto_lasCasillasAdyacentesSePuedenReservar() throws Exception {
		bloqueoCreado(admin, pista, "2030-01-01T11:30:00Z", "2030-01-01T16:00:00Z");

		reservar(otro, pista, "2030-01-01T08:30:00Z").andExpect(status().isCreated()); // acaba justo en 'desde'
		reservar(otro, pista, "2030-01-01T11:30:00Z").andExpect(status().isConflict()).andExpect(jsonPath("$.error").value(MENSAJE));
		reservar(otro, pista, "2030-01-01T13:00:00Z").andExpect(status().isConflict());
		reservar(otro, pista, "2030-01-01T14:30:00Z").andExpect(status().isConflict()); // acaba justo en 'hasta'
		reservar(otro, pista, "2030-01-01T16:00:00Z").andExpect(status().isCreated()); // empieza justo en 'hasta'
	}

	@Test
	void unaCasillaQueSoloSolapaParcialmenteElBloqueo_tambienSeRechaza() throws Exception {
		bloqueoCreado(admin, pista, "2030-01-01T14:00:00Z", "2030-01-01T14:10:00Z"); // 10 minutos dentro de la casilla de las 13:00

		reservar(otro, pista, "2030-01-01T13:00:00Z").andExpect(status().isConflict()).andExpect(jsonPath("$.error").value(MENSAJE));
	}

	@Test
	void antesDelInicioDelBloqueoSeReservaNormal() throws Exception {
		bloqueoCreado(admin, pista, "2030-01-01T17:30:00Z", null);

		reservar(otro, pista, "2030-01-01T13:00:00Z").andExpect(status().isCreated());
		reservar(mant2, pista, "2030-01-01T19:00:00Z").andExpect(status().isConflict()).andExpect(jsonPath("$.error").value(MENSAJE));
	}

	@Test
	void trasLevantarElBloqueo_seVuelveAReservar() throws Exception {
		long bloqueo = bloqueoCreado(admin, pista, null, null);
		reservar(otro, pista, "2030-01-01T13:00:00Z").andExpect(status().isConflict());

		levantar(admin, pista, bloqueo).andExpect(status().isNoContent());

		reservar(otro, pista, "2030-01-01T13:00:00Z").andExpect(status().isCreated());
	}

	@Test
	void trasLevantarlo_otroBloqueoDeLaMismaPistaSigueActuando() throws Exception {
		long primero = bloqueoCreado(admin, pista, null, null);
		bloqueoCreado(admin, pista, "2030-01-01T13:00:00Z", "2030-01-01T14:30:00Z");

		levantar(admin, pista, primero).andExpect(status().isNoContent());

		reservar(otro, pista, "2030-01-01T13:00:00Z").andExpect(status().isConflict()).andExpect(jsonPath("$.error").value(MENSAJE));
		reservar(otro, pista, "2030-01-01T16:00:00Z").andExpect(status().isCreated());
	}

	@Test
	void unaPistaInactiva_sigueDiciendoQueNoEstaActiva() throws Exception {
		pista2.setActiva(false);
		pistas.saveAndFlush(pista2);
		bloqueoCreado(admin, pista2, null, null);

		reservar(otro, pista2, "2030-01-01T13:00:00Z").andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("La pista no está activa"));
	}

	// ---- agenda ----

	@Test
	void laAgendaMarcaLasCasillasBloqueadas_noDisponibles_conElIndicador() throws Exception {
		// rejilla del 2030-01-01 (UTC): 07:00 08:30 10:00 11:30 13:00 14:30 16:00 17:30 19:00 20:30
		bloqueoCreado(admin, pista, "2030-01-01T11:30:00Z", "2030-01-01T16:00:00Z");

		agenda(otro, pista, DIA).andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(10)))
				.andExpect(jsonPath("$[2].inicio").value("2030-01-01T10:00:00Z"))
				.andExpect(jsonPath("$[2].bloqueada").value(false))
				.andExpect(jsonPath("$[2].disponible").value(false)) // ocupada por la reserva del jugador, no por el bloqueo
				.andExpect(jsonPath("$[1].bloqueada").value(false))
				.andExpect(jsonPath("$[1].disponible").value(true))
				.andExpect(jsonPath("$[3].bloqueada").value(true))
				.andExpect(jsonPath("$[3].disponible").value(false))
				.andExpect(jsonPath("$[4].bloqueada").value(true))
				.andExpect(jsonPath("$[4].disponible").value(false))
				.andExpect(jsonPath("$[5].bloqueada").value(true))
				.andExpect(jsonPath("$[5].disponible").value(false))
				.andExpect(jsonPath("$[6].inicio").value("2030-01-01T16:00:00Z"))
				.andExpect(jsonPath("$[6].bloqueada").value(false))
				.andExpect(jsonPath("$[6].disponible").value(true))
				.andExpect(jsonPath("$[9].bloqueada").value(false));
		// la otra pista no se entera
		agenda(otro, pista2, DIA).andExpect(jsonPath("$[3].bloqueada").value(false)).andExpect(jsonPath("$[3].disponible").value(true));
	}

	@Test
	void sinBloqueos_laAgendaTraeBloqueadaFalsa() throws Exception {
		agenda(otro, pista, DIA).andExpect(jsonPath("$[0].bloqueada").value(false)).andExpect(jsonPath("$[9].bloqueada").value(false));
	}

	@Test
	void unBloqueoAbierto_marcaTodasLasCasillasDesdeSuInicio_yNoLasDiasAnterioresALaFecha() throws Exception {
		bloqueoCreado(admin, pista, "2030-01-02T00:00:00Z", null);

		agenda(otro, pista, DIA).andExpect(jsonPath("$[0].bloqueada").value(false)).andExpect(jsonPath("$[9].bloqueada").value(false));
		agenda(otro, pista, DIA.plusDays(1)).andExpect(jsonPath("$[0].bloqueada").value(true))
				.andExpect(jsonPath("$[9].bloqueada").value(true));
		agenda(otro, pista, DIA.plusDays(30)).andExpect(jsonPath("$[4].bloqueada").value(true));
	}

	@Test
	void unBloqueoLevantadoOCaducado_noMarcaNada() throws Exception {
		long levantado = bloqueoCreado(admin, pista, null, null);
		levantar(admin, pista, levantado).andExpect(status().isNoContent());
		agenda(otro, pista, DIA).andExpect(jsonPath("$[3].bloqueada").value(false));

		bloqueoCreado(admin, pista, "2030-01-01T11:30:00Z", "2030-01-01T16:00:00Z");
		agenda(otro, pista, DIA).andExpect(jsonPath("$[3].bloqueada").value(true));

		reloj.fijar(z("2030-01-01T16:00:00Z")); // ha caducado
		agenda(otro, pista, DIA).andExpect(jsonPath("$[3].bloqueada").value(false)).andExpect(jsonPath("$[5].bloqueada").value(false));
	}

	// ---- cancelar afectadas ----

	/** Reserva del organizador con dos jugadores mas, un cargo pendiente y otro ya pagado. */
	private Reserva reservaConCargos(String inicio) {
		Reserva r = reservaEn(otro, pista, inicio);
		anadirJugador(r, mant2, 2);
		anadirJugador(r, monitor, 3);
		cargos.saveAndFlush(TestData.cargo(r, mant2.usuario(), otro.usuario(), "11.25", EstadoCargo.PENDIENTE));
		cargos.saveAndFlush(TestData.cargo(r, monitor.usuario(), otro.usuario(), "11.25", EstadoCargo.PAGADO));
		return r;
	}

	private Reserva reservaPorId(long id) {
		refrescar();
		return reservas.findById(id).orElseThrow();
	}

	private List<Cargo> cargosDe(Reserva r) {
		refrescar();
		return cargos.findByReservaId(r.getId());
	}

	@Test
	void cancelarAfectadas_cancelaLasReservas_anulaYReembolsaLosCargos_yAvisaATodosSalvoAlAdministrador() throws Exception {
		Reserva r1 = reservaConCargos("2030-01-01T13:00:00Z");
		Reserva sinCargos = reservaEn(jugador, pista, "2030-01-01T14:30:00Z");
		Reserva fuera = reservaEn(otro, pista, "2030-01-01T19:00:00Z"); // fuera del rango
		Reserva otraPista = reservaEn(otro, pista2, "2030-01-01T13:00:00Z");
		long bloqueo = bloqueoCreado(admin, pista, "2030-01-01T12:00:00Z", "2030-01-01T16:00:00Z");
		long avisosAntes = totalNotifs();

		cancelarAfectadas(admin, pista, bloqueo).andExpect(status().isOk())
				.andExpect(jsonPath("$.canceladas").value(2))
				.andExpect(jsonPath("$.ids", hasSize(2)))
				.andExpect(jsonPath("$.ids[0]").value(r1.getId()))
				.andExpect(jsonPath("$.ids[1]").value(sinCargos.getId()));

		assertThat(reservaPorId(r1.getId()).getEstado()).isEqualTo(EstadoReserva.CANCELADA);
		assertThat(reservaPorId(sinCargos.getId()).getEstado()).isEqualTo(EstadoReserva.CANCELADA);
		assertThat(reservaPorId(fuera.getId()).getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
		assertThat(reservaPorId(otraPista.getId()).getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
		assertThat(cargosDe(r1)).extracting(Cargo::getEstado).containsExactlyInAnyOrder(EstadoCargo.ANULADO, EstadoCargo.REEMBOLSADO);

		// r1: organizador + 2 jugadores; sinCargos: su organizador. El administrador nunca recibe aviso.
		List<Notificacion> nuevas = notificaciones.findAll().stream()
				.filter(n -> n.getTipo() == TipoNotificacion.RESERVA_CANCELADA).toList();
		assertThat(totalNotifs() - avisosAntes).isEqualTo(4);
		assertThat(nuevas).hasSize(4);
		assertThat(nuevas).extracting(n -> n.getUsuario().getId())
				.containsExactlyInAnyOrder(otro.id(), mant2.id(), monitor.id(), jugador.id());
		assertThat(nuevas).allSatisfy(n -> assertThat(n.getMensaje()).contains("El club ha cancelado la reserva")
				.contains("mantenimiento").contains("Pista 1"));
		assertThat(nuevas).allSatisfy(n -> assertThat(n.getReserva()).isNotNull());
	}

	@Test
	void cancelarAfectadas_dosVeces_laSegundaNoHaceNada() throws Exception {
		Reserva r = reservaConCargos("2030-01-01T13:00:00Z");
		long bloqueo = bloqueoCreado(admin, pista, null, null);

		cancelarAfectadas(admin, pista, bloqueo).andExpect(jsonPath("$.canceladas").value(2)); // la del jugador (10:00Z) y esta
		long avisos = totalNotifs();
		List<EstadoCargo> estados = cargosDe(r).stream().map(Cargo::getEstado).toList();

		cancelarAfectadas(admin, pista, bloqueo).andExpect(status().isOk())
				.andExpect(jsonPath("$.canceladas").value(0)).andExpect(jsonPath("$.ids", hasSize(0)));

		assertThat(totalNotifs()).as("sin avisos duplicados").isEqualTo(avisos);
		assertThat(cargosDe(r).stream().map(Cargo::getEstado).toList()).isEqualTo(estados);
	}

	@Test
	void cancelarAfectadas_noCancelaLasQueYaHanEmpezado() throws Exception {
		Reserva futura = reservaEn(otro, pista, "2030-01-01T13:00:00Z");
		long bloqueo = bloqueoCreado(admin, pista, "2030-01-01T09:00:00Z", "2030-01-01T20:00:00Z");
		reloj.fijar(z("2030-01-01T12:00:00Z")); // la del jugador (10:00Z) ya esta en marcha

		cancelarAfectadas(admin, pista, bloqueo).andExpect(jsonPath("$.canceladas").value(1))
				.andExpect(jsonPath("$.ids[0]").value(futura.getId()));

		assertThat(reservas.findAll().stream().filter(r -> r.getEstado() == EstadoReserva.CONFIRMADA)).hasSize(1);
	}

	@Test
	void cancelarAfectadas_conReservaYaCancelada_laIgnora() throws Exception {
		Reserva r = reservaEn(otro, pista, "2030-01-01T13:00:00Z");
		long bloqueo = bloqueoCreado(admin, pista, "2030-01-01T12:00:00Z", "2030-01-01T16:00:00Z");
		mvc.perform(post("/api/reservas/" + r.getId() + "/cancelar").header("Authorization", admin.token()))
				.andExpect(status().isOk());
		long avisos = totalNotifs();

		cancelarAfectadas(admin, pista, bloqueo).andExpect(jsonPath("$.canceladas").value(0));

		assertThat(totalNotifs()).isEqualTo(avisos);
	}

	@Test
	void cancelarAfectadas_sinPermiso_403_yRecursosAjenos_404() throws Exception {
		Reserva r = reservaEn(otro, pista, "2030-01-01T13:00:00Z");
		long bloqueo = bloqueoCreado(admin, pista, null, null);

		for (Persona quien : List.of(mant1, jugador, adminB, monitor, otro)) {
			cancelarAfectadas(quien, pista, bloqueo).andExpect(status().isForbidden());
		}
		cancelarAfectadas(admin, pista, 999_999L).andExpect(status().isNotFound());
		cancelarAfectadas(admin, pista2, bloqueo).andExpect(status().isNotFound());
		assertThat(reservaPorId(r.getId()).getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
	}

	@Test
	void cancelarAfectadas_deUnBloqueoLevantado_409_yNoCancelaNada() throws Exception {
		Reserva r = reservaEn(otro, pista, "2030-01-01T13:00:00Z");
		long bloqueo = bloqueoCreado(admin, pista, null, null);
		levantar(admin, pista, bloqueo).andExpect(status().isNoContent());

		cancelarAfectadas(admin, pista, bloqueo).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("El bloqueo ya no está activo"));

		assertThat(reservaPorId(r.getId()).getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
	}

	@Test
	void unaReservaNuevaNoPuedeColarseTrasElBloqueo_ElBloqueoNoLaCancelaAutomaticamente() throws Exception {
		// opcion A: hacia adelante. Crear el bloqueo NO toca las reservas existentes; solo cancelar-afectadas lo hace.
		Reserva r = reservaEn(otro, pista, "2030-01-01T13:00:00Z");

		bloqueoCreado(admin, pista, null, null);

		assertThat(reservaPorId(r.getId()).getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
		assertThat(notifsDe(otro, TipoNotificacion.RESERVA_CANCELADA)).isEmpty();
	}
}
