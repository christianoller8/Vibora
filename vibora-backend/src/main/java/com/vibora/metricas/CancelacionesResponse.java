package com.vibora.metricas;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Cancelaciones por periodo (sin huecos) y por pista, siempre por el dia de JUEGO de la reserva. {@code tasa} =
 * canceladas / (canceladas + confirmadas), nula si el periodo no tiene reservas. El modelo no guarda cuando se cancelo
 * una reserva, asi que no hay antelacion de cancelacion.
 */
public record CancelacionesResponse(LocalDate desde, LocalDate hasta, Granularidad granularidad, List<Punto> serie,
		List<PorPista> porPista) {

	public record Punto(LocalDate periodo, long canceladas, long confirmadas, BigDecimal tasa) {
	}

	public record PorPista(Long pistaId, String pistaNombre, long canceladas, BigDecimal ingresosPerdidos) {
	}
}
