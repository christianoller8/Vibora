package com.vibora.incidencia;

import java.time.Instant;

/** Fila del listado: sin descripcion ni historial (eso es el detalle). */
public record IncidenciaResumenResponse(Long id, Long clubId, Long pistaId, String pistaNombre, String titulo,
		CategoriaIncidencia categoria, PrioridadIncidencia prioridad, EstadoIncidencia estado,
		PersonaResponse reportadaPor, PersonaResponse asignadaA, Instant creadaEn, Instant actualizadaEn) {

	public static IncidenciaResumenResponse de(Incidencia i) {
		return new IncidenciaResumenResponse(i.getId(), i.getClub().getId(),
				i.getPista() == null ? null : i.getPista().getId(),
				i.getPista() == null ? null : i.getPista().getNombre(), i.getTitulo(), i.getCategoria(),
				i.getPrioridad(), i.getEstado(), PersonaResponse.de(i.getReportadaPor()),
				PersonaResponse.de(i.getAsignadaA()), i.getCreadaEn(), i.getActualizadaEn());
	}
}
