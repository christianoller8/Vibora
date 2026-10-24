package com.vibora.incidencia;

import com.vibora.bloqueo.BloqueoPista;
import com.vibora.reserva.EstadoReserva;
import com.vibora.reserva.Reserva;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bloqueo de pistas por mantenimiento: crear, listar, levantar, atajo desde una incidencia y levantamiento automatico.
 * La pista 1 del club A ya tiene una reserva del jugador (10:00Z-11:30Z del 2030-01-01) creada por la base.
 */
class BloqueoTests extends BloqueoApiTestBase {

	private Reserva reservaDelJugador() {
		refrescar();
		return reservas.findAll().stream().filter(r -> r.getPista().getId().equals(pista.getId())).findFirst().orElseThrow();
	}

	private List<IncidenciaEvento> eventosDe(long incidencia, TipoEventoIncidencia tipo) {
		return historial(incidencia).stream().filter(e -> e.getTipo() == tipo).toList();
	}

	// ---- crear ----

	@Test
	void elAdministradorCreaUnBloqueoAbierto_201_desdeAhora_yVeLasReservasAfectadas() throws Exception {
		Reserva existente = reservaDelJugador();

		bloquear(admin, pista, cuerpo("Reparar el cristal", null, null, null))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.bloqueo.id").isNumber())
				.andExpect(jsonPath("$.bloqueo.pistaId").value(pista.getId()))
				.andExpect(jsonPath("$.bloqueo.pistaNombre").value("Pista 1"))
				.andExpect(jsonPath("$.bloqueo.incidenciaId").value(nullValue()))
				.andExpect(jsonPath("$.bloqueo.motivo").value("Reparar el cristal"))
				.andExpect(jsonPath("$.bloqueo.desde").value("2030-01-01T00:00:00Z"))
				.andExpect(jsonPath("$.bloqueo.hasta").value(nullValue()))
				.andExpect(jsonPath("$.bloqueo.creadoPor.nombre").value("Alba Admin"))
				.andExpect(jsonPath("$.bloqueo.creadoEn").value("2030-01-01T00:00:00Z"))
				.andExpect(jsonPath("$.bloqueo.levantadoEn").value(nullValue()))
				.andExpect(jsonPath("$.bloqueo.levantadoPor").value(nullValue()))
				.andExpect(jsonPath("$.bloqueo.activo").value(true))
				.andExpect(jsonPath("$.afectadas", hasSize(1)))
				.andExpect(jsonPath("$.afectadas[0].id").value(existente.getId()))
				.andExpect(jsonPath("$.afectadas[0].inicio").value("2030-01-01T10:00:00Z"))
				.andExpect(jsonPath("$.afectadas[0].fin").value("2030-01-01T11:30:00Z"))
				.andExpect(jsonPath("$.afectadas[0].organizador.id").value(jugador.id()))
				.andExpect(jsonPath("$.afectadas[0].organizador.nombre").value("Juan Jugador"))
				.andExpect(jsonPath("$.afectadas[0].organizador.email").value("jugador@vibora.com"))
				.andExpect(jsonPath("$.afectadas[0].jugadores", hasSize(1)))
				.andExpect(jsonPath("$.afectadas[0].jugadores[0].nombre").value("Juan Jugador"));

		assertThat(todosLosBloqueos()).hasSize(1);
	}

	@Test
	void conDesdeYHastaExplicitos_losRespeta_yLasReservasFueraDelRangoNoSonAfectadas() throws Exception {
		bloquear(admin, pista, "2030-01-02T08:00:00Z", "2030-01-02T20:00:00Z")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.bloqueo.desde").value("2030-01-02T08:00:00Z"))
				.andExpect(jsonPath("$.bloqueo.hasta").value("2030-01-02T20:00:00Z"))
				.andExpect(jsonPath("$.afectadas", hasSize(0)));
	}

	@Test
	void sinPermiso_403_yNoSeCreaNada() throws Exception {
		for (Persona quien : List.of(adminB, jugador, otro, mant1, monitor)) {
			bloquear(quien, pista, cuerpo("Reparar", null, null, null)).andExpect(status().isForbidden());
		}
		assertThat(todosLosBloqueos()).isEmpty();
	}

	@Test
	void sinToken_401() throws Exception {
		mvc.perform(post(rutaBloqueos(pista)).contentType(MediaType.APPLICATION_JSON).content(cuerpo("Reparar", null, null, null)))
				.andExpect(status().isUnauthorized());
		mvc.perform(get(rutaBloqueos(pista))).andExpect(status().isUnauthorized());
	}

	@Test
	void clubOPistaInexistentesOAjenos_404() throws Exception {
		crearEnRuta("/api/clubes/999999/pistas/" + pista.getId() + "/bloqueos").andExpect(status().isNotFound());
		crearEnRuta("/api/clubes/" + club.getId() + "/pistas/999999/bloqueos").andExpect(status().isNotFound());
		// una pista de otro club, pedida por el club del administrador
		crearEnRuta("/api/clubes/" + club.getId() + "/pistas/" + pistaB.getId() + "/bloqueos").andExpect(status().isNotFound());
		assertThat(todosLosBloqueos()).isEmpty();
	}

	private ResultActions crearEnRuta(String ruta) throws Exception {
		return mvc.perform(post(ruta).header("Authorization", admin.token()).contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo("Reparar", null, null, null)));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   " })
	void sinMotivo_400(String motivo) throws Exception {
		bloquear(admin, pista, cuerpo(motivo, null, null, null)).andExpect(status().isBadRequest());
		bloquear(admin, pista, "{}").andExpect(status().isBadRequest());
		assertThat(todosLosBloqueos()).isEmpty();
	}

	@Test
	void unMotivoDe300CaracteresCabe_elDe301No() throws Exception {
		bloquear(admin, pista, cuerpo("m".repeat(300), null, null, null)).andExpect(status().isCreated());
		bloquear(admin, pista, cuerpo("m".repeat(301), null, null, null)).andExpect(status().isBadRequest());
	}

	@Test
	void elFinDebeSerPosteriorAlInicio_yFuturo_400() throws Exception {
		bloquear(admin, pista, "2030-01-02T08:00:00Z", "2030-01-02T08:00:00Z").andExpect(status().isBadRequest());
		bloquear(admin, pista, "2030-01-02T08:00:00Z", "2030-01-02T07:59:59Z").andExpect(status().isBadRequest());
		// fin ya pasado (la hora actual de los tests es 2030-01-01T00:00Z)
		bloquear(admin, pista, "2029-12-30T00:00:00Z", "2029-12-31T00:00:00Z").andExpect(status().isBadRequest());
		bloquear(admin, pista, "2029-12-30T00:00:00Z", "2030-01-01T00:00:00Z").andExpect(status().isBadRequest());
		// fecha ilegible
		bloquear(admin, pista, cuerpo("Reparar", "ayer", null, null)).andExpect(status().isBadRequest());
		assertThat(todosLosBloqueos()).isEmpty();
	}

	@Test
	void unInicioEnElPasado_esValido_siElFinEsFuturo() throws Exception {
		bloquear(admin, pista, "2029-12-31T00:00:00Z", "2030-01-01T05:00:00Z")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.bloqueo.desde").value("2029-12-31T00:00:00Z"))
				.andExpect(jsonPath("$.bloqueo.activo").value(true));
	}

	// ---- reservas afectadas ----

	@Test
	void soloSonAfectadasLasConfirmadas_futuras_yQueSolapan_lasAdyacentesNo() throws Exception {
		Reserva alPrincipio = reservaEn(otro, pista, "2030-01-01T11:30:00Z"); // empieza justo en 'desde'
		Reserva enMedio = reservaEn(mant2, pista, "2030-01-01T13:00:00Z");
		Reserva alFinal = reservaEn(monitor, pista, "2030-01-01T14:30:00Z"); // acaba justo en 'hasta'
		reservaEn(otro, pista, "2030-01-01T16:00:00Z"); // empieza justo cuando acaba el bloqueo: no
		reservaEn(otro, pista, "2030-01-01T11:30:00Z", EstadoReserva.CANCELADA); // cancelada: no
		reservaEn(otro, pista2, "2030-01-01T13:00:00Z"); // otra pista: no
		// la del jugador (10:00Z-11:30Z) acaba justo en 'desde': no

		bloquear(admin, pista, "2030-01-01T11:30:00Z", "2030-01-01T16:00:00Z")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.afectadas", hasSize(3)))
				.andExpect(jsonPath("$.afectadas[0].id").value(alPrincipio.getId()))
				.andExpect(jsonPath("$.afectadas[1].id").value(enMedio.getId()))
				.andExpect(jsonPath("$.afectadas[2].id").value(alFinal.getId()))
				.andExpect(jsonPath("$.afectadas[0].inicio").value("2030-01-01T11:30:00Z"))
				.andExpect(jsonPath("$.afectadas[1].inicio").value("2030-01-01T13:00:00Z"))
				.andExpect(jsonPath("$.afectadas[2].inicio").value("2030-01-01T14:30:00Z"));
	}

	@Test
	void lasReservasQueYaHanEmpezado_noSonAfectadas() throws Exception {
		reservaEn(otro, pista, "2030-01-01T11:30:00Z");
		reservaEn(mant2, pista, "2030-01-01T13:00:00Z");
		reloj.fijar(z("2030-01-01T12:00:00Z")); // la de las 11:30 ya ha empezado (y la del jugador, de las 10:00, tambien)

		bloquear(admin, pista, "2030-01-01T09:00:00Z", "2030-01-01T20:00:00Z")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.afectadas", hasSize(1)))
				.andExpect(jsonPath("$.afectadas[0].inicio").value("2030-01-01T13:00:00Z"));
	}

	@Test
	void unBloqueoAbierto_afectaATodasLasReservasFuturasDeLaPista() throws Exception {
		reservaEn(otro, pista, "2030-01-05T11:30:00Z");
		reservaEn(mant2, pista, "2030-02-01T13:00:00Z");
		reservaEn(otro, pista2, "2030-01-05T11:30:00Z");

		bloquear(admin, pista, "2030-01-03T00:00:00Z", null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.afectadas", hasSize(2)))
				.andExpect(jsonPath("$.afectadas[0].inicio").value("2030-01-05T11:30:00Z"))
				.andExpect(jsonPath("$.afectadas[1].inicio").value("2030-02-01T13:00:00Z"));
	}

	@Test
	void laListaDeAfectadasIncluyeALosJugadores_yElEndpointDeAfectadasLaRepite() throws Exception {
		Reserva r = reservaEn(otro, pista, "2030-01-01T13:00:00Z");
		anadirJugador(r, mant2, 2);
		anadirJugador(r, monitor, 3);
		long bloqueo = bloqueoCreado(admin, pista, "2030-01-01T12:00:00Z", "2030-01-01T15:00:00Z");

		afectadas(admin, pista, bloqueo).andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(r.getId()))
				.andExpect(jsonPath("$[0].organizador.nombre").value("Olga Otra"))
				.andExpect(jsonPath("$[0].jugadores", hasSize(3)))
				.andExpect(jsonPath("$[0].jugadores[0].nombre").value("Olga Otra"))
				.andExpect(jsonPath("$[0].jugadores[1].nombre").value("Marta Arregla"))
				.andExpect(jsonPath("$[0].jugadores[2].nombre").value("Moni Tor"));
	}

	// ---- incidencia vinculada ----

	@Test
	void vinculadoAUnaIncidencia_guardaElVinculo_yDejaEventoBloqueoCreado() throws Exception {
		long incidencia = incidenciaDe(jugador);

		bloquear(admin, pista, cuerpo("Cristal roto", null, null, incidencia))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.bloqueo.incidenciaId").value(incidencia));

		List<IncidenciaEvento> creados = eventosDe(incidencia, TipoEventoIncidencia.BLOQUEO_CREADO);
		assertThat(creados).hasSize(1);
		assertThat(creados.get(0).getUsuario().getId()).isEqualTo(admin.id());
		assertThat(creados.get(0).getTexto()).contains("Pista 1").contains("Cristal roto");
		detalle(admin, incidencia).andExpect(jsonPath("$.bloqueoActivo.motivo").value("Cristal roto"))
				.andExpect(jsonPath("$.bloqueoActivo.desde").value("2030-01-01T00:00:00Z"))
				.andExpect(jsonPath("$.bloqueoActivo.hasta").value(nullValue()))
				.andExpect(jsonPath("$.bloqueoActivo.id").isNumber());
	}

	@Test
	void unaIncidenciaSinPista_puedeBloquearCualquierPistaDelClub() throws Exception {
		long sinPista = idDe(reportar(jugador, club, "General", "OTRA", null).andExpect(status().isCreated()));

		bloquear(admin, pista2, cuerpo("Revision general", null, null, sinPista)).andExpect(status().isCreated());
	}

	@Test
	void unaIncidenciaDeOtraPista_400() throws Exception {
		long incidencia = incidenciaDe(jugador); // es de la pista 1

		bloquear(admin, pista2, cuerpo("Cristal", null, null, incidencia)).andExpect(status().isBadRequest());

		assertThat(todosLosBloqueos()).isEmpty();
	}

	@Test
	void unaIncidenciaDeOtroClubOInexistente_404() throws Exception {
		long deOtroClub = idDe(reportar(adminB, clubB, "Ajena", "RED", pistaB.getId()).andExpect(status().isCreated()));

		bloquear(admin, pista, cuerpo("Cristal", null, null, deOtroClub))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Incidencia no encontrada"));
		bloquear(admin, pista, cuerpo("Cristal", null, null, 999_999L)).andExpect(status().isNotFound());

		assertThat(todosLosBloqueos()).isEmpty();
	}

	@Test
	void unSegundoBloqueoActivoParaLaMismaIncidencia_409_peroTrasLevantarElPrimeroSePuede() throws Exception {
		long incidencia = incidenciaDe(jugador);
		long primero = idDe(bloquear(admin, pista, cuerpo("Cristal", null, null, incidencia)).andExpect(status().isCreated()));

		bloquear(admin, pista, cuerpo("Otra vez", null, null, incidencia))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("La incidencia ya tiene un bloqueo activo"));
		assertThat(todosLosBloqueos()).hasSize(1);

		levantar(admin, pista, primero).andExpect(status().isNoContent());
		bloquear(admin, pista, cuerpo("Otra vez", null, null, incidencia)).andExpect(status().isCreated());
		assertThat(todosLosBloqueos()).hasSize(2);
	}

	@Test
	void unBloqueoCaducadoSinLevantar_noImpideCrearOtro_yQuedaCerradoEnSuFecha() throws Exception {
		long incidencia = incidenciaDe(jugador);
		long viejo = idDe(bloquear(admin, pista, cuerpoConIncidencia("2030-01-01T01:00:00Z", "2030-01-01T05:00:00Z", incidencia))
				.andExpect(status().isCreated()));
		reloj.fijar(z("2030-01-01T06:00:00Z"));

		bloquear(admin, pista, cuerpo("Sigue roto", null, null, incidencia)).andExpect(status().isCreated());

		BloqueoPista cerrado = bloqueoPorId(viejo);
		assertThat(cerrado.getLevantadoEn()).isEqualTo(z("2030-01-01T05:00:00Z"));
		assertThat(cerrado.getLevantadoPor()).isNull();
		assertThat(todosLosBloqueos().stream().filter(b -> b.getLevantadoEn() == null)).hasSize(1);
	}

	private static String cuerpoConIncidencia(String desde, String hasta, long incidencia) {
		return cuerpo("Cristal", desde, hasta, incidencia);
	}

	@ParameterizedTest
	@EnumSource(value = EstadoIncidencia.class, names = { "CERRADA", "RECHAZADA" })
	void conUnaIncidenciaTerminal_409(EstadoIncidencia terminal) throws Exception {
		long incidencia = incidenciaEn(terminal);

		bloquear(admin, pista, cuerpo("Cristal", null, null, incidencia)).andExpect(status().isConflict());

		assertThat(todosLosBloqueos()).isEmpty();
	}

	@Test
	void conUnaIncidenciaResuelta_seAdmite_elBloqueoSeLevantaraAlCerrarla() throws Exception {
		long incidencia = incidenciaEn(EstadoIncidencia.RESUELTA);

		bloquear(admin, pista, cuerpo("Pendiente de inspeccion", null, null, incidencia)).andExpect(status().isCreated());

		estado(jugador, incidencia, "CERRADA", null).andExpect(status().isOk());
		BloqueoPista b = todosLosBloqueos().get(0);
		assertThat(b.getLevantadoEn()).isEqualTo(z("2030-01-01T00:00:00Z"));
		assertThat(b.getLevantadoPor().getId()).isEqualTo(jugador.id());
		assertThat(eventosDe(incidencia, TipoEventoIncidencia.BLOQUEO_LEVANTADO)).hasSize(1);
	}

	// ---- listar ----

	@Test
	void listar_laVeElAdministradorYElPersonalDeMantenimientoDelClub_elRestoNo() throws Exception {
		bloqueoCreado(admin, pista, null, null);

		for (Persona quien : List.of(admin, mant1, mant2)) {
			listarBloqueos(quien, pista, null).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
					.andExpect(jsonPath("$[0].motivo").value("Reparar el cristal"));
		}
		for (Persona quien : List.of(jugador, otro, monitor, adminB, mantB)) {
			listarBloqueos(quien, pista, null).andExpect(status().isForbidden());
		}
	}

	@Test
	void listar_conElFiltroDeActivos() throws Exception {
		long caducado = bloqueoCreado(admin, pista, "2030-01-01T00:00:00Z", "2030-01-01T05:00:00Z");
		long levantado = bloqueoCreado(admin, pista, "2030-01-02T00:00:00Z", null);
		levantar(admin, pista, levantado).andExpect(status().isNoContent());
		long activo = bloqueoCreado(admin, pista, "2030-01-03T00:00:00Z", null);
		bloqueoCreado(admin, pista2, null, null); // otra pista: nunca sale
		reloj.fijar(z("2030-01-01T06:00:00Z"));

		listarBloqueos(admin, pista, "true").andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(activo))
				.andExpect(jsonPath("$[0].activo").value(true));
		listarBloqueos(admin, pista, "false").andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].id").value(levantado)).andExpect(jsonPath("$[0].activo").value(false))
				.andExpect(jsonPath("$[0].levantadoPor.nombre").value("Alba Admin"))
				.andExpect(jsonPath("$[1].id").value(caducado)).andExpect(jsonPath("$[1].activo").value(false));
		listarBloqueos(admin, pista, null).andExpect(jsonPath("$", hasSize(3)))
				.andExpect(jsonPath("$[0].id").value(activo)) // el mas reciente (por inicio) primero
				.andExpect(jsonPath("$[1].id").value(levantado)).andExpect(jsonPath("$[2].id").value(caducado));
		listarBloqueos(admin, pista, "quizas").andExpect(status().isBadRequest());
	}

	// ---- afectadas ----

	@Test
	void afectadas_soloElAdministrador() throws Exception {
		long bloqueo = bloqueoCreado(admin, pista, null, null);

		for (Persona quien : List.of(mant1, jugador, adminB, monitor)) {
			afectadas(quien, pista, bloqueo).andExpect(status().isForbidden());
		}
		afectadas(admin, pista, bloqueo).andExpect(status().isOk());
		afectadas(admin, pista, 999_999L).andExpect(status().isNotFound());
		afectadas(admin, pista2, bloqueo).andExpect(status().isNotFound()); // el bloqueo es de la pista 1
	}

	@Test
	void afectadas_deUnBloqueoLevantado_estaVacia() throws Exception {
		long bloqueo = bloqueoCreado(admin, pista, null, null);
		afectadas(admin, pista, bloqueo).andExpect(jsonPath("$", hasSize(1)));
		levantar(admin, pista, bloqueo).andExpect(status().isNoContent());

		afectadas(admin, pista, bloqueo).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
	}

	// ---- levantar ----

	@Test
	void levantar_204_guardaCuandoYQuien_esIdempotente_yDejaUnSoloEvento() throws Exception {
		long incidencia = incidenciaDe(jugador);
		long bloqueo = idDe(bloquear(admin, pista, cuerpo("Cristal", null, null, incidencia)).andExpect(status().isCreated()));
		reloj.fijar(z("2030-01-01T03:00:00Z"));

		levantar(admin, pista, bloqueo).andExpect(status().isNoContent());
		reloj.fijar(z("2030-01-01T04:00:00Z"));
		levantar(admin, pista, bloqueo).andExpect(status().isNoContent());

		BloqueoPista b = bloqueoPorId(bloqueo);
		assertThat(b.getLevantadoEn()).as("la segunda llamada no lo cambia").isEqualTo(z("2030-01-01T03:00:00Z"));
		assertThat(b.getLevantadoPor().getId()).isEqualTo(admin.id());
		List<IncidenciaEvento> eventos = eventosDe(incidencia, TipoEventoIncidencia.BLOQUEO_LEVANTADO);
		assertThat(eventos).hasSize(1);
		assertThat(eventos.get(0).getUsuario().getId()).isEqualTo(admin.id());
		detalle(admin, incidencia).andExpect(jsonPath("$.bloqueoActivo").value(nullValue()));
	}

	@Test
	void levantar_sinPermiso_403_yRecursosAjenos_404() throws Exception {
		long bloqueo = bloqueoCreado(admin, pista, null, null);

		for (Persona quien : List.of(mant1, jugador, adminB, monitor, otro)) {
			levantar(quien, pista, bloqueo).andExpect(status().isForbidden());
		}
		levantar(admin, pista, 999_999L).andExpect(status().isNotFound());
		levantar(admin, pista2, bloqueo).andExpect(status().isNotFound());
		assertThat(bloqueoPorId(bloqueo).getLevantadoEn()).isNull();
	}

	@Test
	void levantar_unBloqueoSinIncidencia_noDejaEventosEnNingunaIncidencia() throws Exception {
		long incidencia = incidenciaDe(jugador);
		long bloqueo = bloqueoCreado(admin, pista, null, null);

		levantar(admin, pista, bloqueo).andExpect(status().isNoContent());

		assertThat(eventosDe(incidencia, TipoEventoIncidencia.BLOQUEO_LEVANTADO)).isEmpty();
	}

	// ---- atajo desde la incidencia ----

	@Test
	void elAtajoBloqueaLaPistaDeLaIncidencia_conSuTituloComoMotivo_abiertoDesdeAhora() throws Exception {
		long incidencia = incidenciaDe(jugador, "Cristal trasero roto");
		Reserva existente = reservaDelJugador();

		bloquearDesdeIncidencia(admin, incidencia).andExpect(status().isCreated())
				.andExpect(jsonPath("$.bloqueo.pistaId").value(pista.getId()))
				.andExpect(jsonPath("$.bloqueo.incidenciaId").value(incidencia))
				.andExpect(jsonPath("$.bloqueo.motivo").value("Cristal trasero roto"))
				.andExpect(jsonPath("$.bloqueo.desde").value("2030-01-01T00:00:00Z"))
				.andExpect(jsonPath("$.bloqueo.hasta").value(nullValue()))
				.andExpect(jsonPath("$.afectadas", hasSize(1)))
				.andExpect(jsonPath("$.afectadas[0].id").value(existente.getId()));

		assertThat(eventosDe(incidencia, TipoEventoIncidencia.BLOQUEO_CREADO)).hasSize(1);
		detalle(jugador, incidencia).andExpect(jsonPath("$.bloqueoActivo.motivo").value("Cristal trasero roto"));
	}

	@Test
	void elAtajo_sinPistaEnLaIncidencia_409() throws Exception {
		long sinPista = idDe(reportar(jugador, club, "General", "OTRA", null).andExpect(status().isCreated()));

		bloquearDesdeIncidencia(admin, sinPista).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("La incidencia no tiene una pista asociada"));
	}

	@Test
	void elAtajo_permisos() throws Exception {
		long incidencia = incidenciaDe(jugador);
		asignar(admin, incidencia, mant1.id(), null).andExpect(status().isOk());

		bloquearDesdeIncidencia(jugador, incidencia).andExpect(status().isForbidden()); // la ve, pero no es admin
		bloquearDesdeIncidencia(mant1, incidencia).andExpect(status().isForbidden());
		for (Persona intruso : List.of(otro, mant2, monitor, adminB)) {
			bloquearDesdeIncidencia(intruso, incidencia).andExpect(status().isNotFound()); // no la ven
		}
		bloquearDesdeIncidencia(admin, 999_999L).andExpect(status().isNotFound());
		assertThat(todosLosBloqueos()).isEmpty();
	}

	@Test
	void elAtajo_dosVeces_409_yEnEstadoTerminal_409() throws Exception {
		long incidencia = incidenciaDe(jugador);
		bloquearDesdeIncidencia(admin, incidencia).andExpect(status().isCreated());

		bloquearDesdeIncidencia(admin, incidencia).andExpect(status().isConflict());

		long rechazada = incidenciaEn(EstadoIncidencia.RECHAZADA);
		bloquearDesdeIncidencia(admin, rechazada).andExpect(status().isConflict());
		assertThat(todosLosBloqueos()).hasSize(1);
	}

	// ---- levantamiento automatico ----

	@Test
	void alResolverLaIncidencia_seLevantaElBloqueo_conEventoYActorDelCambio() throws Exception {
		long incidencia = incidenciaDe(jugador);
		asignar(admin, incidencia, mant1.id(), null).andExpect(status().isOk());
		estado(mant1, incidencia, "EN_CURSO", null).andExpect(status().isOk());
		bloquearDesdeIncidencia(admin, incidencia).andExpect(status().isCreated());
		reloj.fijar(z("2030-01-01T02:00:00Z"));

		estado(mant1, incidencia, "RESUELTA", null).andExpect(status().isOk())
				.andExpect(jsonPath("$.bloqueoActivo").value(nullValue()));

		BloqueoPista b = todosLosBloqueos().get(0);
		assertThat(b.getLevantadoEn()).isEqualTo(z("2030-01-01T02:00:00Z"));
		assertThat(b.getLevantadoPor().getId()).isEqualTo(mant1.id());
		List<IncidenciaEvento> h = historial(incidencia);
		assertThat(h).extracting(IncidenciaEvento::getTipo).endsWith(TipoEventoIncidencia.ESTADO,
				TipoEventoIncidencia.BLOQUEO_LEVANTADO);
		assertThat(h.get(h.size() - 1).getUsuario().getId()).isEqualTo(mant1.id());
		assertThat(h.get(h.size() - 1).getTexto()).contains("Resuelta");
	}

	@Test
	void alRechazarLaIncidencia_seLevantaElBloqueo() throws Exception {
		long incidencia = incidenciaDe(jugador);
		bloquearDesdeIncidencia(admin, incidencia).andExpect(status().isCreated());

		estado(admin, incidencia, "RECHAZADA", "No procede").andExpect(status().isOk());

		BloqueoPista b = todosLosBloqueos().get(0);
		assertThat(b.getLevantadoEn()).isNotNull();
		assertThat(b.getLevantadoPor().getId()).isEqualTo(admin.id());
		assertThat(eventosDe(incidencia, TipoEventoIncidencia.BLOQUEO_LEVANTADO)).hasSize(1);
	}

	@Test
	void reabrirLaIncidencia_noRecreaElBloqueo() throws Exception {
		long incidencia = incidenciaDe(jugador);
		asignar(admin, incidencia, mant1.id(), null).andExpect(status().isOk());
		estado(mant1, incidencia, "EN_CURSO", null).andExpect(status().isOk());
		bloquearDesdeIncidencia(admin, incidencia).andExpect(status().isCreated());
		estado(mant1, incidencia, "RESUELTA", null).andExpect(status().isOk());

		estado(admin, incidencia, "EN_CURSO", "Sigue mal").andExpect(status().isOk())
				.andExpect(jsonPath("$.bloqueoActivo").value(nullValue()));

		assertThat(todosLosBloqueos()).hasSize(1);
		assertThat(todosLosBloqueos().get(0).getLevantadoEn()).isNotNull();
		assertThat(eventosDe(incidencia, TipoEventoIncidencia.BLOQUEO_CREADO)).hasSize(1);
	}

	@Test
	void lasDemasTransiciones_noTocanElBloqueo() throws Exception {
		long incidencia = incidenciaDe(jugador);
		bloquearDesdeIncidencia(admin, incidencia).andExpect(status().isCreated());

		asignar(admin, incidencia, mant1.id(), null).andExpect(status().isOk());
		estado(mant1, incidencia, "EN_CURSO", null).andExpect(status().isOk());
		comentar(jugador, incidencia, "Hola").andExpect(status().isCreated());

		assertThat(todosLosBloqueos().get(0).getLevantadoEn()).isNull();
		detalle(admin, incidencia).andExpect(jsonPath("$.bloqueoActivo.id").isNumber());
	}

	@Test
	void resolverUnaIncidencia_noTocaBloqueosDeOtrasIncidenciasNiLosManuales() throws Exception {
		long a = incidenciaDe(jugador, "Una");
		long b = incidenciaDe(jugador, "Otra");
		bloquearDesdeIncidencia(admin, b).andExpect(status().isCreated());
		long manual = bloqueoCreado(admin, pista2, null, null);
		asignar(admin, a, mant1.id(), null).andExpect(status().isOk());
		estado(mant1, a, "EN_CURSO", null).andExpect(status().isOk());

		estado(mant1, a, "RESUELTA", null).andExpect(status().isOk());

		assertThat(todosLosBloqueos()).allMatch(x -> x.getLevantadoEn() == null);
		assertThat(bloqueoPorId(manual).getLevantadoEn()).isNull();
	}

	@Test
	void unBloqueoYaLevantadoAMano_noGeneraOtroEventoAlResolver() throws Exception {
		long incidencia = incidenciaDe(jugador);
		asignar(admin, incidencia, mant1.id(), null).andExpect(status().isOk());
		estado(mant1, incidencia, "EN_CURSO", null).andExpect(status().isOk());
		long bloqueo = idDe(bloquearDesdeIncidencia(admin, incidencia).andExpect(status().isCreated()));
		levantar(admin, pista, bloqueo).andExpect(status().isNoContent());

		estado(mant1, incidencia, "RESUELTA", null).andExpect(status().isOk());

		assertThat(eventosDe(incidencia, TipoEventoIncidencia.BLOQUEO_LEVANTADO)).hasSize(1);
	}

	@Test
	void elDetalleDejaDeMostrarElBloqueoCuandoCaduca() throws Exception {
		long incidencia = incidenciaDe(jugador);
		bloquear(admin, pista, cuerpoConIncidencia("2030-01-01T00:00:00Z", "2030-01-01T05:00:00Z", incidencia))
				.andExpect(status().isCreated());
		detalle(admin, incidencia).andExpect(jsonPath("$.bloqueoActivo.hasta").value("2030-01-01T05:00:00Z"));

		reloj.fijar(z("2030-01-01T05:00:00Z"));

		detalle(admin, incidencia).andExpect(jsonPath("$.bloqueoActivo").value(nullValue()));
	}
}
