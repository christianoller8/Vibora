package com.vibora.auth;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Controlador SOLO de test: comprueba que @PreAuthorize por rol funciona. */
@RestController
class AdminProbeController {

	@GetMapping("/api/test/admin")
	@PreAuthorize("hasRole('ADMIN_CLUB')")
	Map<String, String> admin() {
		return Map.of("ok", "admin");
	}
}
