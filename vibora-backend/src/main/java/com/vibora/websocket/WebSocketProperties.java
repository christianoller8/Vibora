package com.vibora.websocket;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuracion del WebSocket. {@code vibora.websocket.origenes-permitidos}: origenes (patrones, lista separada por
 * comas) desde los que un navegador puede abrir /ws. Por defecto solo el servidor de desarrollo del frontend.
 */
@ConfigurationProperties(prefix = "vibora.websocket")
public record WebSocketProperties(List<String> origenesPermitidos) {

	public static final List<String> ORIGENES_POR_DEFECTO = List.of("http://localhost:4200");

	public WebSocketProperties {
		List<String> limpios = origenesPermitidos == null ? List.of()
				: origenesPermitidos.stream().map(String::trim).filter(o -> !o.isEmpty()).toList();
		origenesPermitidos = limpios.isEmpty() ? ORIGENES_POR_DEFECTO : limpios;
	}
}
