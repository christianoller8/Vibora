package com.vibora.incidencia;

import java.time.Instant;

/** Metadatos de una foto. Nunca expone la clave del almacen: los bytes se piden por id a traves de la API. */
public record FotoResponse(Long id, String nombreOriginal, String tipoContenido, int tamano, Instant creadaEn,
		PersonaResponse subidaPor) {

	public static FotoResponse de(IncidenciaFoto f) {
		return new FotoResponse(f.getId(), f.getNombreOriginal(), f.getTipoContenido(), f.getTamano(), f.getCreadaEn(),
				PersonaResponse.de(f.getSubidaPor()));
	}
}
