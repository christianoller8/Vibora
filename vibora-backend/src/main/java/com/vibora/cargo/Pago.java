package com.vibora.cargo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** Pago SIMULADO (Bizum de mentira) de un cargo: no mueve dinero real. Un cargo, un pago (pago_cargo_uk). */
@Entity
@Table(name = "pago")
@Getter
@Setter
@NoArgsConstructor
public class Pago {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cargo_id", nullable = false)
	private Cargo cargo;

	@Column(nullable = false, precision = 8, scale = 2)
	private BigDecimal importe;

	/** Referencia simulada, p. ej. SIM-&lt;uuid&gt;. */
	@Column(nullable = false, length = 100)
	private String referencia;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	@PrePersist
	void alCrear() {
		if (creadoEn == null) {
			creadoEn = Instant.now();
		}
	}
}
