package com.vibora.incidencia;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body para cambiar el estado; el comentario es obligatorio solo al rechazar. */
public record CambioEstadoRequest(@NotNull EstadoIncidencia estado, @Size(max = 2000) String comentario) {
}
