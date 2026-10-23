package com.vibora.cargo;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculadoraCuotasTests {

	private static BigDecimal d(String v) {
		return new BigDecimal(v);
	}

	@Test
	void reparteExactoEntreCuatro() {
		assertThat(CalculadoraCuotas.cuotaPorJugador(d("45.00"))).isEqualByComparingTo("11.25");
		assertThat(CalculadoraCuotas.parteOrganizador(d("45.00"))).isEqualByComparingTo("11.25");
		assertThat(CalculadoraCuotas.cuotaPorJugador(d("50.00"))).isEqualByComparingTo("12.50");
		assertThat(CalculadoraCuotas.parteOrganizador(d("50.00"))).isEqualByComparingTo("12.50");
	}

	@Test
	void elRestoDeCentimosLoAsumeElOrganizador() {
		// 10.01 -> 1001 cts: 250 cada jugador (floor), el organizador 251
		assertThat(CalculadoraCuotas.cuotaPorJugador(d("10.01"))).isEqualByComparingTo("2.50");
		assertThat(CalculadoraCuotas.parteOrganizador(d("10.01"))).isEqualByComparingTo("2.51");
		// 10.03 -> 1003 cts: 250 y el organizador 253
		assertThat(CalculadoraCuotas.cuotaPorJugador(d("10.03"))).isEqualByComparingTo("2.50");
		assertThat(CalculadoraCuotas.parteOrganizador(d("10.03"))).isEqualByComparingTo("2.53");
	}

	@Test
	void devuelveSiempreEscalaDos() {
		assertThat(CalculadoraCuotas.cuotaPorJugador(d("45")).scale()).isEqualTo(2);
		assertThat(CalculadoraCuotas.parteOrganizador(d("45.0")).scale()).isEqualTo(2);
	}

	@Test
	void totalesBajosOCero() {
		assertThat(CalculadoraCuotas.cuotaPorJugador(d("0.00"))).isEqualByComparingTo("0.00");
		assertThat(CalculadoraCuotas.parteOrganizador(d("0.00"))).isEqualByComparingTo("0.00");
		// 3 centimos: nadie de los otros tres paga nada y el organizador asume todo
		assertThat(CalculadoraCuotas.cuotaPorJugador(d("0.03"))).isEqualByComparingTo("0.00");
		assertThat(CalculadoraCuotas.parteOrganizador(d("0.03"))).isEqualByComparingTo("0.03");
		assertThat(CalculadoraCuotas.cuotaPorJugador(d("0.04"))).isEqualByComparingTo("0.01");
	}

	@Test
	void tresCuotasMasParteDelOrganizadorSumanExactamenteElTotal() {
		for (long centimos = 0; centimos <= 100_000; centimos++) {
			BigDecimal total = BigDecimal.valueOf(centimos, 2);
			BigDecimal cuota = CalculadoraCuotas.cuotaPorJugador(total);
			BigDecimal organizador = CalculadoraCuotas.parteOrganizador(total);
			assertThat(cuota.multiply(BigDecimal.valueOf(3)).add(organizador))
					.as("total %s", total).isEqualByComparingTo(total);
			assertThat(organizador).as("organizador %s", total).isGreaterThanOrEqualTo(cuota);
			assertThat(organizador.subtract(cuota)).as("resto %s", total).isLessThan(d("0.04"));
		}
	}

	@Test
	void rechazaTotalesNegativosONulos() {
		assertThatThrownBy(() -> CalculadoraCuotas.cuotaPorJugador(d("-1.00"))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> CalculadoraCuotas.cuotaPorJugador(null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rechazaTotalesConMasDeDosDecimales() {
		assertThatThrownBy(() -> CalculadoraCuotas.cuotaPorJugador(d("10.005"))).isInstanceOf(IllegalArgumentException.class);
	}
}
