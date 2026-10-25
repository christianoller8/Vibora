import { ConfirmDialogData } from '../../../shared/components/confirm-dialog/confirm-dialog';

/** "1 reserva" / "3 reservas". */
export function describirReservas(cantidad: number): string {
  return cantidad === 1 ? '1 reserva' : `${cantidad} reservas`;
}

/** Confirmación de "Cancelar afectadas": dice cuántas son y qué pasa con los cargos y con los jugadores. */
export function datosConfirmacionCancelar(
  cantidad: number,
  pistaNombre: string,
): ConfirmDialogData {
  const verbo = cantidad === 1 ? 'Se cancelará' : 'Se cancelarán';
  return {
    titulo: 'Cancelar reservas afectadas',
    mensaje:
      `${verbo} ${describirReservas(cantidad)} de ${pistaNombre} afectadas por el bloqueo. ` +
      'Se anularán sus cargos (con reembolso si ya estaban pagados) y se avisará a los jugadores.',
    textoConfirmar: `Cancelar ${describirReservas(cantidad)}`,
    textoCancelar: 'Mantener',
  };
}

/** Aviso tras crear un bloqueo, con cuántas reservas existentes quedan afectadas. */
export function mensajePistaBloqueada(afectadas: number): string {
  if (afectadas === 0) {
    return 'Pista bloqueada. No hay reservas afectadas.';
  }
  const plural = afectadas === 1 ? '' : 's';
  return `Pista bloqueada. Hay ${describirReservas(afectadas)} afectada${plural} por el bloqueo.`;
}

/** Resultado de la cancelación; 0 es lo que da repetirla (o que otra persona se adelantara). */
export function mensajeCancelacion(canceladas: number): string {
  if (canceladas === 0) {
    return 'No había reservas afectadas.';
  }
  const verbo = canceladas === 1 ? 'Se canceló' : 'Se cancelaron';
  return `${verbo} ${describirReservas(canceladas)}. Se anularon sus cargos y se avisó a los jugadores.`;
}
