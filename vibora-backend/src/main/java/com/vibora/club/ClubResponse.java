package com.vibora.club;

import java.time.LocalTime;

public record ClubResponse(
		Long id,
		String nombre,
		String direccion,
		String ciudad,
		LocalTime horaApertura,
		LocalTime horaCierre) {

	public static ClubResponse de(Club club) {
		return new ClubResponse(club.getId(), club.getNombre(), club.getDireccion(), club.getCiudad(),
				club.getHoraApertura(), club.getHoraCierre());
	}
}
