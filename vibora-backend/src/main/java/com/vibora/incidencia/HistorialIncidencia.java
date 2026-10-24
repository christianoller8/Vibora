package com.vibora.incidencia;

import com.vibora.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

/** Unico punto por el que se anaden entradas al historial de una incidencia (fotos, bloqueos, estados...). */
@Component
@RequiredArgsConstructor
public class HistorialIncidencia {

	private final IncidenciaEventoRepository eventos;
	private final Clock reloj;

	public IncidenciaEvento registrar(Incidencia incidencia, Usuario autor, TipoEventoIncidencia tipo, String anterior,
			String nuevo, String texto) {
		IncidenciaEvento evento = new IncidenciaEvento();
		evento.setIncidencia(incidencia);
		evento.setUsuario(autor);
		evento.setTipo(tipo);
		evento.setEstadoAnterior(anterior);
		evento.setEstadoNuevo(nuevo);
		evento.setTexto(texto);
		evento.setCreadoEn(reloj.instant().truncatedTo(ChronoUnit.MICROS)); // la precision de la BD
		return eventos.save(evento);
	}
}
