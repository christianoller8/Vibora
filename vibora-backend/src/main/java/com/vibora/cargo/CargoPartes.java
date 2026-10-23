package com.vibora.cargo;

/** Identificadores de un cargo: su reserva y las dos partes (sin cargar entidades). */
public record CargoPartes(Long reservaId, Long deudorId, Long acreedorId) {
}
