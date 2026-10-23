package com.vibora.usuario;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	Optional<Usuario> findByEmail(String email);

	/** Usuario con su club en la misma consulta (evita una carga perezosa aparte para el perfil). */
	@EntityGraph(attributePaths = "club")
	@Query("select u from Usuario u where u.email = :email")
	Optional<Usuario> findConClubByEmail(@Param("email") String email);

	boolean existsByEmail(String email);
}
