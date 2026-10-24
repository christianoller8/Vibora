package com.vibora.metricas;

import com.vibora.club.Club;
import com.vibora.reserva.ParrillaHoraria;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Ranuras OFRECIDAS y OCUPADAS de una pista en un rango de dias.
 * <ul>
 * <li>La rejilla es la de las reservas ({@link ParrillaHoraria}): casillas de {@code duracion} desde la hora de apertura
 * hasta que la ultima acaba como tarde a la hora de cierre.</li>
 * <li>Una ranura NO se ofrece si solapa (intervalos semiabiertos) con el rango efectivo de algun bloqueo.</li>
 * <li>Una ranura esta OCUPADA si una reserva confirmada de la pista empieza exactamente en ella.</li>
 * <li>Una ranura bloqueada pero con una reserva confirmada (hecha antes del bloqueo) SE CUENTA como ofrecida y ocupada: asi
 * ocupadas &lt;= ofrecidas siempre, y el bloqueo solo quita lo que de verdad no se pudo vender.</li>
 * </ul>
 */
final class CalculadoraRanuras {

	private CalculadoraRanuras() {
	}

	/** Rango efectivo de un bloqueo: [desde, hasta); {@code hasta} nulo = sin fin. */
	record Tramo(Instant desde, Instant hasta) {

		/** [desde, min(hasta, levantadoEn)): un bloqueo levantado deja de tapar en el momento de levantarse. */
		static Tramo efectivo(Instant desde, Instant hasta, Instant levantadoEn) {
			Instant fin = hasta;
			if (levantadoEn != null && (fin == null || levantadoEn.isBefore(fin))) {
				fin = levantadoEn;
			}
			return new Tramo(desde, fin);
		}

		/** Solapa con [inicio, fin). Un rango vacio (levantado antes o justo al empezar) no solapa con nada. */
		boolean solapa(Instant inicio, Instant fin) {
			if (hasta != null && !hasta.isAfter(desde)) {
				return false;
			}
			return desde.isBefore(fin) && (hasta == null || hasta.isAfter(inicio));
		}
	}

	/** Una ranura ofrecida y si hay una reserva confirmada que empieza justo en ella. */
	record Ranura(Instant inicio, boolean ocupada) {
	}

	/**
	 * Ranuras ofrecidas de la pista en el rango, en orden cronologico.
	 *
	 * @param bloqueos           rangos efectivos de los bloqueos de la pista
	 * @param iniciosConfirmados inicios de las reservas confirmadas de la pista
	 */
	static List<Ranura> ranuras(Club club, RangoFechas rango, Duration duracion, ZoneId zona, List<Tramo> bloqueos,
			Set<Instant> iniciosConfirmados) {
		List<Ranura> ranuras = new ArrayList<>();
		for (var dia : rango.fechas()) {
			for (Instant inicio : ParrillaHoraria.inicios(club, dia, duracion, zona)) {
				boolean ocupada = iniciosConfirmados.contains(inicio);
				Instant fin = inicio.plus(duracion);
				if (ocupada || bloqueos.stream().noneMatch(b -> b.solapa(inicio, fin))) {
					ranuras.add(new Ranura(inicio, ocupada));
				}
			}
		}
		return ranuras;
	}
}
