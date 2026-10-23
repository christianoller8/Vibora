package com.vibora.partido;

import com.vibora.auth.AutorizacionClub;
import com.vibora.cargo.CalculadoraCuotas;
import com.vibora.cargo.Cargo;
import com.vibora.cargo.CargoRepository;
import com.vibora.cargo.EstadoCargo;
import com.vibora.common.exception.ConflictoException;
import com.vibora.common.exception.RecursoNoEncontradoException;
import com.vibora.notificacion.NotificacionService;
import com.vibora.notificacion.TextosAviso;
import com.vibora.notificacion.TipoNotificacion;
import com.vibora.reserva.EstadoReserva;
import com.vibora.reserva.Reserva;
import com.vibora.reserva.ReservaRepository;
import com.vibora.reserva.ReservaResponse;
import com.vibora.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Partidos abiertos: apuntarse, salir y abrir/cerrar. Todo cambio de una reserva (altas, bajas, cancelacion, pago)
 * empieza bloqueando su fila (SELECT ... FOR UPDATE): las operaciones sobre un mismo partido se serializan. Las
 * restricciones de la BD (participante_puesto_uk, participante_usuario_uk, cargo_vivo_uk) son la red de seguridad:
 * si saltasen, se traducen a 409 y nunca a 500.
 * <p>
 * Regla de cuota cero: si la cuota por jugador es 0 (total de 0.00 a 0.03 EUR) el jugador SE PUEDE apuntar pero no
 * se crea ningun cargo (la BD exige importe &gt; 0) ni aviso de cargo.
 */
@Service
@RequiredArgsConstructor
public class PartidoService {

	private static final String PUESTO_UK = "participante_puesto_uk";
	private static final String USUARIO_UK = "participante_usuario_uk";
	private static final String CARGO_VIVO_UK = "cargo_vivo_uk";
	private static final Instant SIN_LIMITE = Instant.parse("9999-12-31T00:00:00Z");

	private final ReservaRepository reservas;
	private final ParticipanteRepository participantes;
	private final CargoRepository cargos;
	private final AutorizacionClub autorizacion;
	private final NotificacionService notificaciones;
	private final TextosAviso textos;
	private final Clock reloj;
	private final ZoneId zona;

	@Transactional(readOnly = true)
	public List<PartidoResponse> listar(Long clubId, LocalDate fecha) {
		Usuario usuario = autorizacion.usuarioActual();
		Instant ahora = reloj.instant();
		Instant desde = fecha == null ? ahora : fecha.atStartOfDay(zona).toInstant();
		Instant hasta = fecha == null ? SIN_LIMITE : fecha.plusDays(1).atStartOfDay(zona).toInstant();
		return reservas.partidosAbiertos(ahora, desde, hasta, clubId).stream()
				.map(r -> PartidoResponse.de(r, r.getParticipantes(), usuario.getId())).toList();
	}

	@Transactional
	public ReservaResponse cambiarAbierta(Long id, boolean abierta) {
		Reserva reserva = bloquear(id);
		Usuario usuario = autorizacion.usuarioActual();
		if (!reserva.getUsuario().getId().equals(usuario.getId())) {
			throw new AccessDeniedException("Acceso denegado");
		}
		exigirConfirmadaYFutura(reserva, "No se puede abrir o cerrar una reserva cancelada");
		reserva.setAbierta(abierta);
		return ReservaResponse.de(reserva);
	}

	@Transactional
	public PartidoResponse apuntarse(Long id) {
		Usuario usuario = autorizacion.usuarioActual();
		Reserva reserva = bloquear(id);
		if (reserva.getEstado() != EstadoReserva.CONFIRMADA) {
			throw new ConflictoException("La reserva está cancelada");
		}
		if (!reserva.isAbierta()) {
			throw new ConflictoException("El partido no está abierto");
		}
		if (!reloj.instant().isBefore(reserva.getInicio())) {
			throw new ConflictoException("El partido ya ha comenzado");
		}
		if (participantes.existsByReservaIdAndUsuarioId(id, usuario.getId())) {
			throw new ConflictoException("Ya estás apuntado a este partido");
		}
		List<Short> ocupados = participantes.findPuestosOcupados(id);
		if (ocupados.size() >= CalculadoraCuotas.JUGADORES) {
			throw completo();
		}
		short puesto = 1;
		while (ocupados.contains(puesto)) {
			puesto++;
		}

		Participante nuevo = new Participante();
		nuevo.setReserva(reserva);
		nuevo.setUsuario(usuario);
		nuevo.setPuesto(puesto);
		nuevo.setUnidoEn(reloj.instant());
		BigDecimal cuota = CalculadoraCuotas.cuotaPorJugador(reserva.getPrecioTotal());
		Usuario organizador = reserva.getUsuario();
		try {
			participantes.saveAndFlush(nuevo);
			if (cuota.signum() > 0) {
				Cargo cargo = new Cargo();
				cargo.setReserva(reserva);
				cargo.setDeudor(usuario);
				cargo.setAcreedor(organizador);
				cargo.setImporte(cuota);
				cargo.setEstado(EstadoCargo.PENDIENTE);
				cargo.setCreadoEn(reloj.instant());
				cargos.saveAndFlush(cargo);
			}
		} catch (DataIntegrityViolationException ex) {
			throw traducir(ex);
		}
		if (Hibernate.isInitialized(reserva.getParticipantes())) {
			reserva.getParticipantes().add(nuevo); // coleccion ya cargada: se mantiene en sincronia
		}

		String descripcion = textos.describir(reserva);
		notificaciones.notificar(organizador, TipoNotificacion.JUGADOR_SE_APUNTO,
				"%s se ha apuntado a tu partido (%s)".formatted(usuario.nombreCompleto(), descripcion), reserva);
		if (cuota.signum() > 0) {
			notificaciones.notificar(usuario, TipoNotificacion.CARGO_NUEVO, "Debes %s a %s por el partido de %s"
					.formatted(TextosAviso.euros(cuota), organizador.nombreCompleto(), descripcion), reserva);
		}
		if (ocupados.size() + 1 == CalculadoraCuotas.JUGADORES) {
			notificaciones.notificar(organizador, TipoNotificacion.PARTIDO_COMPLETO,
					"Tu partido (%s) ya está completo".formatted(descripcion), reserva);
		}
		return respuesta(reserva, usuario);
	}

	@Transactional
	public PartidoResponse salir(Long id) {
		Usuario usuario = autorizacion.usuarioActual();
		Reserva reserva = bloquear(id);
		if (reserva.getUsuario().getId().equals(usuario.getId())) {
			throw new ConflictoException("El organizador debe cancelar la reserva");
		}
		Participante participante = participantes.findByReservaIdAndUsuarioId(id, usuario.getId())
				.orElseThrow(() -> new ConflictoException("No estás apuntado a este partido"));
		exigirConfirmadaYFutura(reserva, "No puedes salir de una reserva cancelada");
		Optional<Cargo> vivo = cargos.findByReservaIdAndDeudorIdAndEstadoIn(id, usuario.getId(),
				List.of(EstadoCargo.PENDIENTE, EstadoCargo.PAGADO));
		if (vivo.isPresent() && vivo.get().getEstado() == EstadoCargo.PAGADO) {
			throw new ConflictoException("Ya has pagado tu parte");
		}
		vivo.ifPresent(c -> c.setEstado(EstadoCargo.ANULADO)); // si no hay cargo (cuota cero) no hay nada que anular
		participantes.delete(participante);
		participantes.flush();
		if (Hibernate.isInitialized(reserva.getParticipantes())) {
			reserva.getParticipantes().remove(participante);
		}
		notificaciones.notificar(reserva.getUsuario(), TipoNotificacion.JUGADOR_SALIO,
				"%s ha salido de tu partido (%s)".formatted(usuario.nombreCompleto(), textos.describir(reserva)),
				reserva);
		return respuesta(reserva, usuario);
	}

	private PartidoResponse respuesta(Reserva reserva, Usuario usuario) {
		return PartidoResponse.de(reserva, participantes.findByReservaIdOrderByPuesto(reserva.getId()), usuario.getId());
	}

	private Reserva bloquear(Long id) {
		return reservas.findByIdParaActualizar(id)
				.orElseThrow(() -> new RecursoNoEncontradoException("Reserva no encontrada"));
	}

	private void exigirConfirmadaYFutura(Reserva reserva, String mensajeCancelada) {
		if (reserva.getEstado() != EstadoReserva.CONFIRMADA) {
			throw new ConflictoException(mensajeCancelada);
		}
		if (!reloj.instant().isBefore(reserva.getInicio())) {
			throw new ConflictoException("El partido ya ha comenzado");
		}
	}

	private static ConflictoException completo() {
		return new ConflictoException("El partido está completo");
	}

	/** Restriccion de BD violada -> 409 con el mensaje que corresponde; cualquier otra cosa se relanza tal cual. */
	private static RuntimeException traducir(DataIntegrityViolationException ex) {
		for (Throwable t = ex; t != null; t = t.getCause()) {
			String mensaje = t.getMessage();
			if (mensaje == null) {
				continue;
			}
			if (mensaje.contains(PUESTO_UK)) {
				return completo();
			}
			if (mensaje.contains(USUARIO_UK) || mensaje.contains(CARGO_VIVO_UK)) {
				return new ConflictoException("Ya estás apuntado a este partido");
			}
		}
		return ex;
	}
}
