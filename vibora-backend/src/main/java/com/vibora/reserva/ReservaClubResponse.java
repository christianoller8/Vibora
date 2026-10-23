package com.vibora.reserva;

import com.vibora.usuario.Usuario;

import java.math.BigDecimal;
import java.time.Instant;

/** Vista de una reserva para el administrador del club, con un resumen minimo de quien reservo. */
public record ReservaClubResponse(Long id, Long pistaId, String pistaNombre, Instant inicio, Instant fin,
		EstadoReserva estado, BigDecimal precioTotal, UsuarioReserva usuario) {

	public record UsuarioReserva(Long id, String nombre, String apellidos, String email) {

		static UsuarioReserva de(Usuario u) {
			return new UsuarioReserva(u.getId(), u.getNombre(), u.getApellidos(), u.getEmail());
		}
	}

	public static ReservaClubResponse de(Reserva r) {
		return new ReservaClubResponse(r.getId(), r.getPista().getId(), r.getPista().getNombre(), r.getInicio(),
				r.getFin(), r.getEstado(), r.getPrecioTotal(), UsuarioReserva.de(r.getUsuario()));
	}
}
