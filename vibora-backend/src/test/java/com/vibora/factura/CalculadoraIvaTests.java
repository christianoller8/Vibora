package com.vibora.factura;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El total de la reserva ya lleva IVA: base = total / (1 + iva), redondeada a 2 decimales (HALF_UP); cuota = total - base. */
class CalculadoraIvaTests {

	private static CalculadoraIva.Desglose desglosar(String total, String iva) {
		return CalculadoraIva.desglosar(new BigDecimal(total), new BigDecimal(iva));
	}

	@ParameterizedTest(name = "{0} al {1} % -> base {2}, cuota {3}")
	@CsvSource({
			"45.00, 21.00, 37.19, 7.81", // 45 / 1.21 = 37.1900826...
			"10.01, 21.00, 8.27, 1.74", // 8.2727...
			"0.03, 21.00, 0.02, 0.01", // 0.0247... -> 0.02
			"0.01, 21.00, 0.01, 0.00", // 0.00826... -> 0.01 y sin cuota
			"121.00, 21.00, 100.00, 21.00", // exacto
			"110.00, 10.00, 100.00, 10.00",
			"104.00, 4.00, 100.00, 4.00",
			"45.00, 0.00, 45.00, 0.00", // exento: toda la cantidad es base
			"99999999.99, 21.00, 82644628.09, 17355371.90"
	})
	void desglosaElTotalConIvaIncluido(String total, String iva, String base, String cuota) {
		CalculadoraIva.Desglose d = desglosar(total, iva);

		assertThat(d.base()).isEqualByComparingTo(base);
		assertThat(d.cuota()).isEqualByComparingTo(cuota);
		assertThat(d.base().scale()).isEqualTo(2);
		assertThat(d.cuota().scale()).isEqualTo(2);
	}

	@ParameterizedTest(name = "{0} al {1} % redondea {2} (HALF_UP)")
	@CsvSource({
			// base exacta = x.xx5: HALF_UP sube (HALF_EVEN dejaria 0.02 y 0.18)
			"0.03, 20.00, 0.03",
			"0.05, 100.00, 0.03"
	})
	void redondeaLaBaseAlAlzaCuandoQuedaJustoEnMedio(String total, String iva, String baseEsperada) {
		BigDecimal exacta = new BigDecimal(total).divide(BigDecimal.ONE.add(new BigDecimal(iva).movePointLeft(2)), 10,
				RoundingMode.HALF_UP);

		assertThat(desglosar(total, iva).base()).isEqualByComparingTo(baseEsperada);
		assertThat(exacta.setScale(2, RoundingMode.HALF_UP)).isEqualByComparingTo(baseEsperada);
	}

	@Test
	void conUnTipoDe20_unTotalDeTresCentimosDaBase0_03_ynoCero02() {
		// 0.03 / 1.20 = 0.025 exacto: HALF_UP -> 0.03 (con HALF_EVEN saldria 0.02)
		CalculadoraIva.Desglose d = desglosar("0.03", "20.00");

		assertThat(d.base()).isEqualByComparingTo("0.03");
		assertThat(d.cuota()).isEqualByComparingTo("0.00");
	}

	@Test
	void baseMasCuotaSiempreSumanElTotalExacto_yLaCuotaNuncaEsNegativa() {
		String[] tipos = { "0.00", "4.00", "5.00", "7.50", "10.00", "15.50", "21.00", "33.33", "99.99" };
		int comprobados = 0;
		for (String tipo : tipos) {
			BigDecimal iva = new BigDecimal(tipo);
			for (long centimos = 1; centimos <= 20_000; centimos++) { // de 0.01 a 200.00
				BigDecimal total = BigDecimal.valueOf(centimos, 2);
				CalculadoraIva.Desglose d = CalculadoraIva.desglosar(total, iva);

				assertThat(d.base().add(d.cuota())).as("%s al %s", total, tipo).isEqualByComparingTo(total);
				assertThat(d.cuota().signum()).as("cuota de %s al %s", total, tipo).isGreaterThanOrEqualTo(0);
				assertThat(d.base().signum()).isGreaterThanOrEqualTo(0);
				comprobados++;
			}
		}
		assertThat(comprobados).isEqualTo(tipos.length * 20_000);
	}

	@Test
	void unTotalCeroDaBaseYCuotaCero() {
		CalculadoraIva.Desglose d = desglosar("0.00", "21.00");

		assertThat(d.base()).isEqualByComparingTo("0.00");
		assertThat(d.cuota()).isEqualByComparingTo("0.00");
	}

	@Test
	void rechazaTotalesNegativosOTiposFueraDeRango() {
		assertThatThrownBy(() -> desglosar("-1.00", "21.00")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> desglosar("1.00", "-0.01")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> desglosar("1.00", "100.01")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> CalculadoraIva.desglosar(null, new BigDecimal("21"))).isInstanceOf(IllegalArgumentException.class);
	}
}
