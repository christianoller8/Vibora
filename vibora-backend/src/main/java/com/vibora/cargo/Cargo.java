package com.vibora.cargo;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lo que el deudor (jugador que se apunta) debe al acreedor (organizador, que paga al club). Un solo cargo vivo
 * (PENDIENTE o PAGADO) por jugador y reserva: indice cargo_vivo_uk.
 */
@Entity
@Table(name = "cargo")
@Getter
@Setter
@NoArgsConstructor
public class Cargo {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "reserva_id", nullable = false)
	private Reserva reserva;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "deudor_id", nullable = false)
	private Usuario deudor;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "acreedor_id", nullable = false)
	private Usuario acreedor;

	@Column(nullable = false, precision = 8, scale = 2)
	private BigDecimal importe;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoCargo estado = EstadoCargo.PENDIENTE;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	@PrePersist
	void alCrear() {
		if (creadoEn == null) {
			creadoEn = Instant.now();
		}
	}
}
