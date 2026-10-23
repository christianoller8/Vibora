package com.vibora.websocket;

import com.vibora.auth.JwtService;
import com.vibora.usuario.Usuario;
import com.vibora.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Seguridad del canal de entrada STOMP. El navegador no puede poner cabeceras en el handshake del WebSocket, asi que
 * la autenticacion se hace en el frame CONNECT (cabecera {@code Authorization: Bearer <jwt>}) con las MISMAS reglas
 * que la API REST (firma, caducidad, emisor, y usuario existente y activo en la BD, como /me). Politica por frame:
 * <ul>
 * <li>CONNECT: exige JWT valido; el Principal pasa a ser el usuario (nombre = email = subject del JWT).</li>
 * <li>SUBSCRIBE: solo {@code /user/queue/notificaciones}, y solo con sesion autenticada.</li>
 * <li>UNSUBSCRIBE / DISCONNECT: permitidos.</li>
 * <li>Todo lo demas (SEND, ACK, BEGIN...): denegado. El cliente nunca envia nada a la aplicacion ni a otros usuarios.</li>
 * </ul>
 * La identidad se valida al conectar: una conexion ya abierta no se vuelve a comprobar cuando el token caduca.
 */
@Component
@RequiredArgsConstructor
public class StompAutenticacionInterceptor implements ChannelInterceptor {

	static final String DESTINO_PERMITIDO = "/user/queue/notificaciones";
	private static final String PREFIJO_BEARER = "bearer ";

	private final JwtService jwtService;
	private final JwtAuthenticationConverter convertidor;
	private final UsuarioRepository usuarios;

	@Override
	public Message<?> preSend(Message<?> mensaje, MessageChannel canal) {
		StompHeaderAccessor accesor = MessageHeaderAccessor.getAccessor(mensaje, StompHeaderAccessor.class);
		StompCommand comando = accesor == null ? null : accesor.getCommand();
		if (comando == null) {
			return mensaje; // latidos y mensajes internos que no son frames STOMP
		}
		switch (comando) {
			case CONNECT, STOMP -> accesor.setUser(autenticar(accesor.getFirstNativeHeader("Authorization")));
			case SUBSCRIBE -> {
				if (accesor.getUser() == null) {
					throw new AccesoStompDenegadoException(AccesoStompDenegadoException.NO_AUTENTICADO);
				}
				if (!DESTINO_PERMITIDO.equals(accesor.getDestination())) {
					throw new AccesoStompDenegadoException(AccesoStompDenegadoException.ACCESO_DENEGADO);
				}
			}
			case UNSUBSCRIBE, DISCONNECT -> {
			}
			default -> throw new AccesoStompDenegadoException(
					accesor.getUser() == null ? AccesoStompDenegadoException.NO_AUTENTICADO
							: AccesoStompDenegadoException.ACCESO_DENEGADO);
		}
		return mensaje;
	}

	private Authentication autenticar(String cabecera) {
		if (cabecera == null || !cabecera.toLowerCase(Locale.ROOT).startsWith(PREFIJO_BEARER)) {
			throw new AccesoStompDenegadoException(AccesoStompDenegadoException.NO_AUTENTICADO);
		}
		String token = cabecera.substring(PREFIJO_BEARER.length()).trim();
		Jwt jwt;
		try {
			jwt = jwtService.decodificar(token);
		} catch (JwtException | IllegalArgumentException ex) {
			throw new AccesoStompDenegadoException(AccesoStompDenegadoException.NO_AUTENTICADO);
		}
		// igual que /me: el usuario debe seguir existiendo y estar activo
		usuarios.findByEmail(jwt.getSubject()).filter(Usuario::isActivo)
				.orElseThrow(() -> new AccesoStompDenegadoException(AccesoStompDenegadoException.NO_AUTENTICADO));
		return convertidor.convert(jwt);
	}
}
