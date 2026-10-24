package com.vibora.metricas;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Serie de ingresos sin huecos: un punto por periodo, identificado por su primer dia (las semanas empiezan en lunes). */
public record IngresosResponse(LocalDate desde, LocalDate hasta, Granularidad granularidad, List<Punto> serie) {

	public record Punto(LocalDate periodo, BigDecimal ingresos, long reservas) {
	}
}
