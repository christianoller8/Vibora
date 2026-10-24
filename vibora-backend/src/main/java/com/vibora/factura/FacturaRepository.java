package com.vibora.factura;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FacturaRepository extends JpaRepository<Factura, Long> {

	/** Totales de un conjunto de facturas (sumas nulas si el conjunto esta vacio). */
	interface Totales {
		long getCantidad();

		BigDecimal getBase();

		BigDecimal getCuotaIva();

		BigDecimal getTotal();
	}

	/** La factura ORIGINAL de la reserva, si ya se emitio (la BD garantiza que hay una como mucho). */
	Optional<Factura> findByReservaIdAndTipo(Long reservaId, TipoFactura tipo);

	/** Ya existe la rectificativa de esa factura (la BD garantiza una como mucho). */
	boolean existsByRectificaAId(Long facturaId);

	/** Original y rectificativa de la reserva, en orden de emision. */
	List<Factura> findByReservaIdOrderByIdAsc(Long reservaId);

	/** Facturas de las que el usuario es receptor, las mas recientes primero. */
	@Query("select f from Factura f where f.receptorUsuario.id = :usuarioId order by f.emitidaEn desc, f.id desc")
	List<Factura> deReceptor(Long usuarioId, Pageable pagina);

	String FILTRO_CLUB = "from Factura f where f.club.id = :clubId and f.emitidaEn >= :desde and f.emitidaEn < :hasta "
			+ "and f.tipo in :tipos";

	/**
	 * Facturas del club emitidas en [desde, hasta), de los tipos dados, las mas recientes primero. Sin parametros nulos
	 * (PostgreSQL no los tipa): el llamador pasa fechas extremas y todos los tipos cuando no hay filtro. Solo se leen
	 * identificadores de las relaciones, asi que no hay una consulta por factura.
	 */
	@Query("select f " + FILTRO_CLUB + " order by f.emitidaEn desc, f.id desc")
	List<Factura> delClub(Long clubId, Instant desde, Instant hasta, Collection<TipoFactura> tipos, Pageable pagina);

	/** Cantidad y sumas de TODO el conjunto filtrado (no solo de la pagina). */
	@Query("select count(f) as cantidad, sum(f.base) as base, sum(f.cuotaIva) as cuotaIva, sum(f.total) as total "
			+ FILTRO_CLUB)
	Totales totalesDelClub(Long clubId, Instant desde, Instant hasta, Collection<TipoFactura> tipos);
}
