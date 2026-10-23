package com.vibora.partido;

import com.vibora.cargo.CalculadoraCuotas;
import com.vibora.reserva.Reserva;
import com.vibora.usuario.Usuario;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Vista publica de un partido abierto. De los jugadores solo se expone id, nombre e inicial del apellido:
 * nunca el email ni el apellido completo.
 */
public record PartidoResponse(Long reservaId, ClubResumen club, String pistaNombre, Instant inicio, Instant fin,
		BigDecimal cuotaPorJugador, int plazasLibres, List<JugadorPartido> jugadores, boolean yaApuntado,
		boolean esOrganizador) {

	public record ClubResumen(Long id, String nombre) {
	}

	public record JugadorPartido(Long usuarioId, String nombre, String inicialApellido) {

		static JugadorPartido de(Usuario u) {
			String apellidos = u.getApellidos() == null ? "" : u.getApellidos().trim();
			String inicial = apellidos.isEmpty() ? ""
					: apellidos.substring(0, apellidos.offsetByCodePoints(0, 1)).toUpperCase();
			return new JugadorPartido(u.getId(), u.getNombre(), inicial);
		}
	}

	/** Requiere pista, club y los usuarios de los jugadores ya cargados. {@code jugadores} ordenados por puesto. */
	public static PartidoResponse de(Reserva r, List<Participante> jugadores, Long usuarioActualId) {
		List<JugadorPartido> vista = jugadores.stream().map(p -> JugadorPartido.de(p.getUsuario())).toList();
		return new PartidoResponse(r.getId(),
				new ClubResumen(r.getPista().getClub().getId(), r.getPista().getClub().getNombre()),
				r.getPista().getNombre(), r.getInicio(), r.getFin(),
				CalculadoraCuotas.cuotaPorJugador(r.getPrecioTotal()),
				Math.max(0, CalculadoraCuotas.JUGADORES - jugadores.size()), vista,
				jugadores.stream().anyMatch(p -> p.getUsuario().getId().equals(usuarioActualId)),
				r.getUsuario().getId().equals(usuarioActualId));
	}
}
