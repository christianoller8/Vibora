package com.vibora.bloqueo;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BloqueoPistaRepository extends JpaRepository<BloqueoPista, Long> {

	/** Activo = sin levantar y (sin fin o con fin futuro). Su rango es [desde, coalesce(hasta, infinito)). */
	String ACTIVO = "b.levantadoEn is null and (b.hasta is null or b.hasta > :ahora)";

	/** Hay un bloqueo ACTIVO de la pista que solapa [inicio, fin). */
	@Query("select count(b) > 0 from BloqueoPista b where b.pista.id = :pistaId and " + ACTIVO
			+ " and b.desde < :fin and (b.hasta is null or b.hasta > :inicio)")
	boolean haySolape(Long pistaId, Instant inicio, Instant fin, Instant ahora);

	/** Bloqueos ACTIVOS de la pista que solapan [desde, hasta) (para marcar la agenda de un dia). */
	@Query("select b from BloqueoPista b where b.pista.id = :pistaId and " + ACTIVO
			+ " and b.desde < :hasta and (b.hasta is null or b.hasta > :desde)")
	List<BloqueoPista> activosEntre(Long pistaId, Instant desde, Instant hasta, Instant ahora);

	/** El bloqueo activo de una incidencia, si lo hay (para su detalle). */
	@Query("select b from BloqueoPista b where b.incidencia.id = :incidenciaId and " + ACTIVO)
	Optional<BloqueoPista> findActivoDeIncidencia(Long incidenciaId, Instant ahora);

	/**
	 * El bloqueo sin levantar de una incidencia (activo o ya caducado), con bloqueo de escritura de la fila: serializa
	 * dos levantamientos simultaneos del mismo bloqueo.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from BloqueoPista b where b.incidencia.id = :incidenciaId and b.levantadoEn is null")
	Optional<BloqueoPista> findSinLevantarDeIncidenciaParaActualizar(Long incidenciaId);

	/** Con bloqueo de escritura de la fila (levantar dos veces a la vez no duplica eventos). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from BloqueoPista b where b.id = :id and b.pista.id = :pistaId")
	Optional<BloqueoPista> findByIdAndPistaIdParaActualizar(Long id, Long pistaId);

	@EntityGraph(attributePaths = { "creadoPor", "levantadoPor", "incidencia" })
	@Query("select b from BloqueoPista b where b.pista.id = :pistaId order by b.desde desc, b.id desc")
	List<BloqueoPista> listarDeLaPista(Long pistaId);

	Optional<BloqueoPista> findByIdAndPistaId(Long id, Long pistaId);
}
