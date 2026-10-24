package com.vibora.incidencia;

import java.time.Instant;

public record EventoResponse(Long id, TipoEventoIncidencia tipo, PersonaResponse usuario, String estadoAnterior,
		String estadoNuevo, String texto, Instant creadoEn) {

	public static EventoResponse de(IncidenciaEvento e) {
		return new EventoResponse(e.getId(), e.getTipo(), PersonaResponse.de(e.getUsuario()), e.getEstadoAnterior(),
				e.getEstadoNuevo(), e.getTexto(), e.getCreadoEn());
	}
}
