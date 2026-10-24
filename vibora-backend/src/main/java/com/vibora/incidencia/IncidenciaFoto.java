package com.vibora.incidencia;

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
 * Metadatos de una foto de incidencia; los bytes viven en el almacen de ficheros bajo {@code claveAlmacen} (un UUID
 * aleatorio generado por el servidor). {@code tipoContenido} y {@code tamano} describen el fichero YA saneado, no el
 * que envio el cliente; {@code nombreOriginal} es solo para mostrar.
 */
@Entity
@Table(name = "incidencia_foto")
@Getter
@Setter
@NoArgsConstructor
public class IncidenciaFoto {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "incidencia_id", nullable = false)
	private Incidencia incidencia;

	@Column(name = "clave_almacen", nullable = false, length = 80, updatable = false)
	private String claveAlmacen;

	@Column(name = "nombre_original", nullable = false)
	private String nombreOriginal;

	@Column(name = "tipo_contenido", nullable = false, length = 20)
	private String tipoContenido;

	@Column(nullable = false)
	private int tamano;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "subida_por", nullable = false)
	private Usuario subidaPor;

	@Column(name = "creada_en", nullable = false, updatable = false)
	private Instant creadaEn;
}
