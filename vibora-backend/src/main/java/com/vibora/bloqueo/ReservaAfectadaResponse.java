package com.vibora.bloqueo;

import com.vibora.incidencia.PersonaResponse;
import com.vibora.reserva.Reserva;
import com.vibora.usuario.Usuario;

import java.time.Instant;
import java.util.List;

/**
 * Reserva confirmada y futura que un bloqueo deja "afectada", para que el administrador decida (avisar, cancelar...).
 * Resumen minimo del organizador (nombre y email, para poder contactarle) y los jugadores por puesto.
 */
public record ReservaAfectadaResponse(Long id, Instant inicio, Instant fin, Organizador organizador,
		List<PersonaResponse> jugadores) {

	public record Organizador(Long id, String nombre, String email) {

		static Organizador de(Usuario u) {
			return new Organizador(u.getId(), u.nombreCompleto(), u.getEmail());
		}
	}

	public static ReservaAfectadaResponse de(Reserva r) {
		return new ReservaAfectadaResponse(r.getId(), r.getInicio(), r.getFin(), Organizador.de(r.getUsuario()),
				r.getParticipantes().stream().map(p -> PersonaResponse.de(p.getUsuario())).toList());
	}
}
