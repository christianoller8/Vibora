package com.vibora.incidencia;

import com.vibora.club.Club;
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
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Problema reportado en un club. Nunca se borra. {@code @Version} da bloqueo optimista: dos cambios simultaneos
 * sobre la misma incidencia no se pisan (el segundo falla y se traduce a 409). Las reglas de estado viven en
 * IncidenciaService; la BD solo garantiza que ASIGNADA/EN_CURSO tengan responsable (incidencia_asignada_ck).
 */
@Entity
@Table(name = "incidencia")
@Getter
@Setter
@NoArgsConstructor
public class Incidencia {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "club_id", nullable = false)
	private Club club;

	/** Opcional; si existe, pertenece al club (lo comprueba el servicio). */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "pista_id")
	private Pista pista;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "reportada_por", nullable = false)
	private Usuario reportadaPor;

	@Column(nullable = false, length = 120)
	private String titulo;

	@Column(nullable = false, length = 2000)
	private String descripcion;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private CategoriaIncidencia categoria;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PrioridadIncidencia prioridad = PrioridadIncidencia.MEDIA;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoIncidencia estado = EstadoIncidencia.ABIERTA;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "asignada_a")
	private Usuario asignadaA;

	@Version
	@Column(nullable = false)
	private Long version;

	@Column(name = "creada_en", nullable = false, updatable = false)
	private Instant creadaEn;

	@Column(name = "actualizada_en", nullable = false)
	private Instant actualizadaEn;

	@Column(name = "resuelta_en")
	private Instant resueltaEn;
}
