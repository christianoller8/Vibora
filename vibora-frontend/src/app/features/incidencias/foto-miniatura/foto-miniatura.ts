import { Component, computed, inject, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { FotoIncidencia } from '../../../shared/models/incidencia.model';
import { FotoBlobCache } from '../foto-blob-cache';

/**
 * Una foto de incidencia. Los bytes llevan JWT, así que no se pueden pedir con `<img src>`: se leen de la
 * {@link FotoBlobCache} del ancestro que la provee (la página), que los descarga una vez y los muestra con una URL
 * de objeto.
 */
@Component({
  selector: 'app-foto-miniatura',
  imports: [MatButtonModule, MatProgressSpinnerModule],
  templateUrl: './foto-miniatura.html',
  styleUrl: './foto-miniatura.scss',
  host: { '[class.ampliada]': 'ampliada()' },
})
export class FotoMiniatura {
  private readonly cache = inject(FotoBlobCache);

  readonly incidenciaId = input.required<number>();
  readonly foto = input.required<FotoIncidencia>();
  /** A tamaño completo (visor) en vez de miniatura recortada. */
  readonly ampliada = input(false);

  protected readonly estado = computed(() =>
    this.cache.estado(this.incidenciaId(), this.foto().id)(),
  );

  protected reintentar(): void {
    this.cache.reintentar(this.incidenciaId(), this.foto().id);
  }
}
