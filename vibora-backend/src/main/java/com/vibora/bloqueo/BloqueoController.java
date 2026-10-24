package com.vibora.bloqueo;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Bloqueos de una pista por mantenimiento (crear, levantar y cancelar afectadas: solo el administrador del club). */
@RestController
@RequestMapping("/api/clubes/{clubId}/pistas/{pistaId}/bloqueos")
@RequiredArgsConstructor
public class BloqueoController {

	private final BloqueoService bloqueoService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public BloqueoCreadoResponse crear(@PathVariable Long clubId, @PathVariable Long pistaId,
			@Valid @RequestBody BloqueoRequest peticion) {
		return bloqueoService.crear(clubId, pistaId, peticion);
	}

	/** {@code activos}: sin parametro, todos; true, solo los activos; false, solo los levantados o caducados. */
	@GetMapping
	public List<BloqueoResponse> listar(@PathVariable Long clubId, @PathVariable Long pistaId,
			@RequestParam(required = false) Boolean activos) {
		return bloqueoService.listar(clubId, pistaId, activos);
	}

	@GetMapping("/{bloqueoId}/afectadas")
	public List<ReservaAfectadaResponse> afectadas(@PathVariable Long clubId, @PathVariable Long pistaId,
			@PathVariable Long bloqueoId) {
		return bloqueoService.afectadas(clubId, pistaId, bloqueoId);
	}

	@DeleteMapping("/{bloqueoId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void levantar(@PathVariable Long clubId, @PathVariable Long pistaId, @PathVariable Long bloqueoId) {
		bloqueoService.levantar(clubId, pistaId, bloqueoId);
	}

	@PostMapping("/{bloqueoId}/cancelar-afectadas")
	public CancelacionAfectadasResponse cancelarAfectadas(@PathVariable Long clubId, @PathVariable Long pistaId,
			@PathVariable Long bloqueoId) {
		return bloqueoService.cancelarAfectadas(clubId, pistaId, bloqueoId);
	}
}
