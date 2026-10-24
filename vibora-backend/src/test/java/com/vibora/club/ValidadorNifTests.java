package com.vibora.club;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Muestras con la letra o el caracter de control calculados a mano (algoritmos oficiales de la AEAT). */
class ValidadorNifTests {

	@ParameterizedTest
	@ValueSource(strings = {
			"12345678Z", // DNI: 12345678 mod 23 = 14 -> Z
			"00000000T", // 0 mod 23 = 0 -> T
			"99999999R", // 99999999 mod 23 = 1 -> R
			"X1234567L", // NIE X -> 0: 01234567 mod 23 = 19 -> L
			"Y1234567X", // NIE Y -> 1: 11234567 mod 23 = 10 -> X
			"Z1234567R", // NIE Z -> 2: 21234567 mod 23 = 1 -> R
			"A58818501", // CIF con control numerico
			"B12345674",
			"Q2826000H", // CIF de organismo publico: el control es una letra
			"P2800000H"
	})
	void aceptaLosDocumentosValidos(String nif) {
		assertThat(ValidadorNif.esValido(nif)).as(nif).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"12345678A", // letra equivocada
			"12345679Z", // numero equivocado
			"1234567Z", // faltan cifras
			"123456789", // sin letra
			"X1234567A", // NIE con letra equivocada
			"X123456L", // NIE corto
			"A58818502", // CIF: control numerico equivocado
			"A5881850A", // CIF de tipo A exige control numerico
			"Q28260008", // CIF de tipo Q exige control en letra
			"Q2826000A", // letra de control equivocada
			"I12345674", // la I no es letra inicial de CIF
			"B1234567", // CIF corto
			"B123456745", // largo
			"ABCDEFGHI",
			"12345678ZZ",
			"1234 5678 ZZ",
			"<script>1"
	})
	void rechazaLosDocumentosMalformadosOConControlEquivocado(String nif) {
		assertThat(ValidadorNif.esValido(nif)).as(nif).isFalse();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { " ", "   \t" })
	void nuloOVacioNoSonValidos(String nif) {
		assertThat(ValidadorNif.esValido(nif)).isFalse();
	}

	@Test
	void normalizarQuitaEspaciosGuionesYPuntos_ypasaAMayusculas() {
		assertThat(ValidadorNif.normalizar(" 12.345.678-z ")).isEqualTo("12345678Z");
		assertThat(ValidadorNif.normalizar("x-1234567 l")).isEqualTo("X1234567L");
		assertThat(ValidadorNif.normalizar("b 1234567 4")).isEqualTo("B12345674");
	}

	@Test
	void esValidoAceptaLaFormaSinNormalizar() {
		assertThat(ValidadorNif.esValido(" 12.345.678-z ")).isTrue();
		assertThat(ValidadorNif.esValido("b-12345674")).isTrue();
	}

	@Test
	void todaslasLetrasDeControlDelDniSeComprueban() {
		String letras = "TRWAGMYFPDXBNJZSQVHLCKE";
		for (int resto = 0; resto < 23; resto++) {
			String numero = "%08d".formatted(resto); // resto = numero mod 23 mientras numero < 23
			assertThat(ValidadorNif.esValido(numero + letras.charAt(resto))).as(numero).isTrue();
			assertThat(ValidadorNif.esValido(numero + letras.charAt((resto + 1) % 23))).as(numero + " mal").isFalse();
		}
	}
}
