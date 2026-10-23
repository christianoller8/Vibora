package com.vibora.websocket;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Configuracion del WebSocket. {@code vibora.websocket.origenes-permitidos}: origenes (patrones, lista separada por
 * comas) desde los que un navegador puede abrir /ws. Por defecto solo el servidor de desarrollo del frontend.
 * {@code vibora.websocket.latido}: periodo de los latidos (heart-beat) STOMP del servidor, en ambos sentidos
 * (por defecto 10 s). El intervalo del barrido de sesiones caducadas ({@code vibora.websocket.intervalo-barrido},
 * ISO-8601, por defecto PT30S) lo lee {@link SesionesWs} directamente.
 */
@ConfigurationProperties(prefix = "vibora.websocket")
public record WebSocketProperties(List<String> origenesPermitidos, Duration latido) {

	public static final List<String> ORIGENES_POR_DEFECTO = List.of("http://localhost:4200");
	public static final Duration LATIDO_POR_DEFECTO = Duration.ofSeconds(10);

	public WebSocketProperties {
		List<String> limpios = origenesPermitidos == null ? List.of()
				: origenesPermitidos.stream().map(String::trim).filter(o -> !o.isEmpty()).toList();
		origenesPermitidos = limpios.isEmpty() ? ORIGENES_POR_DEFECTO : limpios;
		latido = latido == null || latido.isNegative() ? LATIDO_POR_DEFECTO : latido;
	}
}
