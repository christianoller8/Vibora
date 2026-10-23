package com.vibora.reserva;

import com.vibora.cargo.CalculadoraCuotas;

import java.math.BigDecimal;
import java.time.Instant;

public record ReservaResponse(Long id, Long pistaId, String pistaNombre, Long clubId, String clubNombre,
		Instant inicio, Instant fin, EstadoReserva estado, BigDecimal precioTotal, boolean abierta, int jugadores,
		BigDecimal cuotaPorJugador) {

	public static ReservaResponse de(Reserva r) {
		return new ReservaResponse(r.getId(), r.getPista().getId(), r.getPista().getNombre(),
				r.getPista().getClub().getId(), r.getPista().getClub().getNombre(), r.getInicio(), r.getFin(),
				r.getEstado(), r.getPrecioTotal(), r.isAbierta(), r.getParticipantes().size(),
				CalculadoraCuotas.cuotaPorJugador(r.getPrecioTotal()));
	}
}
