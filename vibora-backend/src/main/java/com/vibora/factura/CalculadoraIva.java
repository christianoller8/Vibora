package com.vibora.factura;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Desglose de un total con IVA INCLUIDO (el precio de la reserva ya lo lleva): {@code base = total / (1 + iva/100)}
 * redondeada a 2 decimales con HALF_UP y {@code cuota = total - base}. La cuota se obtiene RESTANDO (no se redondea por
 * su cuenta) para que base + cuota sea siempre exactamente el total, sin descuadres de un centimo.
 */
public final class CalculadoraIva {

	private static final BigDecimal CIEN = BigDecimal.valueOf(100);

	/** Base imponible y cuota de IVA, ambas con 2 decimales. */
	public record Desglose(BigDecimal base, BigDecimal cuota) {
	}

	private CalculadoraIva() {
	}

	public static Desglose desglosar(BigDecimal total, BigDecimal tipoIva) {
		if (total == null || tipoIva == null) {
			throw new IllegalArgumentException("Faltan el total o el tipo de IVA");
		}
		if (total.signum() < 0) {
			throw new IllegalArgumentException("El total no puede ser negativo");
		}
		if (tipoIva.signum() < 0 || tipoIva.compareTo(CIEN) > 0) {
			throw new IllegalArgumentException("El tipo de IVA debe estar entre 0 y 100");
		}
		BigDecimal exacto;
		try {
			exacto = total.setScale(2, RoundingMode.UNNECESSARY);
		} catch (ArithmeticException ex) {
			throw new IllegalArgumentException("El total no puede tener mas de 2 decimales");
		}
		// total / (1 + iva/100) == total * 100 / (100 + iva)
		BigDecimal base = exacto.multiply(CIEN).divide(CIEN.add(tipoIva), 2, RoundingMode.HALF_UP);
		return new Desglose(base, exacto.subtract(base));
	}
}
