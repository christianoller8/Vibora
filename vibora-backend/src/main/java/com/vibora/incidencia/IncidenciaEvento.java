package com.vibora.incidencia;

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

import java.time.Instant;

/**
 * Entrada inmutable del historial de una incidencia. {@code estadoAnterior}/{@code estadoNuevo} guardan el nombre del
 * estado (eventos ESTADO y ASIGNADA) o de la prioridad (eventos PRIORIDAD).
 */
@Entity
@Table(name = "incidencia_evento")
@Getter
@Setter
@NoArgsConstructor
public class IncidenciaEvento {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "incidencia_id", nullable = false)
	private Incidencia incidencia;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "usuario_id", nullable = false)
	private Usuario usuario;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TipoEventoIncidencia tipo;

	@Column(name = "estado_anterior", length = 20)
	private String estadoAnterior;

	@Column(name = "estado_nuevo", length = 20)
	private String estadoNuevo;

	@Column(length = 2000)
	private String texto;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;
}
