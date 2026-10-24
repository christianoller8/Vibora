package com.vibora.incidencia;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IncidenciaRepository extends JpaRepository<Incidencia, Long> {

	/** Con club, pista, quien la reporto y el responsable en la misma consulta. */
	@EntityGraph(attributePaths = { "club", "pista", "reportadaPor", "asignadaA" })
	@Query("select i from Incidencia i where i.id = :id")
	Optional<Incidencia> buscarCompleta(Long id);

	/** Incidencias del reportero en el club cuyo estado NO esta en la lista (se usa con los estados terminales). */
	long countByReportadaPorIdAndClubIdAndEstadoNotIn(Long reportadaPorId, Long clubId,
			Collection<EstadoIncidencia> estados);

	List<Incidencia> findByAsignadaAIdAndEstadoIn(Long usuarioId, Collection<EstadoIncidencia> estados);

	/**
	 * Listado del club. {@code todas}: ve todo (administrador). Si no, solo las que reporto el usuario y, si
	 * {@code mantenimiento}, tambien las que tiene asignadas. Orden: URGENTE primero, luego las mas nuevas. Los
	 * joins son a-uno, asi que la paginacion se hace en la BD y no hay una consulta por fila.
	 */
	@EntityGraph(attributePaths = { "pista", "reportadaPor", "asignadaA" })
	@Query("""
			select i from Incidencia i
			where i.club.id = :clubId
			  and (:todas = true or i.reportadaPor.id = :usuarioId
			       or (:mantenimiento = true and i.asignadaA.id = :usuarioId))
			  and (:estado is null or i.estado = :estado)
			  and (:pistaId is null or i.pista.id = :pistaId)
			  and (:asignadaA is null or i.asignadaA.id = :asignadaA)
			order by case i.prioridad
			           when com.vibora.incidencia.PrioridadIncidencia.URGENTE then 4
			           when com.vibora.incidencia.PrioridadIncidencia.ALTA then 3
			           when com.vibora.incidencia.PrioridadIncidencia.MEDIA then 2
			           else 1 end desc,
			         i.creadaEn desc, i.id desc
			""")
	List<Incidencia> listar(Long clubId, boolean todas, Long usuarioId, boolean mantenimiento, EstadoIncidencia estado,
			Long pistaId, Long asignadaA, Pageable pagina);
}
