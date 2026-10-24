package com.vibora.incidencia;

import com.vibora.bloqueo.BloqueoPista;

import java.time.Instant;

/** Bloqueo de pista activo ligado a una incidencia, tal como se ve en su detalle ({@code hasta} nulo = sin fin). */
public record BloqueoActivoResponse(Long id, Instant desde, Instant hasta, String motivo) {

	public static BloqueoActivoResponse de(BloqueoPista b) {
		return b == null ? null : new BloqueoActivoResponse(b.getId(), b.getDesde(), b.getHasta(), b.getMotivo());
	}
}
