package com.vibora.cargo;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CargoRepository extends JpaRepository<Cargo, Long> {

	/** Con bloqueo de escritura de la fila (SELECT ... FOR UPDATE): serializa pagos simultaneos del mismo cargo. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Cargo c where c.id = :id")
	Optional<Cargo> findByIdParaActualizar(Long id);

	/** Reserva y partes de un cargo sin cargar la entidad (para decidir permisos y que bloquear primero). */
	@Query("select new com.vibora.cargo.CargoPartes(c.reserva.id, c.deudor.id, c.acreedor.id) from Cargo c where c.id = :id")
	Optional<CargoPartes> findPartesById(Long id);

	List<Cargo> findByReservaId(Long reservaId);

	/** El cargo "vivo" (PENDIENTE o PAGADO) de un jugador en una reserva; la BD garantiza que hay uno como mucho. */
	Optional<Cargo> findByReservaIdAndDeudorIdAndEstadoIn(Long reservaId, Long deudorId, Collection<EstadoCargo> estados);

	String LISTADO = "select c from Cargo c join fetch c.reserva r join fetch r.pista p join fetch p.club "
			+ "join fetch c.deudor join fetch c.acreedor ";

	@Query(LISTADO + "where c.deudor.id = :usuarioId order by c.creadoEn desc, c.id desc")
	List<Cargo> deudasDe(Long usuarioId);

	@Query(LISTADO + "where c.deudor.id = :usuarioId and c.estado = :estado order by c.creadoEn desc, c.id desc")
	List<Cargo> deudasDe(Long usuarioId, EstadoCargo estado);

	@Query(LISTADO + "where c.acreedor.id = :usuarioId order by c.creadoEn desc, c.id desc")
	List<Cargo> cobrosDe(Long usuarioId);

	@Query(LISTADO + "where c.acreedor.id = :usuarioId and c.estado = :estado order by c.creadoEn desc, c.id desc")
	List<Cargo> cobrosDe(Long usuarioId, EstadoCargo estado);

	/**
	 * Saldo con cada contraparte, calculado de golpe con los cargos PENDIENTES: [usuarioId, nombre, apellidos, debo,
	 * meDeben].
	 */
	@Query(nativeQuery = true, value = """
			select u.id, u.nombre, u.apellidos,
			       coalesce(sum(c.importe) filter (where c.deudor_id = :usuarioId), 0) as debo,
			       coalesce(sum(c.importe) filter (where c.acreedor_id = :usuarioId), 0) as me_deben
			from cargo c
			join usuario u on u.id = case when c.deudor_id = :usuarioId then c.acreedor_id else c.deudor_id end
			where c.estado = 'PENDIENTE' and (c.deudor_id = :usuarioId or c.acreedor_id = :usuarioId)
			group by u.id, u.nombre, u.apellidos
			order by u.apellidos, u.nombre, u.id
			""")
	List<Object[]> saldosDe(Long usuarioId);
}
