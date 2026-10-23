package com.vibora.reserva;

import com.vibora.partido.Participante;
import com.vibora.pista.Pista;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Reserva de una pista. El solape entre reservas CONFIRMADAS lo impide la base de datos
 * (restriccion reserva_sin_solape en V1__init_schema.sql).
 */
@Entity
@Table(name = "reserva")
@Getter
@Setter
@NoArgsConstructor
public class Reserva {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pista_id", nullable = false)
	private Pista pista;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "usuario_id", nullable = false)
	private Usuario usuario;

	@Column(nullable = false)
	private Instant inicio;

	@Column(nullable = false)
	private Instant fin;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoReserva estado = EstadoReserva.CONFIRMADA;

	/** Precio congelado en el momento de reservar. */
	@Column(name = "precio_total", nullable = false, precision = 8, scale = 2)
	private BigDecimal precioTotal;

	/** Partido abierto: otros jugadores pueden apuntarse mientras queden plazas. */
	@Column(nullable = false)
	private boolean abierta = false;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	/**
	 * Jugadores (el organizador es el puesto 1). Lado inverso de solo lectura: las altas y bajas se hacen con
	 * ParticipanteRepository; los servicios mantienen esta coleccion en sincronia si ya esta cargada.
	 */
	@OneToMany(mappedBy = "reserva")
	@OrderBy("puesto")
	private List<Participante> participantes = new ArrayList<>();

	@PrePersist
	void alCrear() {
		if (creadoEn == null) {
			creadoEn = Instant.now();
		}
	}
}
