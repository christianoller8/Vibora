package com.vibora.pista;

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
@RequestMapping("/api/clubes/{clubId}/pistas")
@RequiredArgsConstructor
public class PistaController {

	private final PistaService pistaService;

	@GetMapping
	public List<PistaResponse> listar(@PathVariable Long clubId) {
		return pistaService.listar(clubId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PistaResponse crear(@PathVariable Long clubId, @Valid @RequestBody PistaRequest peticion) {
		return pistaService.crear(clubId, peticion);
	}

	@PutMapping("/{id}")
	public PistaResponse actualizar(@PathVariable Long clubId, @PathVariable Long id,
			@Valid @RequestBody PistaRequest peticion) {
		return pistaService.actualizar(clubId, id, peticion);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void eliminar(@PathVariable Long clubId, @PathVariable Long id) {
		pistaService.desactivar(clubId, id);
	}
}
