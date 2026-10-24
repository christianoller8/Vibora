package com.vibora.metricas;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Metricas del club para su panel (solo administrador del club). {@code desde} y {@code hasta} son fechas locales
 * (yyyy-MM-dd) incluidas; por defecto, los ultimos 30 dias terminando hoy.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clubes/{clubId}/metricas")
public class MetricasController {

	private final MetricasService metricas;

	@GetMapping("/resumen")
	public ResumenMetricasResponse resumen(@PathVariable Long clubId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
		return metricas.resumen(clubId, desde, hasta);
	}

	@GetMapping("/ingresos")
	public IngresosResponse ingresos(@PathVariable Long clubId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
			@RequestParam(defaultValue = "DIA") Granularidad granularidad) {
		return metricas.ingresos(clubId, desde, hasta, granularidad);
	}

	@GetMapping("/ocupacion")
	public OcupacionResponse ocupacion(@PathVariable Long clubId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
		return metricas.ocupacion(clubId, desde, hasta);
	}

	@GetMapping("/franjas")
	public FranjasResponse franjas(@PathVariable Long clubId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
			@RequestParam(defaultValue = "" + MetricasService.LIMITE_POR_DEFECTO) int limit) {
		return metricas.franjas(clubId, desde, hasta, limit);
	}

	@GetMapping("/cancelaciones")
	public CancelacionesResponse cancelaciones(@PathVariable Long clubId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
			@RequestParam(defaultValue = "DIA") Granularidad granularidad) {
		return metricas.cancelaciones(clubId, desde, hasta, granularidad);
	}
}
