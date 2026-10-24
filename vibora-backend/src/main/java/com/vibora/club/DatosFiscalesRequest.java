package com.vibora.club;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Datos fiscales del club. El NIF/CIF se valida (formato y digito de control) en el servicio. {@code tipoIva} es
 * opcional: si falta se conserva el actual (21 % para un club nuevo). El maximo es 99.99 porque la columna es
 * numeric(4, 2).
 */
public record DatosFiscalesRequest(
		@NotBlank @Size(max = 200) String razonSocial,
		@NotBlank @Size(max = 30) String nif,
		@NotBlank @Size(max = 300) String direccionFiscal,
		@DecimalMin("0") @DecimalMax("99.99") @Digits(integer = 2, fraction = 2) BigDecimal tipoIva) {
}
