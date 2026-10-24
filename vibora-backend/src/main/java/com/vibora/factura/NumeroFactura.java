package com.vibora.factura;

/** Formato de la numeracion: serie "F2026" (una por anio) y numero completo "F2026-0001". */
public final class NumeroFactura {

	private NumeroFactura() {
	}

	public static String serie(int anio) {
		return "F" + anio;
	}

	public static String completo(String serie, int numero) {
		return "%s-%04d".formatted(serie, numero);
	}
}
