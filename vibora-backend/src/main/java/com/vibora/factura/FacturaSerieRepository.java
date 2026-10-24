package com.vibora.factura;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface FacturaSerieRepository extends JpaRepository<FacturaSerie, FacturaSerieId> {

	/**
	 * Crea el contador (a cero) si todavia no existe. Una alta simultanea de otra transaccion espera aqui a que esta
	 * termine (confirme o se deshaga) en vez de fallar.
	 */
	@Modifying
	@Query(nativeQuery = true, value = """
			insert into factura_serie (club_id, anio, ultimo_numero) values (:clubId, :anio, 0)
			on conflict (club_id, anio) do nothing
			""")
	void crearSiNoExiste(Long clubId, short anio);

	/** SELECT ... FOR UPDATE del contador: las demas transacciones del mismo club y anio esperan hasta el commit. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from FacturaSerie s where s.id.clubId = :clubId and s.id.anio = :anio")
	Optional<FacturaSerie> findParaActualizar(Long clubId, Short anio);
}
