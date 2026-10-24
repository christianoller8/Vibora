package com.vibora.incidencia;

import java.util.List;

public enum EstadoIncidencia {
	ABIERTA("Abierta"), ASIGNADA("Asignada"), EN_CURSO("En curso"), RESUELTA("Resuelta"), CERRADA("Cerrada"),
	RECHAZADA("Rechazada");

	/** Estados finales: una incidencia cerrada o rechazada ya no cambia ni admite comentarios. */
	public static final List<EstadoIncidencia> TERMINALES = List.of(CERRADA, RECHAZADA);

	/** Con responsable obligatorio (incidencia_asignada_ck): las que se desasignan al retirar a la persona. */
	public static final List<EstadoIncidencia> ACTIVAS = List.of(ASIGNADA, EN_CURSO);

	private final String etiqueta;

	EstadoIncidencia(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	/** Nombre legible para los textos de avisos. */
	public String etiqueta() {
		return etiqueta;
	}

	public boolean esTerminal() {
		return TERMINALES.contains(this);
	}
}
