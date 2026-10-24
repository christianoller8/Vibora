package com.vibora.metricas;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Resumen del club en el rango. Los porcentajes (tasa y ocupacion) son fracciones 0..1 con 4 decimales; el dinero lleva
 * 2. Son nulos cuando no se pueden calcular (ninguna reserva, ninguna ranura ofrecida).
 */
public record ResumenMetricasResponse(LocalDate desde, LocalDate hasta, BigDecimal ingresos, long reservasConfirmadas,
		long reservasCanceladas, BigDecimal tasaCancelacion, BigDecimal ticketMedio, BigDecimal ingresosPerdidosPorCancelacion,
		BigDecimal ocupacionGlobal, Facturado facturado) {

	/** Facturas EMITIDAS en el rango (por el dia local de emision), netas de rectificativas. */
	public record Facturado(BigDecimal base, BigDecimal cuotaIva, BigDecimal total, long numeroFacturas,
			long numeroRectificativas) {
	}
}
