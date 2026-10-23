package com.vibora.partido;

import com.vibora.reserva.Reserva;
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
 * Jugador de una reserva. El puesto (1..4) y que nadie ocupe dos puestos o repita puesto los garantiza la BD
 * (participante_puesto_ck, participante_puesto_uk, participante_usuario_uk).
 */
@Entity
@Table(name = "participante")
@Getter
@Setter
@NoArgsConstructor
public class Participante {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "reserva_id", nullable = false)
	private Reserva reserva;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "usuario_id", nullable = false)
	private Usuario usuario;

	@Column(nullable = false)
	private short puesto;

	@Column(name = "unido_en", nullable = false)
	private Instant unidoEn;
}
