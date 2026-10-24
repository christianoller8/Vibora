package com.vibora.factura;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Contador de facturas de un club y un anio. Se lee con SELECT ... FOR UPDATE y se incrementa en la MISMA transaccion
 * que inserta la factura (ver NumeracionFacturas): si la transaccion se deshace, el contador vuelve atras con ella.
 */
@Entity
@Table(name = "factura_serie")
@Getter
@Setter
@NoArgsConstructor
public class FacturaSerie {

	@EmbeddedId
	private FacturaSerieId id;

	@Column(name = "ultimo_numero", nullable = false)
	private int ultimoNumero;
}
