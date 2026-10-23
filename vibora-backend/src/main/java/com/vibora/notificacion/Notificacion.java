package com.vibora.notificacion;

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

import java.time.Instant;

/** Aviso persistido para un usuario. Se crea siempre a traves de NotificacionService.notificar. */
@Entity
@Table(name = "notificacion")
@Getter
@Setter
@NoArgsConstructor
public class Notificacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "usuario_id", nullable = false)
	private Usuario usuario;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private TipoNotificacion tipo;

	@Column(nullable = false, length = 500)
	private String mensaje;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reserva_id")
	private Reserva reserva;

	@Column(nullable = false)
	private boolean leida = false;

	@Column(name = "creada_en", nullable = false, updatable = false)
	private Instant creadaEn;

	@PrePersist
	void alCrear() {
		if (creadaEn == null) {
			creadaEn = Instant.now();
		}
	}
}
