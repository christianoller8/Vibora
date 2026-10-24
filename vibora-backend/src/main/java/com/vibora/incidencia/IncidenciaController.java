package com.vibora.incidencia;

import com.vibora.bloqueo.BloqueoCreadoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class IncidenciaController {

	private final IncidenciaService incidenciaService;

	@PostMapping("/api/clubes/{clubId}/incidencias")
	@ResponseStatus(HttpStatus.CREATED)
	public IncidenciaDetalleResponse crear(@PathVariable Long clubId, @Valid @RequestBody IncidenciaRequest peticion) {
		return incidenciaService.crear(clubId, peticion);
	}

	@GetMapping("/api/clubes/{clubId}/incidencias")
	public List<IncidenciaResumenResponse> listar(@PathVariable Long clubId,
			@RequestParam(required = false) EstadoIncidencia estado,
			@RequestParam(required = false) Long pistaId,
			@RequestParam(required = false) Long asignadaA,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "" + IncidenciaService.LIMITE_POR_DEFECTO) int limit) {
		return incidenciaService.listar(clubId, estado, pistaId, asignadaA, page, limit);
	}

	@GetMapping("/api/incidencias/{id}")
	public IncidenciaDetalleResponse obtener(@PathVariable Long id) {
		return incidenciaService.obtener(id);
	}

	@PostMapping("/api/incidencias/{id}/asignar")
	public IncidenciaDetalleResponse asignar(@PathVariable Long id, @Valid @RequestBody AsignarRequest peticion) {
		return incidenciaService.asignar(id, peticion);
	}

	@PostMapping("/api/incidencias/{id}/estado")
	public IncidenciaDetalleResponse cambiarEstado(@PathVariable Long id,
			@Valid @RequestBody CambioEstadoRequest peticion) {
		return incidenciaService.cambiarEstado(id, peticion);
	}

	/** Atajo del administrador: bloquea la pista de la incidencia hasta que se resuelva, cierre o rechace. */
	@PostMapping("/api/incidencias/{id}/bloquear-pista")
	@ResponseStatus(HttpStatus.CREATED)
	public BloqueoCreadoResponse bloquearPista(@PathVariable Long id) {
		return incidenciaService.bloquearPista(id);
	}

	@PostMapping("/api/incidencias/{id}/comentarios")
	@ResponseStatus(HttpStatus.CREATED)
	public IncidenciaDetalleResponse comentar(@PathVariable Long id, @Valid @RequestBody ComentarioRequest peticion) {
		return incidenciaService.comentar(id, peticion);
	}
}
