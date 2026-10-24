package com.vibora.usuario;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	Optional<Usuario> findByEmail(String email);

	/** Usuario con su club en la misma consulta (evita una carga perezosa aparte para el perfil). */
	@EntityGraph(attributePaths = "club")
	@Query("select u from Usuario u where u.email = :email")
	Optional<Usuario> findConClubByEmail(@Param("email") String email);

	boolean existsByEmail(String email);

	/** Administradores de un club (a quienes avisar de incidencias nuevas). */
	List<Usuario> findByClubIdAndRol(Long clubId, Rol rol);

	/** Personal del club con alguno de los roles dados: administradores primero, luego por apellidos. */
	@Query("""
			select u from Usuario u
			where u.club.id = :clubId and u.rol in :roles
			order by case when u.rol = com.vibora.usuario.Rol.ADMIN_CLUB then 0 else 1 end, u.apellidos, u.id
			""")
	List<Usuario> findPersonalDelClub(Long clubId, Collection<Rol> roles);
}
