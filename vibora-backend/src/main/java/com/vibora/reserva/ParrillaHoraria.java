package com.vibora.reserva;

import com.vibora.club.Club;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Rejilla de casillas de un club: empiezan a la hora de apertura y se encadenan cada "duracion"
 * hasta que la ultima termina como tarde a la hora de cierre, todo el mismo dia local.
 */
public final class ParrillaHoraria {

	private ParrillaHoraria() {
	}

	/** 400 si el inicio no es una casilla valida de la rejilla del club. */
	static void validar(Club club, Instant inicio, Duration duracion, ZoneId zona) {
		LocalDateTime local = inicio.atZone(zona).toLocalDateTime();
		LocalDateTime finLocal = local.plus(duracion);
		LocalTime apertura = club.getHoraApertura();
		LocalTime cierre = club.getHoraCierre();
		if (local.toLocalTime().isBefore(apertura)
				|| !finLocal.toLocalDate().equals(local.toLocalDate())
				|| finLocal.toLocalTime().isAfter(cierre)) {
			throw new IllegalArgumentException(
					"La reserva queda fuera del horario del club (" + apertura + " - " + cierre + ")");
		}
		Duration desdeApertura = Duration.between(apertura, local.toLocalTime());
		if (desdeApertura.toNanos() % duracion.toNanos() != 0) {
			throw new IllegalArgumentException("La hora de inicio no coincide con las casillas del club (cada "
					+ duracion.toMinutes() + " minutos desde las " + apertura + ")");
		}
	}

	/** Inicios de todas las casillas del dia local indicado (publico: tambien lo usan las metricas de ocupacion). */
	public static List<Instant> inicios(Club club, LocalDate fecha, Duration duracion, ZoneId zona) {
		long paso = duracion.toSeconds();
		long cierre = club.getHoraCierre().toSecondOfDay();
		List<Instant> inicios = new ArrayList<>();
		for (long s = club.getHoraApertura().toSecondOfDay(); s + paso <= cierre; s += paso) {
			inicios.add(fecha.atTime(LocalTime.ofSecondOfDay(s)).atZone(zona).toInstant());
		}
		return inicios;
	}
}
