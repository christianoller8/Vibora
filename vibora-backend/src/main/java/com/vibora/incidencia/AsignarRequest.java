package com.vibora.incidencia;

import jakarta.validation.constraints.NotNull;

/** Body para asignar una incidencia; {@code prioridad} es opcional (solo el administrador la cambia, aqui). */
public record AsignarRequest(@NotNull Long usuarioId, PrioridadIncidencia prioridad) {
}
