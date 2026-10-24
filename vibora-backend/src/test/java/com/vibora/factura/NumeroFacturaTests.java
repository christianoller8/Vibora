package com.vibora.factura;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NumeroFacturaTests {

	@Test
	void laSerieEsUnaEfeMasElAnio() {
		assertThat(NumeroFactura.serie(2026)).isEqualTo("F2026");
		assertThat(NumeroFactura.serie(2031)).isEqualTo("F2031");
	}

	@Test
	void elNumeroCompletoRellenaConCerosHastaCuatroCifras() {
		assertThat(NumeroFactura.completo("F2026", 1)).isEqualTo("F2026-0001");
		assertThat(NumeroFactura.completo("F2026", 42)).isEqualTo("F2026-0042");
		assertThat(NumeroFactura.completo("F2030", 9999)).isEqualTo("F2030-9999");
	}

	@Test
	void conMasDeCuatroCifrasNoSeTrunca() {
		assertThat(NumeroFactura.completo("F2026", 10000)).isEqualTo("F2026-10000");
		assertThat(NumeroFactura.completo("F2026", 123456)).isEqualTo("F2026-123456");
	}
}
