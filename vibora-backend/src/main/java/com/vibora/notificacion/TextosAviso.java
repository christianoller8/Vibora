package com.vibora.notificacion;

import com.vibora.incidencia.Incidencia;
import com.vibora.reserva.Reserva;
import com.vibora.usuario.Rol;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Trozos de texto en español reutilizados por los mensajes de las notificaciones. */
@Component
@RequiredArgsConstructor
public class TextosAviso {

	private static final Locale ES = Locale.of("es", "ES");
	private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm");

	private final ZoneId zona;

	/** "Pista 1 de Club A el 07/01/2030 a las 11:00" (hora local del club). */
	public String describir(Reserva reserva) {
		return reserva.getPista().getNombre() + " de " + reserva.getPista().getClub().getNombre() + " el "
				+ FECHA_HORA.format(reserva.getInicio().atZone(zona));
	}

	/** "«Red rota» (Pista 1 de Club A)", o "«Red rota» (Club A)" si no es de una pista concreta. */
	public String describir(Incidencia incidencia) {
		String lugar = incidencia.getPista() == null
				? incidencia.getClub().getNombre()
				: incidencia.getPista().getNombre() + " de " + incidencia.getClub().getNombre();
		return "«" + incidencia.getTitulo() + "» (" + lugar + ")";
	}

	/** "monitor" / "personal de mantenimiento" / "administrador" para frases como "como monitor". */
	public static String describir(Rol rol) {
		return switch (rol) {
			case MONITOR -> "monitor";
			case MANTENIMIENTO -> "personal de mantenimiento";
			case ADMIN_CLUB -> "administrador";
			case JUGADOR -> "jugador";
		};
	}

	/** "Se ha emitido la factura rectificativa F2030-0002 (-45,00 €) que anula la F2030-0001: Pista 1 de Club A el 07/01/2030 a las 11:00" */
	public String rectificativa(String numeroRectificativa, String numeroOriginal, BigDecimal total, Reserva reserva) {
		return "Se ha emitido la factura rectificativa %s (%s) que anula la factura %s: %s".formatted(numeroRectificativa,
				euros(total), numeroOriginal, describir(reserva));
	}

	/** "11,25 €" */
	public static String euros(BigDecimal importe) {
		return String.format(ES, "%.2f €", importe);
	}
}
