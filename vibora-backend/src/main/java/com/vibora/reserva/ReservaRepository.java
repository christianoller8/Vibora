package com.vibora.reserva;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

	/** Hay una reserva del estado dado que solapa [inicio, fin) (intervalos semiabiertos). */
	boolean existsByPistaIdAndEstadoAndInicioLessThanAndFinGreaterThan(Long pistaId, EstadoReserva estado,
			Instant fin, Instant inicio);

	/** Reservas del estado dado que solapan [desde, hasta). */
	List<Reserva> findByPistaIdAndEstadoAndInicioLessThanAndFinGreaterThan(Long pistaId, EstadoReserva estado,
			Instant hasta, Instant desde);

	/** Con pista, club y jugadores en la misma consulta: evita una consulta por reserva al construir la respuesta. */
	@EntityGraph(attributePaths = { "pista", "pista.club", "participantes" })
	List<Reserva> findByUsuarioIdOrderByCreadoEnDescIdDesc(Long usuarioId);

	/**
	 * Con bloqueo de escritura de la fila (SELECT ... FOR UPDATE): altas, bajas, cancelaciones y pagos de una misma
	 * reserva se serializan. Orden de bloqueo en todo el sistema: primero la reserva, despues sus cargos.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Reserva r where r.id = :id")
	Optional<Reserva> findByIdParaActualizar(Long id);

	/**
	 * Partidos abiertos, confirmados, que empiezan en (despues de ahora) y [desde, hasta), con alguna plaza libre,
	 * por club opcional. Trae pista, club, organizador y jugadores (con su usuario) en una sola consulta.
	 */
	@EntityGraph(attributePaths = { "pista", "pista.club", "usuario", "participantes", "participantes.usuario" })
	@Query("""
			select r from Reserva r
			where r.abierta = true and r.estado = com.vibora.reserva.EstadoReserva.CONFIRMADA
			  and r.inicio > :ahora and r.inicio >= :desde and r.inicio < :hasta
			  and (:clubId is null or r.pista.club.id = :clubId)
			  and size(r.participantes) < 4
			order by r.inicio, r.id
			""")
	List<Reserva> partidosAbiertos(Instant ahora, Instant desde, Instant hasta, Long clubId);

	/** Reservas (cualquier estado) del club que empiezan en [desde, hasta). */
	List<Reserva> findByPistaClubIdAndInicioGreaterThanEqualAndInicioLessThanOrderByInicioAscIdAsc(Long clubId,
			Instant desde, Instant hasta);
}
