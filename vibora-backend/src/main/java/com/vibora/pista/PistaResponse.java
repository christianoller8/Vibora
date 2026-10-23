package com.vibora.pista;

public record PistaResponse(Long id, String nombre, TipoPista tipo, boolean activa, Long clubId) {

	public static PistaResponse de(Pista pista) {
		return new PistaResponse(pista.getId(), pista.getNombre(), pista.getTipo(), pista.isActiva(),
				pista.getClub().getId());
	}
}
