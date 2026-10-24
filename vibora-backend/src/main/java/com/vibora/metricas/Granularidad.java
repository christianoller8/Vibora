package com.vibora.metricas;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Tamano de los periodos de una serie temporal. Todo se calcula sobre fechas LOCALES, nunca sobre instantes: un domingo
 * de cambio de hora sigue siendo un unico dia. Cada periodo se identifica por su primer dia (las semanas empiezan en
 * lunes), que puede ser anterior al principio del rango: el periodo solo recoge los dias del rango.
 */
public enum Granularidad {
	DIA, SEMANA, MES;

	/** Primer dia del periodo que contiene la fecha. */
	LocalDate inicioDe(LocalDate fecha) {
		return switch (this) {
			case DIA -> fecha;
			case SEMANA -> fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
			case MES -> fecha.withDayOfMonth(1);
		};
	}

	/** Primer dia del periodo siguiente al que empieza en {@code inicioDePeriodo}. */
	LocalDate siguiente(LocalDate inicioDePeriodo) {
		return switch (this) {
			case DIA -> inicioDePeriodo.plusDays(1);
			case SEMANA -> inicioDePeriodo.plusWeeks(1);
			case MES -> inicioDePeriodo.plusMonths(1);
		};
	}

	/** Todos los periodos que tocan el rango, sin huecos, en orden. */
	List<LocalDate> periodos(RangoFechas rango) {
		List<LocalDate> periodos = new ArrayList<>();
		for (LocalDate p = inicioDe(rango.desde()); !p.isAfter(rango.hasta()); p = siguiente(p)) {
			periodos.add(p);
		}
		return periodos;
	}
}
