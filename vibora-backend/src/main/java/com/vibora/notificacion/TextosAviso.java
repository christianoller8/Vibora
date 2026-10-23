package com.vibora.notificacion;

import com.vibora.reserva.Reserva;
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

	/** "11,25 €" */
	public static String euros(BigDecimal importe) {
		return String.format(ES, "%.2f €", importe);
	}
}
