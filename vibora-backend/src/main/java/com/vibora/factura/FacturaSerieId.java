package com.vibora.factura;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;

/** Clave de un contador de facturas: un contador por club y anio. */
@Embeddable
public record FacturaSerieId(
		@Column(name = "club_id") Long clubId,
		@Column(name = "anio") Short anio) implements Serializable {
}
