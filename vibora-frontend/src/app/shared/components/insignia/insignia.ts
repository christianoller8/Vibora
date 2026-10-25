import { Component, input } from '@angular/core';

/**
 * Etiqueta de solo lectura (estado, prioridad...). La `variante` se añade como clase `variante-<x>` para
 * colorearla desde aquí; el color nunca es lo único que la distingue: lleva siempre su texto.
 */
@Component({
  selector: 'app-insignia',
  templateUrl: './insignia.html',
  styleUrl: './insignia.scss',
})
export class Insignia {
  readonly texto = input.required<string>();
  readonly variante = input.required<string>();
}
