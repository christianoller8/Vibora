package com.vibora.factura;

import java.math.BigDecimal;
import java.time.Instant;

/** Factura tal como sale por la API. Nunca lleva el email de nadie: solo los datos copiados en la factura. */
public record FacturaResponse(
		Long id,
		String serie,
		int numero,
		String numeroCompleto,
		TipoFactura tipo,
		Long rectificaA,
		Long reservaId,
		Instant emitidaEn,
		Emisor emisor,
		Receptor receptor,
		String concepto,
		BigDecimal base,
		BigDecimal tipoIva,
		BigDecimal cuotaIva,
		BigDecimal total) {

	public record Emisor(String razonSocial, String nif, String direccion) {
	}

	public record Receptor(String nombre, String nif, String direccion) {
	}

	public static FacturaResponse de(Factura f) {
		return new FacturaResponse(f.getId(), f.getSerie(), f.getNumero(), f.numeroCompleto(), f.getTipo(),
				f.getRectificaA() == null ? null : f.getRectificaA().getId(), f.getReserva().getId(), f.getEmitidaEn(),
				new Emisor(f.getEmisorRazonSocial(), f.getEmisorNif(), f.getEmisorDireccion()),
				new Receptor(f.getReceptorNombre(), f.getReceptorNif(), f.getReceptorDireccion()), f.getConcepto(),
				f.getBase(), f.getTipoIva(), f.getCuotaIva(), f.getTotal());
	}
}
