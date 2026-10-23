package com.vibora.cargo;

import com.vibora.usuario.Usuario;

import java.math.BigDecimal;
import java.time.Instant;

/** Un cargo para quien lo consulta. Solo datos publicos de las partes (nunca email). */
public record CargoResponse(Long id, Long reservaId, String clubNombre, String pistaNombre, Instant inicio,
		BigDecimal importe, EstadoCargo estado, UsuarioCargo deudor, UsuarioCargo acreedor, Instant pagadoEn) {

	public record UsuarioCargo(Long id, String nombre, String apellidos) {

		static UsuarioCargo de(Usuario u) {
			return new UsuarioCargo(u.getId(), u.getNombre(), u.getApellidos());
		}
	}

	/** pagadoEn: cuando se pago (null si nunca se pago). Requiere reserva, pista, club y partes ya cargados. */
	public static CargoResponse de(Cargo c, Instant pagadoEn) {
		return new CargoResponse(c.getId(), c.getReserva().getId(), c.getReserva().getPista().getClub().getNombre(),
				c.getReserva().getPista().getNombre(), c.getReserva().getInicio(), c.getImporte(), c.getEstado(),
				UsuarioCargo.de(c.getDeudor()), UsuarioCargo.de(c.getAcreedor()), pagadoEn);
	}
}
