package com.vibora.reserva;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuracion de reservas: vibora.reservas.duracion-minutos y vibora.reservas.cancelacion-minima-horas. */
@ConfigurationProperties(prefix = "vibora.reservas")
public record ReservaProperties(int duracionMinutos, long cancelacionMinimaHoras) {
}
