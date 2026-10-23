package com.vibora.notificacion;

import com.vibora.auth.AutorizacionClub;
import com.vibora.common.exception.RecursoNoEncontradoException;
import com.vibora.reserva.Reserva;
import com.vibora.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Unico punto por el que se crean notificaciones: guarda la fila y publica {@link NotificacionCreada} (una vez por
 * notificacion) para que quien quiera la entregue en tiempo real tras el commit.
 */
@Service
@RequiredArgsConstructor
public class NotificacionService {

	static final int LIMITE_POR_DEFECTO = 50;
	static final int LIMITE_MAXIMO = 100;
	private static final int LONGITUD_MAXIMA = 500;

	private final NotificacionRepository notificaciones;
	private final ApplicationEventPublisher eventos;
	private final AutorizacionClub autorizacion;
	private final Clock reloj;

	@Transactional
	public Notificacion notificar(Usuario usuario, TipoNotificacion tipo, String mensaje, Reserva reserva) {
		Notificacion n = new Notificacion();
		n.setUsuario(usuario);
		n.setTipo(tipo);
		n.setMensaje(mensaje.length() > LONGITUD_MAXIMA ? mensaje.substring(0, LONGITUD_MAXIMA - 3) + "..." : mensaje);
		n.setReserva(reserva);
		// microsegundos, la precision de la BD: lo empujado por WebSocket coincide exactamente con lo guardado
		n.setCreadaEn(reloj.instant().truncatedTo(ChronoUnit.MICROS));
		Notificacion guardada = notificaciones.save(n);
		eventos.publishEvent(new NotificacionCreada(guardada.getId(), usuario.getId(), usuario.getEmail(), tipo,
				guardada.getMensaje(), reserva == null ? null : reserva.getId(), guardada.getCreadaEn()));
		return guardada;
	}

	@Transactional(readOnly = true)
	public List<NotificacionResponse> listar(boolean soloNoLeidas, int pagina, int limite) {
		if (pagina < 0) {
			throw new IllegalArgumentException("La página no puede ser negativa");
		}
		if (limite < 1) {
			throw new IllegalArgumentException("El límite debe ser al menos 1");
		}
		Long usuarioId = autorizacion.usuarioActual().getId();
		PageRequest pedido = PageRequest.of(pagina, Math.min(limite, LIMITE_MAXIMO));
		List<Notificacion> filas = soloNoLeidas
				? notificaciones.findByUsuarioIdAndLeidaFalseOrderByCreadaEnDescIdDesc(usuarioId, pedido)
				: notificaciones.findByUsuarioIdOrderByCreadaEnDescIdDesc(usuarioId, pedido);
		return filas.stream().map(NotificacionResponse::de).toList();
	}

	@Transactional(readOnly = true)
	public long contarNoLeidas() {
		return notificaciones.countByUsuarioIdAndLeidaFalse(autorizacion.usuarioActual().getId());
	}

	@Transactional
	public NotificacionResponse marcarLeida(Long id) {
		Long usuarioId = autorizacion.usuarioActual().getId();
		// las de otros usuarios se tratan como inexistentes: no se revela que existen
		Notificacion n = notificaciones.findByIdAndUsuarioId(id, usuarioId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Notificación no encontrada"));
		n.setLeida(true);
		return NotificacionResponse.de(n);
	}

	@Transactional
	public int marcarTodasLeidas() {
		return notificaciones.marcarTodasLeidas(autorizacion.usuarioActual().getId());
	}
}
