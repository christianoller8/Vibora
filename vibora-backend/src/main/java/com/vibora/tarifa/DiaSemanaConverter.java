package com.vibora.tarifa;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.DayOfWeek;

/** Guarda el dia de la semana como smallint ISO (1 = lunes ... 7 = domingo). */
@Converter
public class DiaSemanaConverter implements AttributeConverter<DayOfWeek, Short> {

	@Override
	public Short convertToDatabaseColumn(DayOfWeek dia) {
		return dia == null ? null : (short) dia.getValue();
	}

	@Override
	public DayOfWeek convertToEntityAttribute(Short valor) {
		return valor == null ? null : DayOfWeek.of(valor);
	}
}
