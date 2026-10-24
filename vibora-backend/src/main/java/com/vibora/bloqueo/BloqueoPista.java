package com.vibora.bloqueo;

import com.vibora.incidencia.Incidencia;
import com.vibora.pista.Pista;
import com.vibora.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.Instant;

/**
 * Bloqueo de una pista por mantenimiento: no admite reservas NUEVAS en {@code [desde, hasta)} ({@code hasta} nulo =
 * sin fin). Las reservas que ya existian no se tocan: las decide el administrador. Esta ACTIVO mientras no se levante
 * ({@code levantadoEn} nulo) y no haya caducado ({@code hasta} nulo o futuro).
 */
@Entity
@Table(name = "bloqueo_pista")
@Getter
@Setter
@NoArgsConstructor
public class BloqueoPista {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pista_id", nullable = false)
	private Pista pista;

	/** Incidencia que lo motiva, si la hay: al resolverla, cerrarla o rechazarla el bloqueo se levanta solo. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "incidencia_id")
	private Incidencia incidencia;

	@Column(nullable = false, length = 300)
	private String motivo;

	@Column(nullable = false)
	private Instant desde;

	@Column
	private Instant hasta;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "creado_por", nullable = false)
	private Usuario creadoPor;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	@Column(name = "levantado_en")
	private Instant levantadoEn;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "levantado_por")
	private Usuario levantadoPor;

	/** Activo en el instante dado: sin levantar y sin caducar. */
	public boolean estaActivoEn(Instant ahora) {
		return levantadoEn == null && (hasta == null || hasta.isAfter(ahora));
	}
}
