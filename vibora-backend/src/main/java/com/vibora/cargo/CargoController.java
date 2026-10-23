package com.vibora.cargo;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CargoController {

	private final CargoService cargoService;

	/** Lo que yo debo. */
	@GetMapping("/api/cargos/mios")
	public List<CargoResponse> mios(@RequestParam(required = false) EstadoCargo estado) {
		return cargoService.mios(estado);
	}

	/** Lo que otros me deben. */
	@GetMapping("/api/cargos/por-cobrar")
	public List<CargoResponse> porCobrar(@RequestParam(required = false) EstadoCargo estado) {
		return cargoService.porCobrar(estado);
	}

	/** Pago simulado (Bizum de mentira) de un cargo propio. */
	@PostMapping("/api/cargos/{id}/pagar")
	public CargoResponse pagar(@PathVariable Long id) {
		return cargoService.pagar(id);
	}

	@GetMapping("/api/saldos")
	public SaldosResponse saldos() {
		return cargoService.saldos();
	}
}
