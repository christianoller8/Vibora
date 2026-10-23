package com.vibora.partido;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ParticipanteRepository extends JpaRepository<Participante, Long> {

	boolean existsByReservaIdAndUsuarioId(Long reservaId, Long usuarioId);

	Optional<Participante> findByReservaIdAndUsuarioId(Long reservaId, Long usuarioId);

	/** Puestos (1..4) ya ocupados en la reserva. */
	@Query("select p.puesto from Participante p where p.reserva.id = :reservaId")
	List<Short> findPuestosOcupados(Long reservaId);

	/** Jugadores de la reserva con su usuario cargado (para avisarles a todos). */
	@EntityGraph(attributePaths = "usuario")
	List<Participante> findByReservaIdOrderByPuesto(Long reservaId);
}
