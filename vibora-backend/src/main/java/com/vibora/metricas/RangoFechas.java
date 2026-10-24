package com.vibora.metricas;

import com.vibora.common.LimitesFecha;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Rango de dias LOCALES (zona del club), con ambos extremos incluidos. Una reserva pertenece al dia local de su inicio,
 * asi que el rango se traduce a instantes con el principio de cada dia local (no con 24 horas fijas: los domingos del
 * cambio de hora duran 25 o 23).
 */
record RangoFechas(LocalDate desde, LocalDate hasta) {

	static final int MAX_DIAS = 366;
	static final int DIAS_POR_DEFECTO = 30;

	/**
	 * Aplica los valores por defecto (hasta = hoy en la zona del club; desde = 29 dias antes de hasta, o sea los ultimos 30
	 * dias) y valida el rango: las fechas pedidas dentro de {@link LimitesFecha}, desde no posterior a hasta y como mucho
	 * {@value #MAX_DIAS} dias. Los limites absolutos se comprueban primero: con un anio gigantesco el calculo de
	 * {@link #fin} desbordaria.
	 */
	static RangoFechas resolver(LocalDate desde, LocalDate hasta, Clock reloj, ZoneId zona) {
		LimitesFecha.validar(desde);
		LimitesFecha.validar(hasta);
		LocalDate fin = hasta != null ? hasta : reloj.instant().atZone(zona).toLocalDate();
		LocalDate principio = desde != null ? desde : fin.minusDays(DIAS_POR_DEFECTO - 1L);
		if (principio.isAfter(fin)) {
			throw new IllegalArgumentException("La fecha inicial no puede ser posterior a la final");
		}
		if (ChronoUnit.DAYS.between(principio, fin) + 1 > MAX_DIAS) {
			throw new IllegalArgumentException("El rango no puede superar los " + MAX_DIAS + " días");
		}
		return new RangoFechas(principio, fin);
	}

	/** Numero de dias, ambos extremos incluidos. */
	int dias() {
		return (int) ChronoUnit.DAYS.between(desde, hasta) + 1;
	}

	List<LocalDate> fechas() {
		List<LocalDate> fechas = new ArrayList<>(dias());
		for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
			fechas.add(d);
		}
		return fechas;
	}

	/** Principio del primer dia (incluido). */
	Instant inicio(ZoneId zona) {
		return desde.atStartOfDay(zona).toInstant();
	}

	/** Principio del dia siguiente al ultimo (excluido). */
	Instant fin(ZoneId zona) {
		return hasta.plusDays(1).atStartOfDay(zona).toInstant();
	}
}
