package com.vibora.reserva;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class ReservaController {

	private final ReservaService reservaService;

	@PostMapping("/api/clubes/{clubId}/pistas/{pistaId}/reservas")
	@ResponseStatus(HttpStatus.CREATED)
	public ReservaResponse crear(@PathVariable Long clubId, @PathVariable Long pistaId,
			@Valid @RequestBody ReservaRequest peticion) {
		return reservaService.crear(clubId, pistaId, peticion);
	}

	@GetMapping("/api/clubes/{clubId}/pistas/{pistaId}/agenda")
	public List<CasillaAgenda> agenda(@PathVariable Long clubId, @PathVariable Long pistaId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
		return reservaService.agenda(clubId, pistaId, fecha);
	}

	@GetMapping("/api/reservas/mias")
	public List<ReservaResponse> mias() {
		return reservaService.mias();
	}

	@PostMapping("/api/reservas/{id}/cancelar")
	public ReservaResponse cancelar(@PathVariable Long id) {
		return reservaService.cancelar(id);
	}

	@GetMapping("/api/clubes/{clubId}/reservas")
	public List<ReservaClubResponse> delClub(@PathVariable Long clubId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
		return reservaService.delClub(clubId, fecha);
	}
}
