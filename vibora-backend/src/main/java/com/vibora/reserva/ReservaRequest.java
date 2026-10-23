package com.vibora.reserva;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** Body de alta de reserva: solo el inicio (ISO-8601); el fin lo fija la duracion configurada. */
public record ReservaRequest(@NotNull Instant inicio) {
}
