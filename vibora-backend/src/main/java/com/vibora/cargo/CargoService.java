package com.vibora.cargo;

import com.vibora.auth.AutorizacionClub;
import com.vibora.common.exception.ConflictoException;
import com.vibora.common.exception.RecursoNoEncontradoException;
import com.vibora.notificacion.NotificacionService;
import com.vibora.notificacion.TextosAviso;
import com.vibora.notificacion.TipoNotificacion;
import com.vibora.reserva.ReservaRepository;
import com.vibora.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cargos entre jugadores, pago simulado y saldos. Permisos de pagar: solo el deudor. El acreedor (parte
 * implicada) recibe 403; cualquier otro usuario recibe 404, igual que un cargo inexistente (no se revela que existe).
 */
@Service
@RequiredArgsConstructor
public class CargoService {

	private static final String RESTRICCION_PAGO_UNICO = "pago_cargo_uk";

	private final CargoRepository cargos;
	private final PagoRepository pagos;
	private final ReservaRepository reservas;
	private final AutorizacionClub autorizacion;
	private final NotificacionService notificaciones;
	private final TextosAviso textos;
	private final Clock reloj;

	@Transactional(readOnly = true)
	public List<CargoResponse> mios(EstadoCargo estado) {
		Long usuarioId = autorizacion.usuarioActual().getId();
		return aRespuestas(estado == null ? cargos.deudasDe(usuarioId) : cargos.deudasDe(usuarioId, estado));
	}

	@Transactional(readOnly = true)
	public List<CargoResponse> porCobrar(EstadoCargo estado) {
		Long usuarioId = autorizacion.usuarioActual().getId();
		return aRespuestas(estado == null ? cargos.cobrosDe(usuarioId) : cargos.cobrosDe(usuarioId, estado));
	}

	@Transactional
	public CargoResponse pagar(Long id) {
		Usuario usuario = autorizacion.usuarioActual();
		CargoPartes partes = cargos.findPartesById(id)
				.orElseThrow(() -> new RecursoNoEncontradoException("Cargo no encontrado"));
		if (!partes.deudorId().equals(usuario.getId())) {
			if (partes.acreedorId().equals(usuario.getId())) {
				throw new AccessDeniedException("Acceso denegado");
			}
			throw new RecursoNoEncontradoException("Cargo no encontrado");
		}
		// Orden de bloqueo comun a todo el sistema: primero la reserva, despues el cargo (evita interbloqueos
		// con cancelaciones, que bloquean la reserva y luego tocan sus cargos).
		reservas.findByIdParaActualizar(partes.reservaId())
				.orElseThrow(() -> new RecursoNoEncontradoException("Cargo no encontrado"));
		Cargo cargo = cargos.findByIdParaActualizar(id)
				.orElseThrow(() -> new RecursoNoEncontradoException("Cargo no encontrado"));
		if (cargo.getEstado() != EstadoCargo.PENDIENTE) {
			throw new ConflictoException(switch (cargo.getEstado()) {
				case PAGADO -> "El cargo ya está pagado";
				case ANULADO -> "El cargo está anulado";
				case REEMBOLSADO -> "El cargo ya ha sido reembolsado";
				case PENDIENTE -> throw new IllegalStateException();
			});
		}

		Pago pago = new Pago();
		pago.setCargo(cargo);
		pago.setImporte(cargo.getImporte());
		pago.setReferencia("SIM-" + UUID.randomUUID());
		pago.setCreadoEn(reloj.instant());
		try {
			pagos.saveAndFlush(pago); // red de seguridad: pago_cargo_uk impide un segundo pago aunque falle el bloqueo
		} catch (DataIntegrityViolationException ex) {
			if (menciona(ex, RESTRICCION_PAGO_UNICO)) {
				throw new ConflictoException("El cargo ya está pagado");
			}
			throw ex;
		}
		cargo.setEstado(EstadoCargo.PAGADO);
		notificaciones.notificar(cargo.getAcreedor(), TipoNotificacion.PAGO_RECIBIDO,
				"%s te ha pagado %s por el partido de %s".formatted(usuario.nombreCompleto(),
						TextosAviso.euros(cargo.getImporte()), textos.describir(cargo.getReserva())),
				cargo.getReserva());
		return CargoResponse.de(cargo, pago.getCreadoEn());
	}

	@Transactional(readOnly = true)
	public SaldosResponse saldos() {
		Long usuarioId = autorizacion.usuarioActual().getId();
		List<SaldosResponse.SaldoUsuario> saldos = cargos.saldosDe(usuarioId).stream().map(fila -> {
			BigDecimal debo = ((BigDecimal) fila[3]).setScale(2);
			BigDecimal meDeben = ((BigDecimal) fila[4]).setScale(2);
			return new SaldosResponse.SaldoUsuario(((Number) fila[0]).longValue(), (String) fila[1], (String) fila[2],
					debo, meDeben, meDeben.subtract(debo));
		}).toList();
		BigDecimal totalDebo = saldos.stream().map(SaldosResponse.SaldoUsuario::debo).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
		BigDecimal totalMeDeben = saldos.stream().map(SaldosResponse.SaldoUsuario::meDeben).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
		return new SaldosResponse(saldos, totalDebo, totalMeDeben, totalMeDeben.subtract(totalDebo));
	}

	/** Respuestas con pagadoEn: una sola consulta extra para los pagos de todos los cargos que se pagaron. */
	private List<CargoResponse> aRespuestas(List<Cargo> lista) {
		Set<Long> pagados = lista.stream()
				.filter(c -> c.getEstado() == EstadoCargo.PAGADO || c.getEstado() == EstadoCargo.REEMBOLSADO)
				.map(Cargo::getId).collect(Collectors.toSet());
		Map<Long, Pago> pagosPorCargo = pagados.isEmpty() ? Map.of()
				: pagos.findByCargoIdIn(pagados).stream().collect(Collectors.toMap(p -> p.getCargo().getId(), Function.identity()));
		return lista.stream().map(c -> CargoResponse.de(c,
				pagosPorCargo.containsKey(c.getId()) ? pagosPorCargo.get(c.getId()).getCreadoEn() : null)).toList();
	}

	private static boolean menciona(Throwable ex, String texto) {
		for (Throwable t = ex; t != null; t = t.getCause()) {
			if (t.getMessage() != null && t.getMessage().contains(texto)) {
				return true;
			}
		}
		return false;
	}
}
