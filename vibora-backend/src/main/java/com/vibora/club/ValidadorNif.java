package com.vibora.club;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validacion pura (sin BD) del formato y del digito de control de un identificador fiscal espanol: DNI (8 cifras y
 * letra), NIE (X, Y o Z, 7 cifras y letra) y CIF (letra de entidad, 7 cifras y caracter de control). Solo comprueba que
 * el numero esta bien formado; no que exista.
 */
public final class ValidadorNif {

	private static final String LETRAS_DNI = "TRWAGMYFPDXBNJZSQVHLCKE";
	private static final String LETRAS_CIF = "JABCDEFGHI";
	private static final Pattern DNI = Pattern.compile("(\\d{8})([A-Z])");
	private static final Pattern NIE = Pattern.compile("([XYZ])(\\d{7})([A-Z])");
	private static final Pattern CIF = Pattern.compile("([ABCDEFGHJKLMNPQRSUVW])(\\d{7})([0-9A-J])");
	/** Entidades cuyo control es SIEMPRE una letra / SIEMPRE una cifra; el resto admite cualquiera de las dos. */
	private static final String CONTROL_EN_LETRA = "KPQRSNW";
	private static final String CONTROL_EN_CIFRA = "ABEH";

	private ValidadorNif() {
	}

	/** Sin espacios, guiones ni puntos y en mayusculas ("12.345.678-z" -> "12345678Z"). El nulo da cadena vacia. */
	public static String normalizar(String texto) {
		return texto == null ? "" : texto.replaceAll("[\\s.\\-]", "").toUpperCase(Locale.ROOT);
	}

	public static boolean esValido(String texto) {
		String nif = normalizar(texto);
		if (nif.isEmpty()) {
			return false;
		}
		var dni = DNI.matcher(nif);
		if (dni.matches()) {
			return letraDni(Integer.parseInt(dni.group(1))) == dni.group(2).charAt(0);
		}
		var nie = NIE.matcher(nif);
		if (nie.matches()) {
			int prefijo = "XYZ".indexOf(nie.group(1).charAt(0));
			return letraDni(Integer.parseInt(prefijo + nie.group(2))) == nie.group(3).charAt(0);
		}
		var cif = CIF.matcher(nif);
		return cif.matches() && controlCifCorrecto(cif.group(1).charAt(0), cif.group(2), cif.group(3).charAt(0));
	}

	private static char letraDni(int numero) {
		return LETRAS_DNI.charAt(numero % 23);
	}

	private static boolean controlCifCorrecto(char entidad, String cifras, char control) {
		int suma = 0;
		for (int i = 0; i < cifras.length(); i++) {
			int cifra = cifras.charAt(i) - '0';
			if (i % 2 == 0) { // posiciones impares (1, 3, 5, 7): se doblan y se suman sus cifras
				int doble = cifra * 2;
				suma += doble / 10 + doble % 10;
			} else {
				suma += cifra;
			}
		}
		int digito = (10 - suma % 10) % 10;
		boolean esLetra = control >= 'A' && control <= 'J';
		if (esLetra) {
			return CONTROL_EN_CIFRA.indexOf(entidad) < 0 && LETRAS_CIF.charAt(digito) == control;
		}
		return CONTROL_EN_LETRA.indexOf(entidad) < 0 && digito == control - '0';
	}
}
