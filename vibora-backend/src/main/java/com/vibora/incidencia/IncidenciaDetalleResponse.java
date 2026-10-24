package com.vibora.incidencia;

import java.time.Instant;
import java.util.List;

/** Detalle de una incidencia con su historial completo en orden cronologico, sus fotos y su bloqueo activo (si hay). */
public record IncidenciaDetalleResponse(Long id, Long clubId, Long pistaId, String pistaNombre, String titulo,
		String descripcion, CategoriaIncidencia categoria, PrioridadIncidencia prioridad, EstadoIncidencia estado,
		PersonaResponse reportadaPor, PersonaResponse asignadaA, Instant creadaEn, Instant actualizadaEn,
		Instant resueltaEn, List<EventoResponse> eventos, List<FotoResponse> fotos, BloqueoActivoResponse bloqueoActivo) {

	public static IncidenciaDetalleResponse de(Incidencia i, List<IncidenciaEvento> historial, List<IncidenciaFoto> fotos,
			BloqueoActivoResponse bloqueoActivo) {
		return new IncidenciaDetalleResponse(i.getId(), i.getClub().getId(),
				i.getPista() == null ? null : i.getPista().getId(),
				i.getPista() == null ? null : i.getPista().getNombre(), i.getTitulo(), i.getDescripcion(),
				i.getCategoria(), i.getPrioridad(), i.getEstado(), PersonaResponse.de(i.getReportadaPor()),
				PersonaResponse.de(i.getAsignadaA()), i.getCreadaEn(), i.getActualizadaEn(), i.getResueltaEn(),
				historial.stream().map(EventoResponse::de).toList(), fotos.stream().map(FotoResponse::de).toList(),
				bloqueoActivo);
	}
}
