package com.vibora.club;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

/** Body de alta/modificacion de club. La regla horaCierre > horaApertura se valida en el servicio. */
public record ClubRequest(
		@NotBlank @Size(max = 255) String nombre,
		@Size(max = 255) String direccion,
		@Size(max = 255) String ciudad,
		@NotNull LocalTime horaApertura,
		@NotNull LocalTime horaCierre) {
}
