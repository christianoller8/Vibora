package com.vibora.bloqueo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Datos para bloquear una pista. {@code desde} por defecto es ahora; {@code hasta} nulo = sin fin;
 * {@code incidenciaId} es opcional (la incidencia que lo motiva).
 */
public record BloqueoRequest(@NotBlank @Size(max = 300) String motivo, Instant desde, Instant hasta, Long incidenciaId) {
}
