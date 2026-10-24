package com.vibora.metricas;

import com.vibora.reserva.EstadoReserva;
import com.vibora.reserva.Reserva;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Lecturas de reservas para las metricas: solo las columnas necesarias (nada de jugadores ni de entidades completas). */
interface ReservaMetricasRepository extends Repository<Reserva, Long> {

	interface Fila {
		Long getPistaId();

		Instant getInicio();

		EstadoReserva getEstado();

		BigDecimal getPrecioTotal();
	}

	/** Reservas (de cualquier estado) de las pistas del club cuyo inicio cae en [desde, hasta). Una sola consulta. */
	@Query("""
			select r.pista.id as pistaId, r.inicio as inicio, r.estado as estado, r.precioTotal as precioTotal
			from Reserva r
			where r.pista.club.id = :clubId and r.inicio >= :desde and r.inicio < :hasta
			""")
	List<Fila> delClub(Long clubId, Instant desde, Instant hasta);
}
