package com.vibora.metricas;

import com.vibora.TestData;
import com.vibora.club.Club;
import com.vibora.metricas.CalculadoraRanuras.Ranura;
import com.vibora.metricas.CalculadoraRanuras.Tramo;
import com.vibora.reserva.ParrillaHoraria;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Ranuras ofrecidas y ocupadas: misma rejilla que las reservas, menos lo que tapan los bloqueos (pura, sin Spring). */
class CalculadoraRanurasTests {

	private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
	private static final Duration NOVENTA = Duration.ofMinutes(90);
	private static final LocalDate DIA = LocalDate.of(2030, 1, 7);
	private static final List<String> HORAS_08_A_23 = List.of("08:00", "09:30", "11:00", "12:30", "14:00", "15:30", "17:00",
			"18:30", "20:00", "21:30");

	private static Club club(int aperturaHora, int cierreHora) {
		Club c = TestData.club();
		c.setHoraApertura(LocalTime.of(aperturaHora, 0));
		c.setHoraCierre(LocalTime.of(cierreHora, 0));
		return c;
	}

	private static Instant local(LocalDate fecha, String hora) {
		return fecha.atTime(LocalTime.parse(hora)).atZone(MADRID).toInstant();
	}

	private static Instant local(String hora) {
		return local(DIA, hora);
	}

	private static List<Ranura> ranuras(Club c, LocalDate desde, LocalDate hasta, List<Tramo> bloqueos, Set<Instant> confirmadas) {
		return CalculadoraRanuras.ranuras(c, new RangoFechas(desde, hasta), NOVENTA, MADRID, bloqueos, confirmadas);
	}

	private static List<Ranura> unDia(List<Tramo> bloqueos, Set<Instant> confirmadas) {
		return ranuras(club(8, 23), DIA, DIA, bloqueos, confirmadas);
	}

	private static List<Instant> inicios(List<Ranura> ranuras) {
		return ranuras.stream().map(Ranura::inicio).toList();
	}

	private static List<Instant> esperadas(String... horas) {
		List<Instant> r = new ArrayList<>();
		for (String h : horas) {
			r.add(local(h));
		}
		return r;
	}

	// ---- la rejilla ----

	@Test
	void sinBloqueosLasRanurasSonLasDeLaParrillaDeReservas() {
		assertThat(inicios(unDia(List.of(), Set.of()))).isEqualTo(esperadas(HORAS_08_A_23.toArray(String[]::new)))
				.isEqualTo(ParrillaHoraria.inicios(club(8, 23), DIA, NOVENTA, MADRID));
	}

	@Test
	void variosDiasSumanLasRanurasDeCadaUnoEnOrden() {
		LocalDate hasta = DIA.plusDays(2);
		List<Instant> esperado = new ArrayList<>();
		for (LocalDate d = DIA; !d.isAfter(hasta); d = d.plusDays(1)) {
			esperado.addAll(ParrillaHoraria.inicios(club(8, 23), d, NOVENTA, MADRID));
		}

		assertThat(inicios(ranuras(club(8, 23), DIA, hasta, List.of(), Set.of()))).hasSize(30).isEqualTo(esperado);
	}

	@Test
	void unaRejillaQueNoCabeExactaNoOfreceLaUltimaCasillaTruncada() {
		// 08:00-22:00 con casillas de 90 min: la de las 21:30 acabaria a las 23:00, fuera de horario
		List<Ranura> r = ranuras(club(8, 22), DIA, DIA, List.of(), Set.of());

		assertThat(inicios(r)).isEqualTo(esperadas("08:00", "09:30", "11:00", "12:30", "14:00", "15:30", "17:00", "18:30", "20:00"));
	}

	@Test
	void losDomingosDelCambioDeHoraTienenLasMismasCasillasLocales() {
		for (LocalDate domingo : List.of(LocalDate.of(2026, 10, 25), LocalDate.of(2027, 3, 28))) {
			List<Ranura> r = ranuras(club(8, 23), domingo, domingo, List.of(), Set.of());

			assertThat(inicios(r)).hasSize(10).isEqualTo(ParrillaHoraria.inicios(club(8, 23), domingo, NOVENTA, MADRID));
			assertThat(r.stream().map(x -> x.inicio().atZone(MADRID).toLocalTime().toString()).toList())
					.isEqualTo(HORAS_08_A_23);
		}
		// 08:00 local: 07:00Z en otono... el domingo 25-oct ya es horario de invierno (UTC+1)
		assertThat(ranuras(club(8, 23), LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 25), List.of(), Set.of()).get(0).inicio())
				.isEqualTo(Instant.parse("2026-10-25T07:00:00Z"));
		assertThat(ranuras(club(8, 23), LocalDate.of(2027, 3, 28), LocalDate.of(2027, 3, 28), List.of(), Set.of()).get(0).inicio())
				.isEqualTo(Instant.parse("2027-03-28T06:00:00Z"));
	}

	// ---- el rango efectivo de un bloqueo ----

	@Test
	void rangoEfectivoEsElMenorEntreHastaYLevantadoEn() {
		Instant desde = local("10:00");
		Instant hasta = local("14:00");
		Instant levantado = local("12:00");

		assertThat(Tramo.efectivo(desde, null, null)).isEqualTo(new Tramo(desde, null));
		assertThat(Tramo.efectivo(desde, hasta, null)).isEqualTo(new Tramo(desde, hasta));
		assertThat(Tramo.efectivo(desde, null, levantado)).isEqualTo(new Tramo(desde, levantado));
		assertThat(Tramo.efectivo(desde, hasta, levantado)).isEqualTo(new Tramo(desde, levantado));
		assertThat(Tramo.efectivo(desde, levantado, hasta)).isEqualTo(new Tramo(desde, levantado));
	}

	@Test
	void solapeConIntervalosSemiabiertos() {
		Tramo t = new Tramo(local("10:00"), local("12:00"));

		assertThat(t.solapa(local("08:30"), local("10:00"))).as("adyacente por delante").isFalse();
		assertThat(t.solapa(local("12:00"), local("13:30"))).as("adyacente por detras").isFalse();
		assertThat(t.solapa(local("09:30"), local("11:00"))).isTrue();
		assertThat(t.solapa(local("11:00"), local("12:30"))).isTrue();
		assertThat(t.solapa(local("10:30"), local("11:30"))).as("contenida").isTrue();
		assertThat(new Tramo(local("10:00"), null).solapa(local("23:00"), local("23:30"))).as("sin fin").isTrue();
		assertThat(new Tramo(local("10:00"), null).solapa(local("08:00"), local("10:00"))).isFalse();
	}

	@Test
	void unRangoVacioNoSolapaConNada() {
		// levantado antes de empezar: [10:00, 09:00) es vacio y no debe tapar una casilla que lo "rodea"
		Tramo vacio = Tramo.efectivo(local("10:00"), null, local("09:00"));
		Tramo igual = Tramo.efectivo(local("10:00"), null, local("10:00"));

		assertThat(vacio.solapa(local("08:00"), local("20:00"))).isFalse();
		assertThat(igual.solapa(local("08:00"), local("20:00"))).isFalse();
	}

	// ---- exclusion por bloqueos ----

	@Test
	void unBloqueoQueCubreUnaCasillaLaQuitaDeLasOfrecidas() {
		List<Ranura> r = unDia(List.of(new Tramo(local("09:30"), local("11:00"))), Set.of());

		assertThat(inicios(r)).doesNotContain(local("09:30")).hasSize(9);
	}

	@Test
	void unBloqueoQueSoloRozaLaCasillaPorDentroLaQuita() {
		// [10:00, 10:10) solapa con la casilla 09:30-11:00
		List<Ranura> r = unDia(List.of(new Tramo(local("10:00"), local("10:10"))), Set.of());

		assertThat(inicios(r)).doesNotContain(local("09:30")).hasSize(9);
	}

	@Test
	void losBloqueosAdyacentesNoQuitanLasCasillasVecinas() {
		// [11:00, 12:30) quita solo la de las 11:00; la de las 09:30 acaba a las 11:00 y la de las 12:30 empieza cuando acaba
		List<Ranura> r = unDia(List.of(new Tramo(local("11:00"), local("12:30"))), Set.of());

		assertThat(inicios(r)).contains(local("09:30"), local("12:30")).doesNotContain(local("11:00")).hasSize(9);
	}

	@Test
	void unBloqueoQueTerminaJustoAlEmpezarUnaCasillaNoLaQuita() {
		List<Ranura> r = unDia(List.of(new Tramo(local("08:00"), local("09:30"))), Set.of());

		assertThat(inicios(r)).doesNotContain(local("08:00")).contains(local("09:30")).hasSize(9);
	}

	@Test
	void unBloqueoSinFinQuitaTodoDesdeSuInicioEnTodosLosDiasSiguientes() {
		List<Tramo> bloqueos = List.of(new Tramo(local("17:00"), null));

		List<Ranura> r = ranuras(club(8, 23), DIA, DIA.plusDays(1), bloqueos, Set.of());

		// el primer dia quedan 08:00..15:30 (6) y el segundo dia no queda ninguna
		assertThat(r).hasSize(6);
		assertThat(r).allSatisfy(x -> assertThat(x.inicio()).isBefore(local("17:00")));
	}

	@Test
	void unBloqueoLevantadoSoloTapaHastaQueSeLevanto() {
		// bloqueo sin fin desde las 08:00 levantado a las 12:30: quedan las casillas desde las 12:30
		Tramo t = Tramo.efectivo(local("08:00"), null, local("12:30"));

		List<Ranura> r = unDia(List.of(t), Set.of());

		assertThat(inicios(r)).isEqualTo(esperadas("12:30", "14:00", "15:30", "17:00", "18:30", "20:00", "21:30"));
	}

	@Test
	void unBloqueoLevantadoAntesDeEmpezarNoQuitaNada() {
		Tramo t = Tramo.efectivo(local("10:00"), local("20:00"), local("09:00"));

		assertThat(unDia(List.of(t), Set.of())).hasSize(10);
	}

	@Test
	void unBloqueoCaducadoDeDiasAnterioresNoAfectaAOtroDia() {
		Tramo ayer = new Tramo(local(DIA.minusDays(1), "08:00"), local(DIA.minusDays(1), "23:00"));

		assertThat(unDia(List.of(ayer), Set.of())).hasSize(10);
	}

	@Test
	void variosBloqueosSeAcumulan() {
		List<Tramo> bloqueos = List.of(new Tramo(local("08:00"), local("09:30")), new Tramo(local("20:00"), local("23:00")));

		assertThat(inicios(unDia(bloqueos, Set.of()))).isEqualTo(esperadas("09:30", "11:00", "12:30", "14:00", "15:30", "17:00", "18:30"));
	}

	// ---- ocupadas ----

	@Test
	void unaReservaConfirmadaQueEmpiezaEnUnaRanuraLaMarcaOcupada() {
		List<Ranura> r = unDia(List.of(), Set.of(local("09:30"), local("21:30")));

		assertThat(r.stream().filter(Ranura::ocupada).map(Ranura::inicio).toList()).containsExactly(local("09:30"), local("21:30"));
		assertThat(r).hasSize(10);
	}

	@Test
	void unaReservaFueraDeLaRejillaNoOcupaNingunaRanura() {
		// empieza a las 10:00, que no es inicio de casilla (la rejilla es 08:00, 09:30, 11:00...)
		List<Ranura> r = unDia(List.of(), Set.of(local("10:00")));

		assertThat(r).hasSize(10).noneMatch(Ranura::ocupada);
	}

	@Test
	void unaReservaAnteriorAUnBloqueoSigueContandoComoOfrecidaYOcupada() {
		// reservada a las 11:00 y bloqueada despues: la casilla sigue contando (ocupadas <= ofrecidas siempre)
		List<Ranura> r = unDia(List.of(new Tramo(local("08:00"), null)), Set.of(local("11:00")));

		assertThat(r).containsExactly(new Ranura(local("11:00"), true));
	}

	@Test
	void lasOcupadasNuncaSuperanALasOfrecidas() {
		Set<Instant> todas = Set.copyOf(esperadas(HORAS_08_A_23.toArray(String[]::new)));

		List<Ranura> r = unDia(List.of(new Tramo(local("12:00"), local("15:00"))), todas);

		assertThat(r.stream().filter(Ranura::ocupada).count()).isLessThanOrEqualTo(r.size()).isEqualTo(10);
	}

	@Test
	void unaReservaDeOtroDiaNoOcupaLaRanuraDeEsteDia() {
		List<Ranura> r = unDia(List.of(), Set.of(local(DIA.plusDays(1), "09:30")));

		assertThat(r).noneMatch(Ranura::ocupada);
	}
}
