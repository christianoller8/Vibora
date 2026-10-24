package com.vibora.reserva;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Una casilla de la agenda de una pista. Nunca incluye quien la reservo. precio es null si ninguna tarifa la cubre.
 * {@code bloqueada}: la casilla solapa un bloqueo de mantenimiento activo (y entonces {@code disponible} es false).
 */
public record CasillaAgenda(Instant inicio, Instant fin, boolean disponible, BigDecimal precio, boolean bloqueada) {
}
