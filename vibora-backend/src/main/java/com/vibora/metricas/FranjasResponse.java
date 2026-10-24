package com.vibora.metricas;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * Rentabilidad por franja semanal (dia de la semana + hora local de inicio). {@code masRentables}: las {@code limit}
 * franjas con reservas, por ingresos y luego reservas (de mayor a menor); {@code matriz}: todas las franjas en las que
 * hubo alguna ranura ofrecida, por dia de la semana y hora.
 */
public record FranjasResponse(LocalDate desde, LocalDate hasta, int limit, List<Franja> masRentables, List<Franja> matriz) {

	public record Franja(DayOfWeek diaSemana, String hora, long reservas, BigDecimal ingresos, long ranurasOfrecidas,
			BigDecimal ocupacion) {
	}
}
