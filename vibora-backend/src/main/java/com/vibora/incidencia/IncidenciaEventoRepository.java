package com.vibora.incidencia;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidenciaEventoRepository extends JpaRepository<IncidenciaEvento, Long> {

	/** Historial en orden cronologico (el id desempata eventos del mismo instante), con su autor. */
	@EntityGraph(attributePaths = "usuario")
	List<IncidenciaEvento> findByIncidenciaIdOrderByCreadoEnAscIdAsc(Long incidenciaId);
}
