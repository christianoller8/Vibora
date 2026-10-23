package com.vibora.notificacion;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

	List<Notificacion> findByUsuarioIdOrderByCreadaEnDescIdDesc(Long usuarioId, Pageable pagina);

	List<Notificacion> findByUsuarioIdAndLeidaFalseOrderByCreadaEnDescIdDesc(Long usuarioId, Pageable pagina);

	long countByUsuarioIdAndLeidaFalse(Long usuarioId);

	Optional<Notificacion> findByIdAndUsuarioId(Long id, Long usuarioId);

	/** Marca de golpe todas las no leidas del usuario; devuelve cuantas cambiaron. */
	@Modifying(flushAutomatically = true)
	@Query("update Notificacion n set n.leida = true where n.usuario.id = :usuarioId and n.leida = false")
	int marcarTodasLeidas(Long usuarioId);
}
