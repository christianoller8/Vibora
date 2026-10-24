package com.vibora.bloqueo;

import com.vibora.auth.AutorizacionClub;
import com.vibora.club.ClubRepository;
import com.vibora.common.exception.ConflictoException;
import com.vibora.common.exception.RecursoNoEncontradoException;
import com.vibora.incidencia.HistorialIncidencia;
import com.vibora.incidencia.Incidencia;
import com.vibora.incidencia.IncidenciaRepository;
import com.vibora.incidencia.TipoEventoIncidencia;
import com.vibora.pista.Pista;
import com.vibora.pista.PistaRepository;
import com.vibora.reserva.EstadoReserva;
import com.vibora.reserva.Reserva;
import com.vibora.reserva.ReservaRepository;
import com.vibora.reserva.ReservaService;
import com.vibora.usuario.Usuario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Bloqueo de pistas por mantenimiento. Solo el administrador del club crea, levanta, ve las afectadas y las cancela
 * (el personal de mantenimiento solo LEE la lista). Un bloqueo nunca toca por si solo las reservas que ya existen:
 * solo impide las nuevas; las existentes quedan como "afectadas" para que el administrador decida.
 * <p>
 * Orden de comprobaciones: club (404) -> permiso (403) -> pista (404) -> bloqueo (404) -> datos (400) -> estado (409).
 * <p>
 * Concurrencia y orden de bloqueos de fila (siempre en este orden, sin cruces):
 * <ul>
 * <li><b>Crear</b>: incidencia (si hay) -> bloqueos de esa incidencia -> pista (exclusivo, PESSIMISTIC_WRITE). Con la
 * pista bloqueada se calculan las afectadas y se inserta el bloqueo. Las reservas toman la misma fila en modo compartido
 * antes de mirar los bloqueos (ver ReservaService.crear), asi que ninguna reserva puede colarse entre el calculo de las
 * afectadas y la confirmacion del bloqueo.</li>
 * <li><b>Levantar</b> (a mano o al cambiar la incidencia): fila del bloqueo (exclusivo) y despues el evento. Dos
 * levantamientos a la vez no duplican el evento.</li>
 * <li><b>Cancelar afectadas</b>: PRIMERO todas las reservas en orden ASCENDENTE de id (una a una, con
 * findByIdParaActualizar) y SOLO DESPUES, ya con todas bloqueadas, cancelar cada una -> sus cargos -> (si estaba
 * facturada) el contador de facturas, que es el orden de cancelar y pagar (reserva, cargo, contador). Bloquear todas
 * antes de tocar ninguna es lo que impide que el lote retenga el contador mientras espera a otra reserva. No bloquea
 * la fila del bloqueo. Dos
 * cancelaciones a la vez (del mismo bloqueo o de bloqueos distintos que solapan las mismas reservas) esperan en la
 * primera reserva comun, la releen ya canceladas y las saltan (sigueAfectada): ni doble reembolso ni doble aviso, y al
 * ir todas en el mismo orden no hay interbloqueo.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class BloqueoService {

	private static final Instant SIN_FIN = Instant.parse("9999-12-31T00:00:00Z");

	private final BloqueoPistaRepository bloqueos;
	private final PistaRepository pistas;
	private final ClubRepository clubs;
	private final IncidenciaRepository incidencias;
	private final ReservaRepository reservas;
	private final ReservaService reservaService;
	private final AutorizacionClub autorizacion;
	private final HistorialIncidencia historial;
	private final EntityManager em;
	private final Clock reloj;

	// ---- crear ----

	@Transactional
	public BloqueoCreadoResponse crear(Long clubId, Long pistaId, BloqueoRequest peticion) {
		buscarClub(clubId);
		Usuario administrador = autorizacion.exigirAdminDe(clubId);
		buscarPista(clubId, pistaId);
		// Misma respuesta para inexistente y de otro club: no se revela que existe
		Incidencia incidencia = peticion.incidenciaId() == null ? null
				: incidencias.buscarCompleta(peticion.incidenciaId())
						.filter(i -> i.getClub().getId().equals(clubId))
						.orElseThrow(() -> new RecursoNoEncontradoException("Incidencia no encontrada"));
		return crearBloqueo(administrador, pistaId, incidencia, peticion.motivo().trim(), peticion.desde(), peticion.hasta());
	}

	/**
	 * Crea el bloqueo ya comprobado el permiso del administrador. La usa tambien el atajo desde una incidencia.
	 * {@code desde} nulo = ahora; {@code hasta} nulo = sin fin.
	 */
	@Transactional
	public BloqueoCreadoResponse crearBloqueo(Usuario administrador, Long pistaId, Incidencia incidencia, String motivo,
			Instant desdePedido, Instant hastaPedido) {
		Instant ahora = ahora();
		Instant desde = desdePedido == null ? ahora : desdePedido.truncatedTo(ChronoUnit.MICROS);
		Instant hasta = hastaPedido == null ? null : hastaPedido.truncatedTo(ChronoUnit.MICROS);
		if (hasta != null && !hasta.isAfter(desde)) {
			throw new IllegalArgumentException("El fin del bloqueo debe ser posterior al inicio");
		}
		if (hasta != null && !hasta.isAfter(ahora)) {
			throw new IllegalArgumentException("El fin del bloqueo debe ser futuro");
		}
		if (incidencia != null) {
			prepararIncidencia(incidencia, pistaId, ahora);
		}

		// Bloqueo exclusivo de la pista ANTES de calcular las afectadas (ver la nota de concurrencia de la clase)
		Pista pista = pistas.findByIdParaActualizar(pistaId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Pista no encontrada"));
		List<Reserva> afectadas = reservas.afectadasPorBloqueo(pistaId, ahora, desde, finEfectivo(hasta));

		BloqueoPista bloqueo = new BloqueoPista();
		bloqueo.setPista(pista);
		bloqueo.setIncidencia(incidencia);
		bloqueo.setMotivo(motivo);
		bloqueo.setDesde(desde);
		bloqueo.setHasta(hasta);
		bloqueo.setCreadoPor(administrador);
		bloqueo.setCreadoEn(ahora);
		bloqueos.saveAndFlush(bloqueo);
		if (incidencia != null) {
			historial.registrar(incidencia, administrador, TipoEventoIncidencia.BLOQUEO_CREADO, null, null,
					"Pista %s bloqueada: %s".formatted(pista.getNombre(), motivo));
		}
		return new BloqueoCreadoResponse(BloqueoResponse.de(bloqueo, ahora),
				afectadas.stream().map(ReservaAfectadaResponse::de).toList());
	}

	/**
	 * Valida la incidencia y la BLOQUEA (fila): asi un cambio de estado concurrente a CERRADA/RECHAZADA o un segundo
	 * bloqueo para la misma incidencia esperan a que esta transaccion termine, y nunca queda un bloqueo activo ligado
	 * a una incidencia ya terminada (que ya no se levantaria sola).
	 */
	private void prepararIncidencia(Incidencia incidencia, Long pistaId, Instant ahora) {
		if (incidencia.getPista() != null && !incidencia.getPista().getId().equals(pistaId)) {
			throw new IllegalArgumentException("La incidencia es de otra pista");
		}
		em.refresh(incidencia, LockModeType.PESSIMISTIC_WRITE);
		if (incidencia.getEstado().esTerminal()) {
			throw new ConflictoException("No se puede bloquear una pista por una incidencia %s"
					.formatted(incidencia.getEstado().etiqueta().toLowerCase()));
		}
		Optional<BloqueoPista> previo = bloqueos.findSinLevantarDeIncidenciaParaActualizar(incidencia.getId());
		if (previo.isPresent()) {
			BloqueoPista anterior = previo.get();
			if (anterior.estaActivoEn(ahora)) {
				throw new ConflictoException("La incidencia ya tiene un bloqueo activo");
			}
			// Caducado sin levantar: sigue ocupando el hueco "uno activo por incidencia"; se cierra en su fecha de fin
			anterior.setLevantadoEn(anterior.getHasta());
			bloqueos.saveAndFlush(anterior);
		}
	}

	// ---- consultar ----

	/** El administrador y el personal de mantenimiento del club. {@code activos}: nulo = todos, true/false = solo esos. */
	@Transactional(readOnly = true)
	public List<BloqueoResponse> listar(Long clubId, Long pistaId, Boolean activos) {
		buscarClub(clubId);
		Usuario actor = autorizacion.usuarioActual();
		if (!AutorizacionClub.esAdminDe(actor, clubId) && !AutorizacionClub.esMantenimientoDe(actor, clubId)) {
			throw new AccessDeniedException("Acceso denegado");
		}
		buscarPista(clubId, pistaId);
		Instant ahora = ahora();
		return bloqueos.listarDeLaPista(pistaId).stream()
				.filter(b -> activos == null || b.estaActivoEn(ahora) == activos)
				.map(b -> BloqueoResponse.de(b, ahora)).toList();
	}

	/** Reservas afectadas AHORA por el bloqueo; un bloqueo levantado o caducado no afecta a ninguna. */
	@Transactional(readOnly = true)
	public List<ReservaAfectadaResponse> afectadas(Long clubId, Long pistaId, Long bloqueoId) {
		buscarClub(clubId);
		autorizacion.exigirAdminDe(clubId);
		buscarPista(clubId, pistaId);
		BloqueoPista bloqueo = bloqueos.findByIdAndPistaId(bloqueoId, pistaId).orElseThrow(this::bloqueoNoEncontrado);
		Instant ahora = ahora();
		if (!bloqueo.estaActivoEn(ahora)) {
			return List.of();
		}
		return reservas.afectadasPorBloqueo(pistaId, ahora, bloqueo.getDesde(), finEfectivo(bloqueo.getHasta())).stream()
				.map(ReservaAfectadaResponse::de).toList();
	}

	// ---- levantar ----

	/** Idempotente: levantar uno ya levantado no cambia nada (ni la fecha, ni quien, ni el historial). */
	@Transactional
	public void levantar(Long clubId, Long pistaId, Long bloqueoId) {
		buscarClub(clubId);
		Usuario administrador = autorizacion.exigirAdminDe(clubId);
		buscarPista(clubId, pistaId);
		BloqueoPista bloqueo = bloqueos.findByIdAndPistaIdParaActualizar(bloqueoId, pistaId)
				.orElseThrow(this::bloqueoNoEncontrado);
		if (bloqueo.getLevantadoEn() != null) {
			return;
		}
		levantarYRegistrar(bloqueo, administrador, ahora(), "Bloqueo de la pista %s levantado");
	}

	/**
	 * Levanta el bloqueo sin levantar de la incidencia, si lo hay, en la transaccion de quien cambia su estado. Lo
	 * llama IncidenciaService al pasar a RESUELTA, CERRADA o RECHAZADA; reabrirla NO lo vuelve a crear.
	 */
	@Transactional
	public void levantarDeIncidencia(Incidencia incidencia, Usuario actor, String detalle) {
		bloqueos.findSinLevantarDeIncidenciaParaActualizar(incidencia.getId())
				.ifPresent(b -> levantarYRegistrar(b, actor, ahora(), "Bloqueo de la pista %s levantado " + detalle));
	}

	private void levantarYRegistrar(BloqueoPista bloqueo, Usuario actor, Instant ahora, String plantillaTexto) {
		bloqueo.setLevantadoEn(ahora);
		bloqueo.setLevantadoPor(actor);
		bloqueos.save(bloqueo);
		if (bloqueo.getIncidencia() != null) {
			historial.registrar(bloqueo.getIncidencia(), actor, TipoEventoIncidencia.BLOQUEO_LEVANTADO, null, null,
					plantillaTexto.formatted(bloqueo.getPista().getNombre()));
		}
	}

	// ---- cancelar afectadas ----

	/**
	 * Cancela todas las reservas afectadas (confirmadas, futuras y solapadas) reutilizando la cancelacion de siempre,
	 * en UNA transaccion. Repetirlo no hace nada nuevo (devuelve 0). Un bloqueo levantado o caducado ya no afecta a
	 * nadie: 409.
	 */
	@Transactional
	public CancelacionAfectadasResponse cancelarAfectadas(Long clubId, Long pistaId, Long bloqueoId) {
		buscarClub(clubId);
		Usuario administrador = autorizacion.exigirAdminDe(clubId);
		buscarPista(clubId, pistaId);
		BloqueoPista bloqueo = bloqueos.findByIdAndPistaId(bloqueoId, pistaId).orElseThrow(this::bloqueoNoEncontrado);
		Instant ahora = ahora();
		if (!bloqueo.estaActivoEn(ahora)) {
			throw new ConflictoException("El bloqueo ya no está activo");
		}

		// Solo ids (sin cargar las reservas): al bloquear cada fila se lee su estado ACTUAL, no uno viejo de la sesion
		List<Long> candidatas = reservas.idsAfectadasPorBloqueo(pistaId, ahora, bloqueo.getDesde(),
				finEfectivo(bloqueo.getHasta()));
		// Fase 1: se BLOQUEAN todas, en orden ascendente, antes de cancelar ninguna. Cancelar una reserva facturada toma el
		// contador de facturas, que se conserva hasta el commit: si se cancelase reserva a reserva, el lote tendria el
		// contador mientras espera a la siguiente, y quien tuviese esa reserva y quisiera el contador cerraria un ciclo.
		List<Reserva> bloqueadas = new ArrayList<>();
		for (Long id : candidatas) { // ya en orden ascendente
			Reserva reserva = reservas.findByIdParaActualizar(id).orElse(null);
			if (reserva != null && sigueAfectada(reserva, bloqueo, ahora)) { // si no, otra transaccion la cancelo o la movio
				bloqueadas.add(reserva);
			}
		}
		// Fase 2: ya con todas las filas en la mano, se cancelan (cargos, avisos y, si estaban facturadas, rectificativas)
		List<Long> canceladas = new ArrayList<>();
		for (Reserva reserva : bloqueadas) {
			reservaService.cancelarPorMantenimiento(reserva, administrador);
			canceladas.add(reserva.getId());
		}
		return new CancelacionAfectadasResponse(canceladas.size(), canceladas);
	}

	/** El rango de un bloqueo sin fin es [desde, infinito): para PostgreSQL, un instante lejanisimo. */
	private static Instant finEfectivo(Instant hasta) {
		return hasta == null ? SIN_FIN : hasta;
	}

	private static boolean sigueAfectada(Reserva r, BloqueoPista b, Instant ahora) {
		return r.getEstado() == EstadoReserva.CONFIRMADA && r.getInicio().isAfter(ahora) && r.getFin().isAfter(b.getDesde())
				&& (b.getHasta() == null || r.getInicio().isBefore(b.getHasta()));
	}

	// ---- ayudas ----

	private void buscarClub(Long clubId) {
		clubs.findById(clubId).orElseThrow(() -> new RecursoNoEncontradoException("Club no encontrado"));
	}

	private Pista buscarPista(Long clubId, Long pistaId) {
		return pistas.findByIdAndClubId(pistaId, clubId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Pista no encontrada"));
	}

	private RecursoNoEncontradoException bloqueoNoEncontrado() {
		return new RecursoNoEncontradoException("Bloqueo no encontrado");
	}

	/** Microsegundos, la precision de la BD. */
	private Instant ahora() {
		return reloj.instant().truncatedTo(ChronoUnit.MICROS);
	}
}
