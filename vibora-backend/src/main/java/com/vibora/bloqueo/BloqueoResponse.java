package com.vibora.bloqueo;

import com.vibora.incidencia.PersonaResponse;

import java.time.Instant;

/** Un bloqueo de pista. {@code activo}: sin levantar y sin caducar en el momento de la consulta. */
public record BloqueoResponse(Long id, Long pistaId, String pistaNombre, Long incidenciaId, String motivo, Instant desde,
		Instant hasta, PersonaResponse creadoPor, Instant creadoEn, Instant levantadoEn, PersonaResponse levantadoPor,
		boolean activo) {

	public static BloqueoResponse de(BloqueoPista b, Instant ahora) {
		return new BloqueoResponse(b.getId(), b.getPista().getId(), b.getPista().getNombre(),
				b.getIncidencia() == null ? null : b.getIncidencia().getId(), b.getMotivo(), b.getDesde(), b.getHasta(),
				PersonaResponse.de(b.getCreadoPor()), b.getCreadoEn(), b.getLevantadoEn(),
				PersonaResponse.de(b.getLevantadoPor()), b.estaActivoEn(ahora));
	}
}
