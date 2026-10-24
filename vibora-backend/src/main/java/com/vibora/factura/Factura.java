package com.vibora.factura;

import com.vibora.club.Club;
import com.vibora.reserva.Reserva;
import com.vibora.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Factura emitida (simulada). INMUTABLE: la base de datos rechaza cualquier UPDATE o DELETE con un trigger
 * (V5__facturacion.sql) y aqui se marca @Immutable para que Hibernate ni lo intente. Los datos del emisor y del receptor
 * son una COPIA del momento de emitirla: cambiar despues los datos fiscales del club no altera las facturas ya emitidas.
 */
@Entity
@Table(name = "factura")
@Immutable
@Getter
@Setter
@NoArgsConstructor
public class Factura {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "club_id", nullable = false, updatable = false)
	private Club club;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "reserva_id", nullable = false, updatable = false)
	private Reserva reserva;

	/** Quien recibe la factura: el organizador de la reserva. */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "receptor_usuario_id", nullable = false, updatable = false)
	private Usuario receptorUsuario;

	@Column(nullable = false, length = 10, updatable = false)
	private String serie;

	@Column(nullable = false, updatable = false)
	private int numero;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20, updatable = false)
	private TipoFactura tipo;

	/** Solo en las rectificativas: la factura original que anulan. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "rectifica_a", updatable = false)
	private Factura rectificaA;

	@Column(name = "emitida_en", nullable = false, updatable = false)
	private Instant emitidaEn;

	@Column(name = "emisor_razon_social", nullable = false, length = 200, updatable = false)
	private String emisorRazonSocial;

	@Column(name = "emisor_nif", nullable = false, length = 15, updatable = false)
	private String emisorNif;

	@Column(name = "emisor_direccion", nullable = false, length = 300, updatable = false)
	private String emisorDireccion;

	@Column(name = "receptor_nombre", nullable = false, length = 200, updatable = false)
	private String receptorNombre;

	@Column(name = "receptor_nif", length = 15, updatable = false)
	private String receptorNif;

	@Column(name = "receptor_direccion", length = 300, updatable = false)
	private String receptorDireccion;

	@Column(nullable = false, length = 300, updatable = false)
	private String concepto;

	@Column(nullable = false, precision = 10, scale = 2, updatable = false)
	private BigDecimal base;

	@Column(name = "tipo_iva", nullable = false, precision = 4, scale = 2, updatable = false)
	private BigDecimal tipoIva;

	@Column(name = "cuota_iva", nullable = false, precision = 10, scale = 2, updatable = false)
	private BigDecimal cuotaIva;

	@Column(nullable = false, precision = 10, scale = 2, updatable = false)
	private BigDecimal total;

	/** "F2026-0001" */
	public String numeroCompleto() {
		return NumeroFactura.completo(serie, numero);
	}
}
