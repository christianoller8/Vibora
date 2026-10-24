package com.vibora.club;

import java.math.BigDecimal;

public record DatosFiscalesResponse(
		String razonSocial,
		String nif,
		String direccionFiscal,
		BigDecimal tipoIva,
		boolean configurado) {

	public static DatosFiscalesResponse de(Club club) {
		return new DatosFiscalesResponse(club.getRazonSocial(), club.getNif(), club.getDireccionFiscal(), club.getTipoIva(),
				club.tieneDatosFiscales());
	}
}
