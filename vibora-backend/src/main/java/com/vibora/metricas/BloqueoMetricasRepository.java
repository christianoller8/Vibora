package com.vibora.metricas;

import com.vibora.bloqueo.BloqueoPista;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.time.Instant;
import java.util.List;

/** Lectura de bloqueos para las metricas de ocupacion. */
interface BloqueoMetricasRepository extends Repository<BloqueoPista, Long> {

	interface Fila {
		Long getPistaId();

		Instant getDesde();

		Instant getHasta();

		Instant getLevantadoEn();
	}

	/**
	 * Bloqueos (activos, levantados o caducados: es historia) de las pistas del club cuyo rango efectivo
	 * [desde, least(hasta, levantadoEn)) solapa con [desde, hasta) del periodo consultado. Una sola consulta.
	 */
	@Query("""
			select b.pista.id as pistaId, b.desde as desde, b.hasta as hasta, b.levantadoEn as levantadoEn
			from BloqueoPista b
			where b.pista.club.id = :clubId and b.desde < :hasta
			  and (b.hasta is null or b.hasta > :desde)
			  and (b.levantadoEn is null or b.levantadoEn > :desde)
			""")
	List<Fila> delClub(Long clubId, Instant desde, Instant hasta);
}
