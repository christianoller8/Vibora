package com.vibora.bloqueo;

import java.util.List;

/** Respuesta al crear un bloqueo: el bloqueo y las reservas que ya existian en su rango (no se tocan). */
public record BloqueoCreadoResponse(BloqueoResponse bloqueo, List<ReservaAfectadaResponse> afectadas) {
}
