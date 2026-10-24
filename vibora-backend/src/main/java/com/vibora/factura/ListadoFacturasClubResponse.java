package com.vibora.factura;

import java.math.BigDecimal;
import java.util.List;

/** Una pagina de facturas del club y los totales de TODO el conjunto filtrado (las rectificativas restan). */
public record ListadoFacturasClubResponse(List<FacturaResponse> facturas, Totales totales, int page, int limit) {

	public record Totales(long cantidad, BigDecimal base, BigDecimal cuotaIva, BigDecimal total) {
	}
}
