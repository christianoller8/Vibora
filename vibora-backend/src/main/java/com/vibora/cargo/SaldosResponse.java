package com.vibora.cargo;

import java.math.BigDecimal;
import java.util.List;

/** Saldo neto del usuario con cada contraparte (solo cargos PENDIENTES) y sus totales. neto = meDeben - debo. */
public record SaldosResponse(List<SaldoUsuario> saldos, BigDecimal totalDebo, BigDecimal totalMeDeben,
		BigDecimal totalNeto) {

	public record SaldoUsuario(Long usuarioId, String nombre, String apellidos, BigDecimal debo, BigDecimal meDeben,
			BigDecimal neto) {
	}
}
