package com.vibora.tarifa;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Body de alta/modificacion de franja tarifaria. diaSemana es el nombre del enum java.time.DayOfWeek
 * (MONDAY ... SUNDAY). La regla horaFin > horaInicio y el no solape se validan en el servicio.
 */
public record TarifaRequest(
		@NotNull DayOfWeek diaSemana,
		@NotNull LocalTime horaInicio,
		@NotNull LocalTime horaFin,
		@NotNull @DecimalMin("0.00") @Digits(integer = 6, fraction = 2) BigDecimal precioHora,
		@NotNull TipoTarifa tipo) {
}
