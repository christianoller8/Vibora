package com.vibora.metricas;

import com.vibora.auth.AutorizacionClub;
import com.vibora.club.Club;
import com.vibora.club.ClubRepository;
import com.vibora.common.exception.RecursoNoEncontradoException;
import com.vibora.factura.FacturaRepository;
import com.vibora.factura.TipoFactura;
import com.vibora.metricas.CalculadoraRanuras.Ranura;
import com.vibora.metricas.CalculadoraRanuras.Tramo;
import com.vibora.pista.Pista;
import com.vibora.pista.PistaRepository;
import com.vibora.reserva.EstadoReserva;
import com.vibora.reserva.ReservaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Metricas del club para su panel (solo lectura, solo el administrador del club). Todas las consultas son de TAMANO FIJO:
 * el club, sus pistas, sus reservas del rango, sus bloqueos del rango y (en el resumen) dos sumas de facturas; el resto es
 * calculo en memoria. El numero de sentencias SQL no depende del numero de pistas ni de dias.
 * <p>
 * Definiciones:
 * <ul>
 * <li><b>Dia de una reserva</b>: la fecha LOCAL (zona del club) de su inicio. Todos los periodos se calculan sobre fechas
 * locales, nunca sobre instantes UTC: un domingo de cambio de hora sigue siendo un dia.</li>
 * <li><b>Ingresos</b>: suma del precio congelado de las reservas CONFIRMADAS por dia de juego (no es lo cobrado ni lo
 * facturado). Las CANCELADAS no son ingreso: suman en "ingresos perdidos".</li>
 * <li><b>Ranuras ofrecidas / ocupadas</b>: ver {@link CalculadoraRanuras}. Se aplican el horario y las pistas ACTUALES a
 * todo el rango: el modelo no guarda historico de horarios ni de altas y bajas de pistas. Cuentan tambien los dias futuros del
 * rango. Una pista inactiva solo cuenta si tiene alguna reserva en el rango (se le aplica la rejilla actual).</li>
 * <li><b>Facturado</b>: facturas emitidas en el rango por el dia local de {@code emitida_en}; las rectificativas llevan
 * importes negativos, asi que la suma es neta.</li>
 * </ul>
 * Ningun dato de jugadores sale de aqui.
 */
@Service
@RequiredArgsConstructor
public class MetricasService {

	static final int LIMITE_POR_DEFECTO = 10;
	static final int LIMITE_MAXIMO = 50;
	private static final BigDecimal CERO = BigDecimal.ZERO.setScale(2);
	private static final int DECIMALES_FRACCION = 4;
	private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

	private final ClubRepository clubs;
	private final PistaRepository pistas;
	private final ReservaMetricasRepository reservas;
	private final BloqueoMetricasRepository bloqueos;
	private final FacturaRepository facturas;
	private final AutorizacionClub autorizacion;
	private final ReservaProperties propiedades;
	private final Clock reloj;
	private final ZoneId zona;

	/** Una reserva reducida a lo que las metricas necesitan: sin jugador. */
	private record Fila(Long pistaId, Instant inicio, LocalDate dia, boolean confirmada, BigDecimal precio) {
	}

	private record Contexto(Club club, RangoFechas rango, List<Fila> filas) {
	}

	/** Acumulador de un periodo o de una franja. */
	private static final class Acumulado {
		BigDecimal importe = CERO;
		long confirmadas;
		long canceladas;

		void confirmada(BigDecimal precio) {
			confirmadas++;
			importe = importe.add(precio);
		}

		void cancelada(BigDecimal precio) {
			canceladas++;
			importe = importe.add(precio);
		}
	}

	private record OcupacionPista(Pista pista, List<Ranura> ranuras, BigDecimal ingresos) {
		long ofrecidas() {
			return ranuras.size();
		}

		long ocupadas() {
			return ranuras.stream().filter(Ranura::ocupada).count();
		}
	}

	// ---- resumen ----

	@Transactional(readOnly = true)
	public ResumenMetricasResponse resumen(Long clubId, LocalDate desde, LocalDate hasta) {
		Contexto ctx = contexto(clubId, desde, hasta);
		BigDecimal ingresos = CERO;
		BigDecimal perdidos = CERO;
		long confirmadas = 0;
		long canceladas = 0;
		for (Fila f : ctx.filas()) {
			if (f.confirmada()) {
				confirmadas++;
				ingresos = ingresos.add(f.precio());
			} else {
				canceladas++;
				perdidos = perdidos.add(f.precio());
			}
		}
		BigDecimal ticketMedio = confirmadas == 0 ? null
				: ingresos.divide(BigDecimal.valueOf(confirmadas), 2, RoundingMode.HALF_UP);

		List<OcupacionPista> porPista = ocupacionPorPista(ctx);
		long ofrecidas = porPista.stream().mapToLong(OcupacionPista::ofrecidas).sum();
		long ocupadas = porPista.stream().mapToLong(OcupacionPista::ocupadas).sum();

		return new ResumenMetricasResponse(ctx.rango().desde(), ctx.rango().hasta(), ingresos, confirmadas, canceladas,
				fraccion(canceladas, confirmadas + canceladas), ticketMedio, perdidos, fraccion(ocupadas, ofrecidas),
				facturado(clubId, ctx.rango()));
	}

	private ResumenMetricasResponse.Facturado facturado(Long clubId, RangoFechas rango) {
		Instant desde = rango.inicio(zona);
		Instant hasta = rango.fin(zona);
		FacturaRepository.Totales originales = facturas.totalesDelClub(clubId, desde, hasta, List.of(TipoFactura.ORIGINAL));
		FacturaRepository.Totales rectificativas = facturas.totalesDelClub(clubId, desde, hasta,
				List.of(TipoFactura.RECTIFICATIVA));
		return new ResumenMetricasResponse.Facturado(
				suma(originales.getBase()).add(suma(rectificativas.getBase())),
				suma(originales.getCuotaIva()).add(suma(rectificativas.getCuotaIva())),
				suma(originales.getTotal()).add(suma(rectificativas.getTotal())), originales.getCantidad(),
				rectificativas.getCantidad());
	}

	// ---- series ----

	@Transactional(readOnly = true)
	public IngresosResponse ingresos(Long clubId, LocalDate desde, LocalDate hasta, Granularidad granularidad) {
		Contexto ctx = contexto(clubId, desde, hasta);
		Map<LocalDate, Acumulado> periodos = periodosVacios(granularidad, ctx.rango());
		for (Fila f : ctx.filas()) {
			if (f.confirmada()) {
				periodos.get(granularidad.inicioDe(f.dia())).confirmada(f.precio());
			}
		}
		List<IngresosResponse.Punto> serie = periodos.entrySet().stream()
				.map(e -> new IngresosResponse.Punto(e.getKey(), e.getValue().importe, e.getValue().confirmadas)).toList();
		return new IngresosResponse(ctx.rango().desde(), ctx.rango().hasta(), granularidad, serie);
	}

	@Transactional(readOnly = true)
	public CancelacionesResponse cancelaciones(Long clubId, LocalDate desde, LocalDate hasta, Granularidad granularidad) {
		Contexto ctx = contexto(clubId, desde, hasta);
		Map<LocalDate, Acumulado> periodos = periodosVacios(granularidad, ctx.rango());
		Map<Long, Acumulado> perdidasPorPista = new HashMap<>();
		for (Fila f : ctx.filas()) {
			Acumulado periodo = periodos.get(granularidad.inicioDe(f.dia()));
			if (f.confirmada()) {
				periodo.confirmada(f.precio());
			} else {
				periodo.cancelada(f.precio());
				perdidasPorPista.computeIfAbsent(f.pistaId(), k -> new Acumulado()).cancelada(f.precio());
			}
		}
		List<CancelacionesResponse.Punto> serie = periodos.entrySet().stream()
				.map(e -> new CancelacionesResponse.Punto(e.getKey(), e.getValue().canceladas, e.getValue().confirmadas,
						fraccion(e.getValue().canceladas, e.getValue().canceladas + e.getValue().confirmadas)))
				.toList();
		List<CancelacionesResponse.PorPista> porPista = consideradas(ctx).stream().map(p -> {
			Acumulado a = perdidasPorPista.getOrDefault(p.getId(), new Acumulado());
			return new CancelacionesResponse.PorPista(p.getId(), p.getNombre(), a.canceladas, a.importe);
		}).toList();
		return new CancelacionesResponse(ctx.rango().desde(), ctx.rango().hasta(), granularidad, serie, porPista);
	}

	/** Un acumulador a cero por cada periodo del rango, en orden: la serie sale sin huecos. */
	private static Map<LocalDate, Acumulado> periodosVacios(Granularidad granularidad, RangoFechas rango) {
		Map<LocalDate, Acumulado> periodos = new TreeMap<>();
		for (LocalDate p : granularidad.periodos(rango)) {
			periodos.put(p, new Acumulado());
		}
		return periodos;
	}

	// ---- ocupacion ----

	@Transactional(readOnly = true)
	public OcupacionResponse ocupacion(Long clubId, LocalDate desde, LocalDate hasta) {
		Contexto ctx = contexto(clubId, desde, hasta);
		List<OcupacionPista> porPista = ocupacionPorPista(ctx);
		long ofrecidas = porPista.stream().mapToLong(OcupacionPista::ofrecidas).sum();
		long ocupadas = porPista.stream().mapToLong(OcupacionPista::ocupadas).sum();
		List<OcupacionResponse.PistaOcupacion> filas = porPista.stream()
				.map(o -> new OcupacionResponse.PistaOcupacion(o.pista().getId(), o.pista().getNombre(), o.ofrecidas(),
						o.ocupadas(), fraccion(o.ocupadas(), o.ofrecidas()), o.ingresos()))
				.toList();
		return new OcupacionResponse(ctx.rango().desde(), ctx.rango().hasta(), fraccion(ocupadas, ofrecidas), ofrecidas,
				ocupadas, filas);
	}

	// ---- franjas ----

	/** Dia de la semana y hora local de inicio: las franjas se ordenan por dia (lunes primero) y hora. */
	private record ClaveFranja(DayOfWeek dia, LocalTime hora) implements Comparable<ClaveFranja> {
		@Override
		public int compareTo(ClaveFranja otra) {
			int c = dia.compareTo(otra.dia);
			return c != 0 ? c : hora.compareTo(otra.hora);
		}
	}

	private static final class Celda {
		long ofrecidas;
		long ocupadas;
		long reservas;
		BigDecimal ingresos = CERO;
	}

	@Transactional(readOnly = true)
	public FranjasResponse franjas(Long clubId, LocalDate desde, LocalDate hasta, int limite) {
		Contexto ctx = contexto(clubId, desde, hasta);
		if (limite < 1 || limite > LIMITE_MAXIMO) {
			throw new IllegalArgumentException("El límite debe estar entre 1 y " + LIMITE_MAXIMO);
		}
		Map<ClaveFranja, Celda> celdas = new TreeMap<>();
		for (OcupacionPista o : ocupacionPorPista(ctx)) {
			for (Ranura r : o.ranuras()) {
				Celda c = celdas.computeIfAbsent(clave(r.inicio()), k -> new Celda());
				c.ofrecidas++;
				if (r.ocupada()) {
					c.ocupadas++;
				}
			}
		}
		Map<ClaveFranja, Celda> conReservas = new TreeMap<>();
		for (Fila f : ctx.filas()) {
			if (f.confirmada()) {
				Celda c = celdas.computeIfAbsent(clave(f.inicio()), k -> new Celda());
				c.reservas++;
				c.ingresos = c.ingresos.add(f.precio());
				conReservas.put(clave(f.inicio()), c);
			}
		}
		List<FranjasResponse.Franja> matriz = celdas.entrySet().stream().filter(e -> e.getValue().ofrecidas > 0)
				.map(e -> franja(e.getKey(), e.getValue())).toList();
		List<FranjasResponse.Franja> masRentables = conReservas.entrySet().stream()
				.sorted(Comparator.<Map.Entry<ClaveFranja, Celda>, BigDecimal>comparing(e -> e.getValue().ingresos).reversed()
						.thenComparing(Comparator.<Map.Entry<ClaveFranja, Celda>>comparingLong(e -> e.getValue().reservas)
								.reversed())
						.thenComparing(Map.Entry::getKey))
				.limit(limite).map(e -> franja(e.getKey(), e.getValue())).toList();
		return new FranjasResponse(ctx.rango().desde(), ctx.rango().hasta(), limite, masRentables, matriz);
	}

	private ClaveFranja clave(Instant inicio) {
		var local = inicio.atZone(zona);
		return new ClaveFranja(local.getDayOfWeek(), local.toLocalTime());
	}

	private static FranjasResponse.Franja franja(ClaveFranja clave, Celda c) {
		return new FranjasResponse.Franja(clave.dia(), clave.hora().format(HH_MM), c.reservas, c.ingresos, c.ofrecidas,
				fraccion(c.ocupadas, c.ofrecidas));
	}

	// ---- comun ----

	/** Club (404), administrador (403), rango (400) y las reservas del rango: la misma cabecera para todas. */
	private Contexto contexto(Long clubId, LocalDate desde, LocalDate hasta) {
		Club club = clubs.findById(clubId).orElseThrow(() -> new RecursoNoEncontradoException("Club no encontrado"));
		autorizacion.exigirAdminDe(clubId);
		RangoFechas rango = RangoFechas.resolver(desde, hasta, reloj, zona);
		List<Fila> filas = reservas.delClub(clubId, rango.inicio(zona), rango.fin(zona)).stream()
				.map(r -> new Fila(r.getPistaId(), r.getInicio(), r.getInicio().atZone(zona).toLocalDate(),
						r.getEstado() == EstadoReserva.CONFIRMADA, r.getPrecioTotal()))
				.toList();
		return new Contexto(club, rango, filas);
	}

	/** Pistas activas, mas las inactivas que tienen alguna reserva (de cualquier estado) en el rango. */
	private List<Pista> consideradas(Contexto ctx) {
		Set<Long> conReservas = new HashSet<>();
		for (Fila f : ctx.filas()) {
			conReservas.add(f.pistaId());
		}
		return pistas.findByClubIdOrderByNombre(ctx.club().getId()).stream()
				.filter(p -> p.isActiva() || conReservas.contains(p.getId())).toList();
	}

	private List<OcupacionPista> ocupacionPorPista(Contexto ctx) {
		Map<Long, List<Tramo>> tramos = new HashMap<>();
		for (BloqueoMetricasRepository.Fila b : bloqueos.delClub(ctx.club().getId(), ctx.rango().inicio(zona),
				ctx.rango().fin(zona))) {
			tramos.computeIfAbsent(b.getPistaId(), k -> new ArrayList<>())
					.add(Tramo.efectivo(b.getDesde(), b.getHasta(), b.getLevantadoEn()));
		}
		Map<Long, Set<Instant>> confirmadas = new HashMap<>();
		Map<Long, BigDecimal> ingresos = new HashMap<>();
		for (Fila f : ctx.filas()) {
			if (f.confirmada()) {
				confirmadas.computeIfAbsent(f.pistaId(), k -> new HashSet<>()).add(f.inicio());
				ingresos.merge(f.pistaId(), f.precio(), BigDecimal::add);
			}
		}
		Duration duracion = Duration.ofMinutes(propiedades.duracionMinutos());
		return consideradas(ctx).stream()
				.map(p -> new OcupacionPista(p,
						CalculadoraRanuras.ranuras(ctx.club(), ctx.rango(), duracion, zona,
								tramos.getOrDefault(p.getId(), List.of()), confirmadas.getOrDefault(p.getId(), Set.of())),
						ingresos.getOrDefault(p.getId(), CERO)))
				.toList();
	}

	/** a / b con 4 decimales (0..1); nulo si b es 0. */
	private static BigDecimal fraccion(long a, long b) {
		return b == 0 ? null : BigDecimal.valueOf(a).divide(BigDecimal.valueOf(b), DECIMALES_FRACCION, RoundingMode.HALF_UP);
	}

	private static BigDecimal suma(BigDecimal valor) {
		return valor == null ? CERO : valor;
	}
}
