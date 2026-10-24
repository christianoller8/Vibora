package com.vibora.incidencia;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body para reportar una incidencia. No lleva prioridad ni estado: siempre nace MEDIA y ABIERTA. */
public record IncidenciaRequest(
		@NotBlank @Size(max = 120) String titulo,
		@NotBlank @Size(max = 2000) String descripcion,
		@NotNull CategoriaIncidencia categoria,
		Long pistaId) {
}
