package com.vibora.cargo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PagoRepository extends JpaRepository<Pago, Long> {

	/** Pagos de varios cargos en una sola consulta (para rellenar pagadoEn sin una consulta por cargo). */
	List<Pago> findByCargoIdIn(Collection<Long> cargoIds);
}
