package com.vibora.tarifa;

import com.vibora.club.Club;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;

@Entity
@Table(name = "tarifa_franja")
@Getter
@Setter
@NoArgsConstructor
public class TarifaFranja {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "club_id", nullable = false)
	private Club club;

	@Convert(converter = DiaSemanaConverter.class)
	@Column(name = "dia_semana", nullable = false)
	private DayOfWeek diaSemana;

	@Column(name = "hora_inicio", nullable = false)
	private LocalTime horaInicio;

	@Column(name = "hora_fin", nullable = false)
	private LocalTime horaFin;

	@Column(name = "precio_hora", nullable = false, precision = 8, scale = 2)
	private BigDecimal precioHora;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TipoTarifa tipo;
}
