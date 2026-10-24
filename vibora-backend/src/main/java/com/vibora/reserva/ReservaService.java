package com.vibora.reserva;

import com.vibora.auth.AutorizacionClub;
import com.vibora.bloqueo.BloqueoPista;
import com.vibora.bloqueo.BloqueoPistaRepository;
import com.vibora.cargo.Cargo;
import com.vibora.cargo.CargoRepository;
import com.vibora.cargo.EstadoCargo;
import com.vibora.club.Club;
import com.vibora.club.ClubRepository;
import com.vibora.common.exception.ConflictoException;
import com.vibora.common.exception.RecursoNoEncontradoException;
import com.vibora.factura.FacturaService;
import com.vibora.notificacion.NotificacionService;
import com.vibora.notificacion.TextosAviso;
import com.vibora.notificacion.TipoNotificacion;
import com.vibora.partido.Participante;
import com.vibora.partido.ParticipanteRepository;
import com.vibora.pista.Pista;
import com.vibora.pista.PistaRepository;
import com.vibora.tarifa.CalculadoraPrecio;
import com.vibora.tarifa.TarifaFranja;
import com.vibora.tarifa.TarifaFranjaRepository;
import com.vibora.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Reservas de pistas. La fuente de verdad contra dobles reservas es la restriccion reserva_sin_solape de la BD;
 * la comprobacion previa solo existe para dar un error amable en el caso normal.
 */
@Service
@RequiredArgsConstructor
public class ReservaService {

	private static final String RESTRICCION_SOLAPE = "reserva_sin_solape";

	private final ReservaRepository reservas;
	private final PistaRepository pistas;
	private final ClubRepository clubs;
	private final TarifaFranjaRepository tarifas;
	private final AutorizacionClub autorizacion;
	private final ParticipanteRepository participantes;
	private final BloqueoPistaRepository bloqueos;
	private final CargoRepository cargos;
	private final NotificacionService notificaciones;
	private final FacturaService facturaService;
	private final TextosAviso textos;
	private final CalculadoraPrecio calculadora;
	private final ReservaProperties propiedades;
	private final Clock reloj;
	private final ZoneId zona;

	/**
	 * Reservar contra bloquear la pista: antes de mirar los bloqueos se toma un bloqueo COMPARTIDO de la fila de la pista
	 * (FOR SHARE). Varias reservas a la vez no se estorban (la restriccion reserva_sin_solape sigue protegiendo cada
	 * casilla), pero crear un bloqueo pide el bloqueo exclusivo de esa fila: espera a las reservas en curso y las que
	 * llegan despues esperan a que termine, y entonces ven el bloqueo. Asi no hay reserva que se cuele entre el calculo
	 * de las "afectadas" y la confirmacion del bloqueo. Orden de bloqueos: pista (compartido) y despues lo de siempre;
	 * ninguna otra operacion pide una fila de reserva, de cargo o de usuario ANTES que la de pista.
	 */
	@Transactional
	public ReservaResponse crear(Long clubId, Long pistaId, ReservaRequest peticion) {
		Club club = buscarClub(clubId);
		Pista pista = pistas.findByIdAndClubIdParaCompartir(pistaId, clubId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Pista no encontrada"));
		Usuario usuario = autorizacion.usuarioActual();
		if (!pista.isActiva()) {
			throw new ConflictoException("La pista no está activa");
		}
		Duration duracion = duracion();
		Instant inicio = peticion.inicio();
		Instant fin = inicio.plus(duracion);
		ParrillaHoraria.validar(club, inicio, duracion, zona);
		Instant ahora = reloj.instant();
		if (!inicio.isAfter(ahora)) {
			throw new IllegalArgumentException("No se puede reservar una casilla que ya ha empezado");
		}
		if (bloqueos.haySolape(pistaId, inicio, fin, ahora)) {
			throw new ConflictoException("La pista no está disponible por mantenimiento");
		}
		BigDecimal precio = calculadora.calcular(tarifas.findByClubId(clubId), inicio, fin, zona);

		// Comprobacion amable; la que manda es la restriccion de la BD (carreras entre peticiones)
		if (reservas.existsByPistaIdAndEstadoAndInicioLessThanAndFinGreaterThan(pistaId, EstadoReserva.CONFIRMADA,
				fin, inicio)) {
			throw pistaOcupada();
		}
		Reserva reserva = new Reserva();
		reserva.setPista(pista);
		reserva.setUsuario(usuario);
		reserva.setInicio(inicio);
		reserva.setFin(fin);
		reserva.setEstado(EstadoReserva.CONFIRMADA);
		reserva.setPrecioTotal(precio);
		reserva.setCreadoEn(reloj.instant());
		try {
			Reserva guardada = reservas.saveAndFlush(reserva); // flush: la restriccion salta aqui dentro
			// el organizador juega siempre: puesto 1, en la misma transaccion
			Participante organizador = new Participante();
			organizador.setReserva(guardada);
			organizador.setUsuario(usuario);
			organizador.setPuesto((short) 1);
			organizador.setUnidoEn(reloj.instant());
			participantes.save(organizador);
			guardada.getParticipantes().add(organizador);
			return ReservaResponse.de(guardada);
		} catch (DataIntegrityViolationException ex) {
			if (violaSolape(ex)) {
				throw pistaOcupada();
			}
			throw ex;
		} catch (PessimisticLockingFailureException ex) {
			// Con inserciones simultaneas sobre el mismo rango, PostgreSQL puede abortar a una de las
			// transacciones por interbloqueo (40P01) en vez de por la restriccion: otra reserva solapada gana.
			throw new ConflictoException("La pista se está reservando en este momento; inténtalo de nuevo");
		}
	}

	@Transactional(readOnly = true)
	public List<CasillaAgenda> agenda(Long clubId, Long pistaId, LocalDate fecha) {
		Club club = buscarClub(clubId);
		Pista pista = buscarPista(clubId, pistaId);
		Duration duracion = duracion();
		List<Reserva> ocupadas = reservas.findByPistaIdAndEstadoAndInicioLessThanAndFinGreaterThan(pistaId,
				EstadoReserva.CONFIRMADA, inicioDelDia(fecha.plusDays(1)), inicioDelDia(fecha));
		List<TarifaFranja> franjas = tarifas.findByClubId(clubId);
		Instant ahora = reloj.instant();
		List<BloqueoPista> bloqueados = bloqueos.activosEntre(pistaId, inicioDelDia(fecha), inicioDelDia(fecha.plusDays(1)),
				ahora);

		return ParrillaHoraria.inicios(club, fecha, duracion, zona).stream().map(inicio -> {
			Instant fin = inicio.plus(duracion);
			boolean bloqueada = bloqueados.stream()
					.anyMatch(b -> b.getDesde().isBefore(fin) && (b.getHasta() == null || b.getHasta().isAfter(inicio)));
			boolean libre = pista.isActiva() && inicio.isAfter(ahora) && !bloqueada
					&& ocupadas.stream().noneMatch(r -> r.getInicio().isBefore(fin) && r.getFin().isAfter(inicio));
			return new CasillaAgenda(inicio, fin, libre, precioONulo(franjas, inicio, fin), bloqueada);
		}).toList();
	}

	@Transactional(readOnly = true)
	public List<ReservaResponse> mias() {
		Usuario usuario = autorizacion.usuarioActual();
		return reservas.findByUsuarioIdOrderByCreadoEnDescIdDesc(usuario.getId()).stream()
				.map(ReservaResponse::de).toList();
	}

	@Transactional
	public ReservaResponse cancelar(Long id) {
		// con bloqueo: no puede cruzarse con un alta, una baja o un pago de ese mismo partido
		Reserva reserva = reservas.findByIdParaActualizar(id)
				.orElseThrow(() -> new RecursoNoEncontradoException("Reserva no encontrada"));
		Usuario usuario = autorizacion.usuarioActual();
		boolean admin = autorizacion.esAdminDe(reserva.getPista().getClub().getId());
		boolean propietario = reserva.getUsuario().getId().equals(usuario.getId());
		if (!admin && !propietario) {
			throw new AccessDeniedException("Acceso denegado");
		}
		if (reserva.getEstado() == EstadoReserva.CANCELADA) {
			return ReservaResponse.de(reserva); // idempotente
		}
		Instant ahora = reloj.instant();
		if (admin) {
			if (!ahora.isBefore(reserva.getInicio())) {
				throw new ConflictoException("La reserva ya ha comenzado");
			}
		} else if (ahora.isAfter(reserva.getInicio().minus(Duration.ofHours(propiedades.cancelacionMinimaHoras())))) {
			throw new ConflictoException("Fuera del plazo de cancelación");
		}
		reserva.setEstado(EstadoReserva.CANCELADA);
		liquidarCargosYAvisar(reserva, usuario, false);
		facturaService.rectificarSiFacturada(reserva); // ultimo bloqueo de fila: el contador de facturas
		return ReservaResponse.de(reservas.save(reserva));
	}

	/**
	 * Cancelacion del club por mantenimiento (la dispara el administrador desde un bloqueo de pista). Reutiliza la
	 * logica de cancelar: cargos pendientes a ANULADO, pagados a REEMBOLSADO y un aviso RESERVA_CANCELADA a cada jugador
	 * (organizador incluido) salvo a quien cancela y, si la reserva estaba facturada, emite su factura rectificativa. El
	 * llamador tiene que haber BLOQUEADO la fila de la reserva (findByIdParaActualizar) y comprobado que sigue confirmada y
	 * futura; si cancela varias en un lote, tiene que bloquearlas TODAS antes de cancelar la primera (ver BloqueoService).
	 */
	@Transactional
	public void cancelarPorMantenimiento(Reserva reserva, Usuario administrador) {
		reserva.setEstado(EstadoReserva.CANCELADA);
		liquidarCargosYAvisar(reserva, administrador, true);
		facturaService.rectificarSiFacturada(reserva); // ultimo bloqueo de fila: el contador de facturas
		reservas.save(reserva);
	}

	/**
	 * Al cancelar: los cargos pendientes se anulan, los ya pagados se reembolsan (simulado) y se avisa a cada
	 * jugador salvo a quien cancela (nunca se avisa a si mismo). El organizador solo queda sin aviso cuando es el
	 * quien cancela; si cancela el administrador del club, el organizador tambien se entera.
	 */
	private void liquidarCargosYAvisar(Reserva reserva, Usuario quienCancela, boolean porMantenimiento) {
		for (Cargo cargo : cargos.findByReservaId(reserva.getId())) {
			if (cargo.getEstado() == EstadoCargo.PENDIENTE) {
				cargo.setEstado(EstadoCargo.ANULADO);
			} else if (cargo.getEstado() == EstadoCargo.PAGADO) {
				cargo.setEstado(EstadoCargo.REEMBOLSADO);
			}
		}
		boolean cancelaElClub = porMantenimiento || !reserva.getUsuario().getId().equals(quienCancela.getId());
		String mensaje = (cancelaElClub ? "El club ha cancelado la reserva (%s)" : "Se ha cancelado la reserva (%s)")
				.formatted(textos.describir(reserva)) + (porMantenimiento ? " por mantenimiento de la pista" : "");
		for (Participante p : participantes.findByReservaIdOrderByPuesto(reserva.getId())) {
			if (!p.getUsuario().getId().equals(quienCancela.getId())) {
				notificaciones.notificar(p.getUsuario(), TipoNotificacion.RESERVA_CANCELADA, mensaje, reserva);
			}
		}
	}

	@Transactional(readOnly = true)
	public List<ReservaClubResponse> delClub(Long clubId, LocalDate fecha) {
		buscarClub(clubId);
		autorizacion.exigirAdminDe(clubId);
		return reservas.findByPistaClubIdAndInicioGreaterThanEqualAndInicioLessThanOrderByInicioAscIdAsc(clubId,
				inicioDelDia(fecha), inicioDelDia(fecha.plusDays(1))).stream().map(ReservaClubResponse::de).toList();
	}

	private BigDecimal precioONulo(List<TarifaFranja> franjas, Instant inicio, Instant fin) {
		try {
			return calculadora.calcular(franjas, inicio, fin, zona);
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private Instant inicioDelDia(LocalDate fecha) {
		return fecha.atStartOfDay(zona).toInstant();
	}

	private Duration duracion() {
		return Duration.ofMinutes(propiedades.duracionMinutos());
	}

	private static boolean violaSolape(Throwable ex) {
		for (Throwable t = ex; t != null; t = t.getCause()) {
			if (t.getMessage() != null && t.getMessage().contains(RESTRICCION_SOLAPE)) {
				return true;
			}
		}
		return false;
	}

	private static ConflictoException pistaOcupada() {
		return new ConflictoException("La pista ya está reservada en ese horario");
	}

	private Club buscarClub(Long clubId) {
		return clubs.findById(clubId).orElseThrow(() -> new RecursoNoEncontradoException("Club no encontrado"));
	}

	private Pista buscarPista(Long clubId, Long pistaId) {
		return pistas.findByIdAndClubId(pistaId, clubId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Pista no encontrada"));
	}
}
