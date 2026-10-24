package com.vibora.club;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalTime;

@Entity
@Table(name = "club")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Club {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String nombre;

	private String direccion;

	private String ciudad;

	@Column(name = "hora_apertura", nullable = false)
	private LocalTime horaApertura;

	@Column(name = "hora_cierre", nullable = false)
	private LocalTime horaCierre;

	// ---- datos fiscales (para facturar); nulos hasta que el administrador los configura ----

	@Column(name = "razon_social", length = 200)
	private String razonSocial;

	@Column(length = 15)
	private String nif;

	@Column(name = "direccion_fiscal", length = 300)
	private String direccionFiscal;

	/** IVA incluido en los precios, en %. Por defecto el 21 %. */
	@Column(name = "tipo_iva", nullable = false, precision = 4, scale = 2)
	private BigDecimal tipoIva = new BigDecimal("21.00");

	/** true si ya tiene los tres datos fiscales: sin ellos no se puede emitir ninguna factura. */
	public boolean tieneDatosFiscales() {
		return razonSocial != null && nif != null && direccionFiscal != null;
	}
}
