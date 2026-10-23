package com.vibora.cargo;

import java.math.BigDecimal;

/**
 * Reparto de lo que cuesta la pista entre 4 jugadores. Clase pura: trabaja en CENTIMOS enteros para que 3 cuotas
 * mas la parte del organizador sumen SIEMPRE exactamente el total.
 * <ul>
 * <li>cuota de cada jugador que se apunta = floor(total / 4)</li>
 * <li>el organizador (que paga al club) asume cuota + el resto de centimos (0..3), es decir total - 3 * cuota</li>
 * </ul>
 */
public final class CalculadoraCuotas {

	public static final int JUGADORES = 4;
	/** Cuantos jugadores distintos del organizador pueden tener un cargo. */
	public static final int CARGADOS = JUGADORES - 1;

	private CalculadoraCuotas() {
	}

	/** Lo que paga cada jugador que se apunta (escala 2). */
	public static BigDecimal cuotaPorJugador(BigDecimal total) {
		return aEuros(cuotaEnCentimos(centimos(total)));
	}

	/** Lo que asume el organizador: cuota + resto de centimos (escala 2). */
	public static BigDecimal parteOrganizador(BigDecimal total) {
		long centimos = centimos(total);
		return aEuros(centimos - CARGADOS * cuotaEnCentimos(centimos));
	}

	private static long cuotaEnCentimos(long totalCentimos) {
		return totalCentimos / JUGADORES; // el total nunca es negativo: la division entera es floor
	}

	private static long centimos(BigDecimal total) {
		if (total == null) {
			throw new IllegalArgumentException("El total no puede ser nulo");
		}
		if (total.signum() < 0) {
			throw new IllegalArgumentException("El total no puede ser negativo");
		}
		try {
			return total.movePointRight(2).longValueExact();
		} catch (ArithmeticException ex) {
			throw new IllegalArgumentException("El total no puede tener mas de dos decimales");
		}
	}

	private static BigDecimal aEuros(long centimos) {
		return BigDecimal.valueOf(centimos, 2);
	}
}
