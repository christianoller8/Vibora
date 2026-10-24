package com.vibora.incidencia;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IncidenciaFotoRepository extends JpaRepository<IncidenciaFoto, Long> {

	@EntityGraph(attributePaths = "subidaPor")
	List<IncidenciaFoto> findByIncidenciaIdOrderByIdAsc(Long incidenciaId);

	/** Filtra tambien por incidencia: una foto de otra incidencia es, a todos los efectos, inexistente (sin IDOR). */
	@EntityGraph(attributePaths = "subidaPor")
	Optional<IncidenciaFoto> findByIdAndIncidenciaId(Long id, Long incidenciaId);

	long countByIncidenciaId(Long incidenciaId);
}
