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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reservas en los dias del cambio de hora en Europe/Madrid. El cambio ocurre de madrugada (fuera del horario del
 * club, 09:00-21:00), asi que las horas LOCALES de las casillas no se mueven, pero su instante UTC si: el sabado y el
 * domingo del cambio difieren en una hora de offset. Los instantes esperados se calculan con offsets FIJOS (no con la
 * zona), de forma independiente del codigo bajo prueba.
 * <p>
 * Rejilla de 90 min desde las 09:00: 09:00, 10:30, 12:00, 13:30, 15:00, 16:30, 18:00, 19:30 (8 casillas).
 * Tarifa: 10,00/h de 09:00 a 14:00 y 20,00/h de 14:00 a 21:00 => 15, 15, 15, 25 (cruza las 14:00), 30, 30, 30, 30.
 */
@Import(RelojTestConfig.class)
class ReservaCambioHorarioTests extends ApiTestBase {

	private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
	private static final int[][] CASILLAS = { { 9, 0 }, { 10, 30 }, { 12, 0 }, { 13, 30 }, { 15, 0 }, { 16, 30 }, { 18, 0 },
			{ 19, 30 } };
	private static final double[] PRECIOS = { 15.0, 15.0, 15.0, 25.0, 30.0, 30.0, 30.0, 30.0 };

	/** Offset UTC (horas) del sabado y del domingo del cambio, y un "ahora" anterior al sabado. */
	enum Cambio {
		OTONO(LocalDate.of(2026, 10, 24), 2, 1, Instant.parse("2026-10-01T00:00:00Z")),
		PRIMAVERA(LocalDate.of(2027, 3, 27), 1, 2, Instant.parse("2027-03-01T00:00:00Z"));

		final LocalDate sabado;
		final int offsetSabado;
		final int offsetDomingo;
		final Instant ahora;

		Cambio(LocalDate sabado, int offsetSabado, int offsetDomingo, Instant ahora) {
			this.sabado = sabado;
			this.offsetSabado = offsetSabado;
			this.offsetDomingo = offsetDomingo;
			this.ahora = ahora;
		}

		LocalDate domingo() {
			return sabado.plusDays(1);
		}
	}

	@Autowired RelojMutable reloj;
	@Autowired PistaRepository pistas;
	@Autowired TarifaFranjaRepository tarifas;
	@Autowired ReservaRepository reservas;

	Club club;
	Pista pista;
	String token;

	@BeforeEach
	void preparar() throws Exception {
		reloj.fijar(RelojTestConfig.AHORA_INICIAL);
		club = crearClub("Club DST");
		club.setHoraApertura(LocalTime.of(9, 0));
		club.setHoraCierre(LocalTime.of(21, 0));
		club = clubs.saveAndFlush(club);
		pista = pistas.saveAndFlush(TestData.pista(club, "Pista 1"));
		for (DayOfWeek dia : new DayOfWeek[] { DayOfWeek.SATURDAY, DayOfWeek.SUNDAY }) {
			franja(dia, 9, 14, "10.00");
			franja(dia, 14, 21, "20.00");
		}
		// El JWT se firma con el reloj de test (2030) pero el decodificador valida con la hora REAL: el token se
		// obtiene antes de llevar el reloj de la aplicacion a 2026/2027, y sigue siendo valido.
		token = bearerJugador("jugador-dst@vibora.com");
	}

	private void franja(DayOfWeek dia, int desde, int hasta, String precioHora) {
		TarifaFranja t = TestData.tarifa(club, dia, TipoTarifa.NORMAL);
		t.setHoraInicio(LocalTime.of(desde, 0));
		t.setHoraFin(LocalTime.of(hasta, 0));
		t.setPrecioHora(new BigDecimal(precioHora));
		tarifas.saveAndFlush(t);
	}

	/** Instante de una hora local con un offset fijo (independiente de la zona: es la verdad esperada). */
	private static Instant instante(LocalDate dia, int hora, int minuto, int offsetHoras) {
		return dia.atTime(hora, minuto).toInstant(ZoneOffset.ofHours(offsetHoras));
	}

	private ResultActions agenda(LocalDate dia) throws Exception {
		return mvc.perform(get("/api/clubes/" + club.getId() + "/pistas/" + pista.getId() + "/agenda")
				.param("fecha", dia.toString()).header("Authorization", token));
	}

	private ResultActions reservar(Instant inicio) throws Exception {
		return mvc.perform(post("/api/clubes/" + club.getId() + "/pistas/" + pista.getId() + "/reservas")
				.header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"inicio\":\"" + inicio + "\"}"));
	}

	@ParameterizedTest
	@EnumSource(Cambio.class)
	void agendaDelDomingoDelCambio_tieneOchoCasillasEnLasHorasLocalesYConElOffsetNuevo(Cambio c) throws Exception {
		reloj.fijar(c.ahora);

		ResultActions agenda = agenda(c.domingo()).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(8));

		for (int i = 0; i < CASILLAS.length; i++) {
			Instant inicio = instante(c.domingo(), CASILLAS[i][0], CASILLAS[i][1], c.offsetDomingo);
			agenda.andExpect(jsonPath("$[" + i + "].inicio").value(inicio.toString()))
					.andExpect(jsonPath("$[" + i + "].fin").value(inicio.plusSeconds(90 * 60).toString()))
					.andExpect(jsonPath("$[" + i + "].disponible").value(true))
					.andExpect(jsonPath("$[" + i + "].precio").value(PRECIOS[i]));
			// y la zona confirma que son las horas locales de la rejilla
			assertThat(inicio.atZone(MADRID).toLocalTime()).isEqualTo(LocalTime.of(CASILLAS[i][0], CASILLAS[i][1]));
		}
	}

	@ParameterizedTest
	@EnumSource(Cambio.class)
	void agendaDelSabadoPrevio_usaElOffsetAnterior_ySuCountEsElMismo(Cambio c) throws Exception {
		reloj.fijar(c.ahora);

		ResultActions agenda = agenda(c.sabado).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(8));

		for (int i = 0; i < CASILLAS.length; i++) {
			agenda.andExpect(jsonPath("$[" + i + "].inicio")
					.value(instante(c.sabado, CASILLAS[i][0], CASILLAS[i][1], c.offsetSabado).toString()))
					.andExpect(jsonPath("$[" + i + "].precio").value(PRECIOS[i]));
		}
	}

	@ParameterizedTest
	@EnumSource(Cambio.class)
	void reservarEnElDomingoDelCambio_guardaElInstanteCorrecto_yCalculaElPrecioConHorasLocales(Cambio c) throws Exception {
		reloj.fijar(c.ahora);

		// 13:30-15:00: cruza las 14:00 de la tarifa => 0,5 h * 10 + 1 h * 20 = 25,00
		Instant inicio = instante(c.domingo(), 13, 30, c.offsetDomingo);
		reservar(inicio).andExpect(status().isCreated())
				.andExpect(jsonPath("$.inicio").value(inicio.toString()))
				.andExpect(jsonPath("$.fin").value(instante(c.domingo(), 15, 0, c.offsetDomingo).toString()))
				.andExpect(jsonPath("$.precioTotal").value(25.0));

		assertThat(reservas.findAll()).singleElement().satisfies(r -> {
			assertThat(r.getInicio()).isEqualTo(inicio);
			assertThat(r.getFin()).isEqualTo(instante(c.domingo(), 15, 0, c.offsetDomingo));
			assertThat(r.getPrecioTotal()).isEqualByComparingTo("25.00");
		});
		// y la casilla reservada aparece ocupada en esa agenda (solo esa)
		agenda(c.domingo()).andExpect(jsonPath("$[3].disponible").value(false))
				.andExpect(jsonPath("$[2].disponible").value(true)).andExpect(jsonPath("$[4].disponible").value(true));
	}

	@ParameterizedTest
	@EnumSource(Cambio.class)
	void primeraCasillaDelDomingo_yUltimaDelSabado_sonReservablesYNoSeMezclan(Cambio c) throws Exception {
		reloj.fijar(c.ahora);
		Instant ultimaDelSabado = instante(c.sabado, 19, 30, c.offsetSabado);
		Instant primeraDelDomingo = instante(c.domingo(), 9, 0, c.offsetDomingo);

		reservar(ultimaDelSabado).andExpect(status().isCreated())
				.andExpect(jsonPath("$.fin").value(instante(c.sabado, 21, 0, c.offsetSabado).toString()))
				.andExpect(jsonPath("$.precioTotal").value(30.0));
		reservar(primeraDelDomingo).andExpect(status().isCreated())
				.andExpect(jsonPath("$.precioTotal").value(15.0));

		// cada reserva solo ocupa su casilla en su propio dia
		agenda(c.sabado).andExpect(jsonPath("$[7].disponible").value(false)).andExpect(jsonPath("$[0].disponible").value(true));
		agenda(c.domingo()).andExpect(jsonPath("$[0].disponible").value(false)).andExpect(jsonPath("$[7].disponible").value(true));
		List<Reserva> guardadas = reservas.findAll();
		assertThat(guardadas).extracting(Reserva::getInicio).containsExactlyInAnyOrder(ultimaDelSabado, primeraDelDomingo);
	}

	@ParameterizedTest
	@EnumSource(Cambio.class)
	void fueraDeRejilla_sigueDetectandose_tambienConElOffsetEquivocado(Cambio c) throws Exception {
		reloj.fijar(c.ahora);

		// 09:30 local no es casilla (la rejilla es 09:00, 10:30...)
		reservar(instante(c.domingo(), 9, 30, c.offsetDomingo)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").isString());
		// un cliente con el offset del dia anterior (hora de verano/invierno de ayer): 09:00 "de ayer" cae en otra hora local
		reservar(instante(c.domingo(), 9, 0, c.offsetSabado)).andExpect(status().isBadRequest());
		// justo antes de abrir y a la hora de cierre (la casilla acabaria despues de las 21:00)
		reservar(instante(c.domingo(), 7, 30, c.offsetDomingo)).andExpect(status().isBadRequest());
		reservar(instante(c.domingo(), 21, 0, c.offsetDomingo)).andExpect(status().isBadRequest());

		assertThat(reservas.count()).isZero();
	}
}
