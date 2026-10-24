package com.vibora.club;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/clubes")
@RequiredArgsConstructor
public class ClubController {

	private final ClubService clubService;

	@GetMapping
	public List<ClubResponse> listar() {
		return clubService.listar();
	}

	@GetMapping("/{id}")
	public ClubResponse obtener(@PathVariable Long id) {
		return clubService.obtener(id);
	}

	/** Un JUGADOR sin club crea el suyo y pasa a ADMIN_CLUB; debe volver a hacer login para el nuevo token. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ClubResponse crear(@Valid @RequestBody ClubRequest peticion) {
		return clubService.crear(peticion);
	}

	@PutMapping("/{id}")
	public ClubResponse actualizar(@PathVariable Long id, @Valid @RequestBody ClubRequest peticion) {
		return clubService.actualizar(id, peticion);
	}

	/** Datos fiscales del club (solo su administrador): lo que sale como emisor en las facturas. */
	@GetMapping("/{id}/datos-fiscales")
	public DatosFiscalesResponse datosFiscales(@PathVariable Long id) {
		return clubService.datosFiscales(id);
	}

	@PutMapping("/{id}/datos-fiscales")
	public DatosFiscalesResponse actualizarDatosFiscales(@PathVariable Long id,
			@Valid @RequestBody DatosFiscalesRequest peticion) {
		return clubService.actualizarDatosFiscales(id, peticion);
	}
}
