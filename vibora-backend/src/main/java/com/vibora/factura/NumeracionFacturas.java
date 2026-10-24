package com.vibora.factura;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Numeracion consecutiva y SIN HUECOS de las facturas, por club y por anio.
 * <p>
 * Como: se bloquea la fila del contador (SELECT ... FOR UPDATE), se incrementa y se devuelve el numero, todo dentro de la
 * transaccion del llamador (se comprueba que hay una abierta: sin transaccion no hay numeracion posible; no se usa
 * @Transactional(MANDATORY) para que este componente no sea un proxy y los tests puedan espiarlo). El bloqueo dura
 * hasta el commit, asi que nadie mas toma un numero de ese club y anio mientras esta factura no se confirme. Y si la
 * transaccion se deshace por cualquier motivo, el incremento se deshace con ella: ese numero queda libre para la
 * siguiente. Por eso NO se usa una secuencia de PostgreSQL (nextval no se revierte y deja huecos).
 * <p>
 * Orden de bloqueos: este es siempre el ULTIMO bloqueo de fila que toma una transaccion (despues de la reserva y sus
 * cargos), y quien lo tiene no pide ya ninguna fila de reserva, de cargo, de pista ni de incidencia.
 */
@Component
@RequiredArgsConstructor
public class NumeracionFacturas {

	private final FacturaSerieRepository series;

	public int siguiente(Long clubId, int anio) {
		if (!TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException("La numeracion de facturas exige una transaccion abierta");
		}
		short anioCorto = (short) anio;
		series.crearSiNoExiste(clubId, anioCorto);
		FacturaSerie serie = series.findParaActualizar(clubId, anioCorto)
				.orElseThrow(() -> new IllegalStateException("No existe el contador de facturas"));
		serie.setUltimoNumero(serie.getUltimoNumero() + 1);
		series.saveAndFlush(serie);
		return serie.getUltimoNumero();
	}
}
