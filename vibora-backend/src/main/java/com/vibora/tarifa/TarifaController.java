package com.vibora.tarifa;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/clubes/{clubId}/tarifas")
@RequiredArgsConstructor
public class TarifaController {

	private final TarifaService tarifaService;

	@GetMapping
	public List<TarifaResponse> listar(@PathVariable Long clubId) {
		return tarifaService.listar(clubId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TarifaResponse crear(@PathVariable Long clubId, @Valid @RequestBody TarifaRequest peticion) {
		return tarifaService.crear(clubId, peticion);
	}

	@PutMapping("/{id}")
	public TarifaResponse actualizar(@PathVariable Long clubId, @PathVariable Long id,
			@Valid @RequestBody TarifaRequest peticion) {
		return tarifaService.actualizar(clubId, id, peticion);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void eliminar(@PathVariable Long clubId, @PathVariable Long id) {
		tarifaService.eliminar(clubId, id);
	}
}
