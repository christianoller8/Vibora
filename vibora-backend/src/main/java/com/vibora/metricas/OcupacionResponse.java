package com.vibora.metricas;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Ocupacion por pista y global. {@code ocupacion} es una fraccion 0..1 (4 decimales), nula si no se ofrecio ninguna ranura. */
public record OcupacionResponse(LocalDate desde, LocalDate hasta, BigDecimal ocupacionGlobal, long ranurasOfrecidas,
		long ranurasOcupadas, List<PistaOcupacion> pistas) {

	public record PistaOcupacion(Long pistaId, String pistaNombre, long ranurasOfrecidas, long ranurasOcupadas,
			BigDecimal ocupacion, BigDecimal ingresos) {
	}
}
