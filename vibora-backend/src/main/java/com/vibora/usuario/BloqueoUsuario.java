package com.vibora.usuario;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bloquea la fila de un usuario (SELECT ... FOR UPDATE) hasta el final de la transaccion y la RELEE de la BD.
 * Sirve para serializar operaciones que se deciden mirando el usuario (anadir/quitar personal, asignar una
 * incidencia, contar los reportes de una persona): quien llega despues espera y ve lo que dejo el primero.
 * La relectura (refresh) es imprescindible: una entidad ya cargada en la sesion no se actualiza con una consulta.
 */
@Component
@RequiredArgsConstructor
public class BloqueoUsuario {

	private final EntityManager em;

	@Transactional(propagation = Propagation.MANDATORY)
	public Usuario bloquear(Usuario usuario) {
		em.refresh(usuario, LockModeType.PESSIMISTIC_WRITE);
		return usuario;
	}
}
