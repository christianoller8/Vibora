package com.vibora.auth;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

/**
 * Reglas sobre la contraseña en claro. Se normaliza a NFC (la misma contraseña tecleada como "ñ" o como "n" + tilde
 * combinada produce el mismo hash en cualquier dispositivo) y se limita a 72 BYTES UTF-8: BCrypt lanza excepcion al
 * codificar mas de eso y, peor, al comparar trunca en silencio y daria por buena una contraseña con la cola distinta.
 */
public final class PoliticaPassword {

	public static final int MAX_BYTES = 72;

	private PoliticaPassword() {
	}

	public static String normalizar(String password) {
		return Normalizer.normalize(password, Normalizer.Form.NFC);
	}

	/** Bytes UTF-8 de la contraseña ya normalizada (la forma que se hashea). */
	public static int bytes(String password) {
		return normalizar(password).getBytes(StandardCharsets.UTF_8).length;
	}

	public static boolean excedeLimite(String password) {
		return bytes(password) > MAX_BYTES;
	}
}
