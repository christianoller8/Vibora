package com.vibora.pista;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body de alta/modificacion de pista. "activa" solo aplica al PUT (null = no cambiar). */
public record PistaRequest(
		@NotBlank @Size(max = 255) String nombre,
		@NotNull TipoPista tipo,
		Boolean activa) {
}
