package com.vibora.partido;

import com.vibora.reserva.ReservaResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class PartidoController {

	private final PartidoService partidoService;

	@GetMapping("/api/partidos")
	public List<PartidoResponse> listar(@RequestParam(required = false) Long clubId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
		return partidoService.listar(clubId, fecha);
	}

	@PutMapping("/api/reservas/{id}/abierta")
	public ReservaResponse cambiarAbierta(@PathVariable Long id, @Valid @RequestBody AbiertaRequest peticion) {
		return partidoService.cambiarAbierta(id, peticion.abierta());
	}

	@PostMapping("/api/reservas/{id}/apuntarse")
	@ResponseStatus(HttpStatus.CREATED)
	public PartidoResponse apuntarse(@PathVariable Long id) {
		return partidoService.apuntarse(id);
	}

	@PostMapping("/api/reservas/{id}/salir")
	public PartidoResponse salir(@PathVariable Long id) {
		return partidoService.salir(id);
	}
}
