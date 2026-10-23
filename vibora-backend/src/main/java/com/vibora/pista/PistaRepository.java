package com.vibora.pista;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PistaRepository extends JpaRepository<Pista, Long> {

	List<Pista> findByClubIdOrderByNombre(Long clubId);

	List<Pista> findByClubIdAndActivaTrueOrderByNombre(Long clubId);

	Optional<Pista> findByIdAndClubId(Long id, Long clubId);

	boolean existsByClubIdAndNombre(Long clubId, String nombre);

	boolean existsByClubIdAndNombreAndIdNot(Long clubId, String nombre, Long id);
}
