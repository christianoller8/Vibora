package com.vibora.bloqueo;

import java.util.List;

/** Reservas canceladas por mantenimiento en esta llamada (ids en orden ascendente). Repetir la llamada da 0. */
public record CancelacionAfectadasResponse(int canceladas, List<Long> ids) {
}
