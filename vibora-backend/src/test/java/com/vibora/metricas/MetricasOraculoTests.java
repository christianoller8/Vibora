package com.vibora.metricas;

import com.vibora.pista.Pista;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ORACULO: unos miles de reservas aleatorias (semilla fija) en 5 pistas (una inactiva) durante 187 dias que cruzan los dos
 * cambios de hora, con bloqueos de todo tipo. Los valores esperados se calculan aqui con bucles simples sobre los datos
 * generados (sin reutilizar nada del codigo de produccion) y se comparan con el JSON de cada endpoint.
 */
class MetricasOraculoTests extends MetricasApiTestBase {

	private static final LocalDate DESDE = d(2026, 10, 1);
	private static final LocalDate HASTA = d(2027, 4, 5);
	private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

	/** Una reserva generada: indice de pista, inicio local, estado y precio. */
	record Res(int pista, ZonedDateTime inicio, boolean confirmada, BigDecimal precio) {
		LocalDate dia() {
			return inicio.toLocalDate();
		}
	}

	/** Un bloqueo generado, con su rango tal como esta en la BD. */
	record Blq(int pista, Instant desde, Instant hasta, Instant levantadoEn) {
	}

	/** Contadores de una celda (dia de la semana + hora). */
	static final class Celda {
		int ofrecidas;
		int ocupadas;
		int reservas;
		BigDecimal ingresos = BigDecimal.ZERO;
	}

	private final List<Res> res = new ArrayList<>();
	private final List<Blq> blqs = new ArrayList<>();
	private final List<Pista> pistasCreadas = new ArrayList<>();

	private boolean activa(int i) {
		return i < 4;
	}

	private void generar() {
		Random rnd = new Random(20261024L);
		// pistas 1-4 activas; la 5 esta inactiva pero tiene reservas; la 6 esta inactiva y NUNCA tiene reservas (no debe salir)
		for (int i = 0; i < 6; i++) {
			pistasCreadas.add(i >= 4 ? pistaInactiva(club, "Pista " + (i + 1)) : pista(club, "Pista " + (i + 1)));
		}
		List<Object[]> filas = new ArrayList<>();
		// dias sin ninguna reserva en ninguna pista: la serie tiene que rellenarlos con ceros
		java.util.Set<LocalDate> muertos = new java.util.HashSet<>();
		for (LocalDate dia = DESDE; !dia.isAfter(HASTA); dia = dia.plusDays(1)) {
			if (rnd.nextDouble() < 0.08) {
				muertos.add(dia);
			}
		}
		muertos.add(d(2026, 10, 25)); // el domingo del cambio de hora de otono, sin reservas
		for (int i = 0; i < 5; i++) {
			for (LocalDate dia = DESDE.minusDays(1); !dia.isAfter(HASTA.plusDays(1)); dia = dia.plusDays(1)) {
				if (muertos.contains(dia)) {
					continue;
				}
				for (String hora : HORAS) {
					double azar = rnd.nextDouble();
					BigDecimal precio = BigDecimal.valueOf(500 + rnd.nextInt(5500), 2);
					ZonedDateTime inicio = dia.atTime(java.time.LocalTime.parse(hora)).atZone(MADRID);
					if (azar < 0.45) {
						res.add(new Res(i, inicio, true, precio));
					} else if (azar < 0.55) {
						res.add(new Res(i, inicio, false, precio));
					} else if (azar < 0.62) {
						res.add(new Res(i, inicio, false, precio));
						res.add(new Res(i, inicio, true, BigDecimal.valueOf(500 + rnd.nextInt(5500), 2)));
					}
				}
				if (rnd.nextDouble() < 0.04) { // una cancelada fuera de rejilla (10:00 no es inicio de casilla)
					res.add(new Res(i, dia.atTime(10, 0).atZone(MADRID), false, BigDecimal.valueOf(500 + rnd.nextInt(5500), 2)));
				}
				// reservas pegadas a la medianoche local: en UTC caen en OTRO dia (las confirmadas a las 23:30 acaban a la 01:00)
				if (rnd.nextDouble() < 0.10) {
					res.add(new Res(i, dia.atTime(23, 30).atZone(MADRID), true, BigDecimal.valueOf(500 + rnd.nextInt(5500), 2)));
				}
				if (rnd.nextDouble() < 0.05) {
					res.add(new Res(i, dia.atTime(0, 30).atZone(MADRID), false, BigDecimal.valueOf(500 + rnd.nextInt(5500), 2)));
				}
			}
		}
		for (Res r : res) {
			filas.add(new Object[] { pistasCreadas.get(r.pista()).getId(), organizador.getId(),
					OffsetDateTime.ofInstant(r.inicio().toInstant(), ZoneOffset.UTC),
					OffsetDateTime.ofInstant(r.inicio().toInstant().plusSeconds(5400), ZoneOffset.UTC),
					r.confirmada() ? "CONFIRMADA" : "CANCELADA", r.precio() });
		}
		jdbc.batchUpdate("insert into reserva (pista_id, usuario_id, inicio, fin, estado, precio_total) values (?, ?, ?, ?, ?, ?)",
				filas);

		for (int b = 0; b < 16; b++) {
			int pista = rnd.nextInt(5);
			LocalDate dia = DESDE.plusDays(rnd.nextInt((int) java.time.temporal.ChronoUnit.DAYS.between(DESDE, HASTA) + 1));
			Instant desde = dia.atStartOfDay(MADRID).toInstant().plusSeconds(60L * 15 * (28 + rnd.nextInt(60)));
			Instant hasta = rnd.nextDouble() < 0.2 ? null : desde.plusSeconds(3600L * (1 + rnd.nextInt(60)));
			Instant levantado = null;
			if (hasta == null) {
				levantado = desde.plusSeconds(3600L * (1 + rnd.nextInt(200)));
			} else if (rnd.nextDouble() < 0.35) {
				levantado = desde.plusSeconds(60L * (rnd.nextInt(24 * 60 + 60) - 60)); // a veces ANTES de empezar
			}
			blqs.add(new Blq(pista, desde, hasta, levantado));
			bloqueo(pistasCreadas.get(pista), desde, hasta, levantado);
		}
		assertThat(res.size()).as("reservas generadas").isGreaterThan(3000);
	}

	// ---- el oraculo ----

	private boolean enRango(Res r) {
		return !r.dia().isBefore(DESDE) && !r.dia().isAfter(HASTA);
	}

	private boolean incluida(int pista) {
		return activa(pista) || res.stream().anyMatch(r -> r.pista() == pista && enRango(r));
	}

	private boolean bloqueada(int pista, ZonedDateTime inicio, ZonedDateTime fin) {
		for (Blq b : blqs) {
			if (b.pista() != pista) {
				continue;
			}
			Instant fin_ = b.hasta();
			if (b.levantadoEn() != null && (fin_ == null || b.levantadoEn().isBefore(fin_))) {
				fin_ = b.levantadoEn();
			}
			boolean vacio = fin_ != null && !fin_.isAfter(b.desde());
			if (!vacio && b.desde().isBefore(fin.toInstant()) && (fin_ == null || fin_.isAfter(inicio.toInstant()))) {
				return true;
			}
		}
		return false;
	}

	private boolean confirmadaEn(int pista, ZonedDateTime inicio) {
		for (Res r : res) {
			if (r.pista() == pista && r.confirmada() && r.inicio().toInstant().equals(inicio.toInstant())) {
				return true;
			}
		}
		return false;
	}

	private static BigDecimal ratio(long a, long b) {
		return b == 0 ? null : BigDecimal.valueOf(a).divide(BigDecimal.valueOf(b), 4, RoundingMode.HALF_UP);
	}

	/** [ofrecidas, ocupadas] de una pista en el rango, y las celdas por dia de la semana y hora (acumulando). */
	private long[] ranuras(int pista, Map<String, Celda> celdas) {
		long ofrecidas = 0;
		long ocupadas = 0;
		for (LocalDate dia = DESDE; !dia.isAfter(HASTA); dia = dia.plusDays(1)) {
			for (String hora : HORAS) {
				ZonedDateTime inicio = dia.atTime(java.time.LocalTime.parse(hora)).atZone(MADRID);
				ZonedDateTime fin = inicio.plusMinutes(90);
				boolean reservada = confirmadaEn(pista, inicio);
				if (reservada || !bloqueada(pista, inicio, fin)) {
					ofrecidas++;
					Celda c = celdas.computeIfAbsent(clave(dia.getDayOfWeek(), hora), k -> new Celda());
					c.ofrecidas++;
					if (reservada) {
						ocupadas++;
						c.ocupadas++;
					}
				}
			}
		}
		return new long[] { ofrecidas, ocupadas };
	}

	private static String clave(DayOfWeek dia, String hora) {
		return dia.getValue() + "|" + hora;
	}

	private static LocalDate lunesDe(LocalDate f) {
		LocalDate l = f;
		while (l.getDayOfWeek() != DayOfWeek.MONDAY) {
			l = l.minusDays(1);
		}
		return l;
	}

	private static LocalDate periodo(String granularidad, LocalDate f) {
		return switch (granularidad) {
			case "DIA" -> f;
			case "SEMANA" -> lunesDe(f);
			default -> f.withDayOfMonth(1);
		};
	}

	/** Periodos de la serie, de 'periodo(desde)' hasta 'hasta', sin huecos. */
	private static List<LocalDate> periodos(String granularidad) {
		List<LocalDate> r = new ArrayList<>();
		LocalDate p = periodo(granularidad, DESDE);
		while (!p.isAfter(HASTA)) {
			r.add(p);
			p = switch (granularidad) {
				case "DIA" -> p.plusDays(1);
				case "SEMANA" -> p.plusDays(7);
				default -> p.plusMonths(1);
			};
		}
		return r;
	}

	private static BigDecimal suma(List<Res> lista) {
		BigDecimal t = BigDecimal.ZERO;
		for (Res r : lista) {
			t = t.add(r.precio());
		}
		return t;
	}

	private List<Res> enRango(boolean confirmada) {
		return res.stream().filter(r -> enRango(r) && r.confirmada() == confirmada).toList();
	}

	// ---- la comparacion ----

	private static void igual(Object json, BigDecimal esperado, String que) {
		if (esperado == null) {
			assertThat(json).as(que).isNull();
		} else {
			assertThat(num(json)).as(que).isEqualByComparingTo(esperado);
		}
	}

	@Test
	void todosLosEndpointsCoincidenConUnCalculoIndependienteSobreMilesDeReservas() throws Exception {
		generar();
		String[] r = rango(DESDE, HASTA);

		resumen(r);
		for (String g : List.of("DIA", "SEMANA", "MES")) {
			ingresos(g);
			cancelaciones(g);
		}
		ocupacionYFranjas(r);
	}

	private void resumen(String[] r) throws Exception {
		var j = json(comoAdmin("resumen", r).andExpect(status().isOk()));
		List<Res> conf = enRango(true);
		List<Res> canc = enRango(false);

		igual(j.read("$.ingresos"), suma(conf), "ingresos");
		assertThat(j.read("$.reservasConfirmadas", Integer.class)).isEqualTo(conf.size());
		assertThat(j.read("$.reservasCanceladas", Integer.class)).isEqualTo(canc.size());
		igual(j.read("$.tasaCancelacion"), ratio(canc.size(), conf.size() + canc.size()), "tasa");
		igual(j.read("$.ticketMedio"), suma(conf).divide(BigDecimal.valueOf(conf.size()), 2, RoundingMode.HALF_UP), "ticket");
		igual(j.read("$.ingresosPerdidosPorCancelacion"), suma(canc), "perdidos");

		long ofrecidas = 0;
		long ocupadas = 0;
		for (int i = 0; i < 5; i++) {
			if (incluida(i)) {
				long[] o = ranuras(i, new TreeMap<>());
				ofrecidas += o[0];
				ocupadas += o[1];
			}
		}
		igual(j.read("$.ocupacionGlobal"), ratio(ocupadas, ofrecidas), "ocupacion global");
		igual(j.read("$.facturado.total"), BigDecimal.ZERO, "facturado");
	}

	private void ingresos(String g) throws Exception {
		var j = json(comoAdmin("ingresos", rango(DESDE, HASTA, "granularidad", g)).andExpect(status().isOk()));
		List<LocalDate> periodos = periodos(g);
		List<Map<String, Object>> serie = j.read("$.serie");

		assertThat(serie).as("puntos " + g).hasSize(periodos.size());
		for (int i = 0; i < periodos.size(); i++) {
			LocalDate p = periodos.get(i);
			List<Res> delPeriodo = enRango(true).stream().filter(x -> periodo(g, x.dia()).equals(p)).toList();
			assertThat(serie.get(i).get("periodo")).isEqualTo(p.toString());
			igual(serie.get(i).get("ingresos"), suma(delPeriodo), "ingresos " + g + " " + p);
			assertThat(serie.get(i).get("reservas")).as("reservas " + g + " " + p).isEqualTo(delPeriodo.size());
		}
	}

	private void cancelaciones(String g) throws Exception {
		var j = json(comoAdmin("cancelaciones", rango(DESDE, HASTA, "granularidad", g)).andExpect(status().isOk()));
		List<LocalDate> periodos = periodos(g);
		List<Map<String, Object>> serie = j.read("$.serie");

		assertThat(serie).as("puntos " + g).hasSize(periodos.size());
		for (int i = 0; i < periodos.size(); i++) {
			LocalDate p = periodos.get(i);
			long canc = enRango(false).stream().filter(x -> periodo(g, x.dia()).equals(p)).count();
			long conf = enRango(true).stream().filter(x -> periodo(g, x.dia()).equals(p)).count();
			assertThat(serie.get(i).get("periodo")).isEqualTo(p.toString());
			assertThat(serie.get(i).get("canceladas")).as("canceladas " + g + " " + p).isEqualTo((int) canc);
			assertThat(serie.get(i).get("confirmadas")).as("confirmadas " + g + " " + p).isEqualTo((int) conf);
			igual(serie.get(i).get("tasa"), ratio(canc, canc + conf), "tasa " + g + " " + p);
		}
		if (g.equals("DIA")) {
			List<Map<String, Object>> porPista = j.read("$.porPista");
			List<Integer> esperadas = new ArrayList<>();
			for (int i = 0; i < 5; i++) {
				if (incluida(i)) {
					esperadas.add(i);
				}
			}
			assertThat(porPista).hasSize(esperadas.size());
			for (int k = 0; k < esperadas.size(); k++) {
				int i = esperadas.get(k);
				final int pistaActual = i;
				List<Res> suyas = enRango(false).stream().filter(x -> x.pista() == pistaActual).toList();
				assertThat(porPista.get(k).get("pistaNombre")).isEqualTo("Pista " + (i + 1));
				assertThat(((Number) porPista.get(k).get("pistaId")).longValue()).isEqualTo(pistasCreadas.get(i).getId());
				assertThat(porPista.get(k).get("canceladas")).isEqualTo(suyas.size());
				igual(porPista.get(k).get("ingresosPerdidos"), suma(suyas), "perdidos pista " + i);
			}
		}
	}

	private void ocupacionYFranjas(String[] r) throws Exception {
		var oc = json(comoAdmin("ocupacion", r).andExpect(status().isOk()));
		List<Map<String, Object>> filas = oc.read("$.pistas");
		Map<String, Celda> celdas = new LinkedHashMap<>();
		long totalOfrecidas = 0;
		long totalOcupadas = 0;
		int k = 0;
		for (int i = 0; i < 5; i++) {
			if (!incluida(i)) {
				continue;
			}
			long[] o = ranuras(i, celdas);
			final int pista = i;
			List<Res> suyas = enRango(true).stream().filter(x -> x.pista() == pista).toList();
			Map<String, Object> f = filas.get(k++);
			assertThat(f.get("pistaNombre")).isEqualTo("Pista " + (i + 1));
			assertThat(f.get("ranurasOfrecidas")).as("ofrecidas pista " + i).isEqualTo((int) o[0]);
			assertThat(f.get("ranurasOcupadas")).as("ocupadas pista " + i).isEqualTo((int) o[1]);
			igual(f.get("ocupacion"), ratio(o[1], o[0]), "ocupacion pista " + i);
			igual(f.get("ingresos"), suma(suyas), "ingresos pista " + i);
			totalOfrecidas += o[0];
			totalOcupadas += o[1];
		}
		assertThat(filas).hasSize(k);
		assertThat(oc.read("$.ranurasOfrecidas", Integer.class)).isEqualTo((int) totalOfrecidas);
		assertThat(oc.read("$.ranurasOcupadas", Integer.class)).isEqualTo((int) totalOcupadas);
		igual(oc.read("$.ocupacionGlobal"), ratio(totalOcupadas, totalOfrecidas), "global");

		// las reservas confirmadas de las celdas (las de las 23:30 no son de rejilla: su celda no tiene ranuras ofrecidas)
		for (Res x : enRango(true)) {
			Celda c = celdas.computeIfAbsent(clave(x.inicio().getDayOfWeek(), x.inicio().format(HH_MM)), clave -> new Celda());
			c.reservas++;
			c.ingresos = c.ingresos.add(x.precio());
		}
		List<String> ordenadas = new ArrayList<>(celdas.keySet());
		ordenadas.sort(Comparator.comparingInt((String s) -> Integer.parseInt(s.substring(0, s.indexOf('|'))))
				.thenComparing(s -> s.substring(s.indexOf('|') + 1)));
		List<String> conOfrecidas = ordenadas.stream().filter(s -> celdas.get(s).ofrecidas > 0).toList();
		assertThat(ordenadas.size()).as("hay celdas sin ranuras (reservas de las 23:30)").isGreaterThan(conOfrecidas.size());

		var fr = json(comoAdmin("franjas", rango(DESDE, HASTA, "limit", "12")).andExpect(status().isOk()));
		List<Map<String, Object>> matriz = fr.read("$.matriz");
		assertThat(matriz).hasSize(conOfrecidas.size());
		for (int i = 0; i < conOfrecidas.size(); i++) {
			String clave = conOfrecidas.get(i);
			Celda c = celdas.get(clave);
			assertFranja(matriz.get(i), clave, c);
		}
		List<String> conReservas = ordenadas.stream().filter(s -> celdas.get(s).reservas > 0).sorted(Comparator
				.comparing((String s) -> celdas.get(s).ingresos).reversed()
				.thenComparing(Comparator.comparingInt((String s) -> celdas.get(s).reservas).reversed())
				.thenComparingInt(s -> Integer.parseInt(s.substring(0, s.indexOf('|'))))
				.thenComparing(s -> s.substring(s.indexOf('|') + 1))).limit(12).toList();
		List<Map<String, Object>> top = fr.read("$.masRentables");
		assertThat(top).hasSize(conReservas.size());
		for (int i = 0; i < conReservas.size(); i++) {
			assertFranja(top.get(i), conReservas.get(i), celdas.get(conReservas.get(i)));
		}
	}

	private static void assertFranja(Map<String, Object> f, String clave, Celda c) {
		int dia = Integer.parseInt(clave.substring(0, clave.indexOf('|')));
		assertThat(f.get("diaSemana")).as(clave).isEqualTo(DayOfWeek.of(dia).name());
		assertThat(f.get("hora")).as(clave).isEqualTo(clave.substring(clave.indexOf('|') + 1));
		assertThat(f.get("reservas")).as("reservas " + clave).isEqualTo(c.reservas);
		igual(f.get("ingresos"), c.ingresos, "ingresos " + clave);
		assertThat(f.get("ranurasOfrecidas")).as("ofrecidas " + clave).isEqualTo(c.ofrecidas);
		igual(f.get("ocupacion"), ratio(c.ocupadas, c.ofrecidas), "ocupacion " + clave);
	}

}
