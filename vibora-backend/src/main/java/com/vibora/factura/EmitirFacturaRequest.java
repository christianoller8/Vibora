package com.vibora.factura;

import jakarta.validation.constraints.Size;

/**
 * Receptor opcional de la factura. Todo es opcional: sin nombre se usa el del organizador de la reserva; un campo en
 * blanco equivale a no ponerlo. El NIF/CIF, si viene, se valida en el servicio.
 */
public record EmitirFacturaRequest(
		@Size(max = 200) String nombre,
		@Size(max = 30) String nif,
		@Size(max = 300) String direccion) {
}
