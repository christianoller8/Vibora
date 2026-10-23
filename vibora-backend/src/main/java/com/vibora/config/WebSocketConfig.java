package com.vibora.config;

import com.vibora.websocket.SalidaStompInterceptor;
import com.vibora.websocket.SesionesWs;
import com.vibora.websocket.StompAutenticacionInterceptor;
import com.vibora.websocket.WebSocketProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * Notificaciones en tiempo real: STOMP sobre WebSocket nativo en /ws (sin SockJS). Broker en memoria SOLO en /queue
 * (colas por usuario: /user/queue/...). Sin prefijo de aplicacion ni /topic: el cliente no puede enviar nada ni
 * suscribirse a canales de difusion.
 */
@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(WebSocketProperties.class)
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	/** Los unicos frames que envia un cliente (CONNECT/SUBSCRIBE) son diminutos. */
	private static final int LIMITE_MENSAJE_BYTES = 16 * 1024;

	private final WebSocketProperties propiedades;
	private final StompAutenticacionInterceptor stompAutenticacionInterceptor;
	private final SalidaStompInterceptor salidaStompInterceptor;
	private final SesionesWs sesiones;

	/**
	 * Planificador propio del WebSocket (latidos del broker y barrido de sesiones caducadas). Con nombre propio para
	 * no chocar con los planificadores que crea Spring (p. ej. messageBrokerTaskScheduler) ni con otros futuros.
	 */
	@Bean
	ThreadPoolTaskScheduler viboraWsScheduler() {
		ThreadPoolTaskScheduler planificador = new ThreadPoolTaskScheduler();
		planificador.setPoolSize(2);
		planificador.setThreadNamePrefix("vibora-ws-");
		planificador.setRemoveOnCancelPolicy(true);
		return planificador;
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registro) {
		registro.addEndpoint("/ws")
				.setAllowedOriginPatterns(propiedades.origenesPermitidos().toArray(String[]::new));
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registro) {
		long latidoMs = propiedades.latido().toMillis();
		registro.enableSimpleBroker("/queue")
				.setHeartbeatValue(new long[] { latidoMs, latidoMs })
				.setTaskScheduler(viboraWsScheduler());
		registro.setUserDestinationPrefix("/user");
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registro) {
		registro.interceptors(stompAutenticacionInterceptor);
	}

	@Override
	public void configureClientOutboundChannel(ChannelRegistration registro) {
		registro.interceptors(salidaStompInterceptor);
	}

	@Override
	public void configureWebSocketTransport(WebSocketTransportRegistration registro) {
		registro.setMessageSizeLimit(LIMITE_MENSAJE_BYTES);
		registro.addDecoratorFactory(sesiones.decoradorDeHandler());
	}
}
