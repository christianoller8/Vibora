package com.vibora.tarifa;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record TarifaResponse(Long id, Long clubId, DayOfWeek diaSemana, LocalTime horaInicio, LocalTime horaFin,
		BigDecimal precioHora, TipoTarifa tipo) {

	public static TarifaResponse de(TarifaFranja t) {
		return new TarifaResponse(t.getId(), t.getClub().getId(), t.getDiaSemana(), t.getHoraInicio(),
				t.getHoraFin(), t.getPrecioHora(), t.getTipo());
	}
}
