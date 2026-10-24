package com.vibora.pista;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PistaRepository extends JpaRepository<Pista, Long> {

	List<Pista> findByClubIdOrderByNombre(Long clubId);

	List<Pista> findByClubIdAndActivaTrueOrderByNombre(Long clubId);

	Optional<Pista> findByIdAndClubId(Long id, Long clubId);

	/**
	 * Con bloqueo COMPARTIDO de la fila (SELECT ... FOR SHARE). Lo toma quien reserva ANTES de mirar los bloqueos de la
	 * pista: las reservas entre si siguen en paralelo (el bloqueo compartido no choca con el compartido), pero ninguna
	 * puede estar a medias mientras se crea un bloqueo, que pide el bloqueo exclusivo de la misma fila.
	 */
	@Lock(LockModeType.PESSIMISTIC_READ)
	@Query("select p from Pista p where p.id = :id and p.club.id = :clubId")
	Optional<Pista> findByIdAndClubIdParaCompartir(Long id, Long clubId);

	/**
	 * Con bloqueo EXCLUSIVO de la fila (PESSIMISTIC_WRITE; en PostgreSQL con Hibernate es FOR NO KEY UPDATE, que no
	 * choca con las comprobaciones de clave foranea de otras inserciones). Lo toma quien crea un bloqueo ANTES de
	 * calcular las reservas afectadas: espera a las reservas en curso y las que lleguen despues esperan a que termine,
	 * y entonces ya ven el bloqueo.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Pista p where p.id = :id")
	Optional<Pista> findByIdParaActualizar(Long id);

	boolean existsByClubIdAndNombre(Long clubId, String nombre);

	boolean existsByClubIdAndNombreAndIdNot(Long clubId, String nombre, Long id);
}
