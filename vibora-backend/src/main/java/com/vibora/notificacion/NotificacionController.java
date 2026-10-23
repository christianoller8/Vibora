package com.vibora.notificacion;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class NotificacionController {

	public record Contador(long count) {
	}

	public record Marcadas(int marcadas) {
	}

	private final NotificacionService notificacionService;

	@GetMapping("/api/notificaciones")
	public List<NotificacionResponse> listar(@RequestParam(defaultValue = "false") boolean soloNoLeidas,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "" + NotificacionService.LIMITE_POR_DEFECTO) int limit) {
		return notificacionService.listar(soloNoLeidas, page, limit);
	}

	@GetMapping("/api/notificaciones/no-leidas/count")
	public Contador contarNoLeidas() {
		return new Contador(notificacionService.contarNoLeidas());
	}

	@PostMapping("/api/notificaciones/{id}/leer")
	public NotificacionResponse leer(@PathVariable Long id) {
		return notificacionService.marcarLeida(id);
	}

	@PostMapping("/api/notificaciones/leer-todas")
	public Marcadas leerTodas() {
		return new Marcadas(notificacionService.marcarTodasLeidas());
	}
}
