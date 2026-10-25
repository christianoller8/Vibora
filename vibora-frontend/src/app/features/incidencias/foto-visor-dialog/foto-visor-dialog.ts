import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { FotoIncidencia } from '../../../shared/models/incidencia.model';
import { FotoMiniatura } from '../foto-miniatura/foto-miniatura';

export interface FotoVisorDialogData {
  incidenciaId: number;
  fotos: FotoIncidencia[];
  /** Foto con la que se abre. */
  indice: number;
}

/**
 * Visor de fotos a tamaño completo. Se abre con el `injector` de la página para que la foto salga de su misma
 * caché de blobs. Material ya lo cierra con Esc y mantiene el foco dentro; las flechas del teclado navegan.
 */
@Component({
  selector: 'app-foto-visor-dialog',
  imports: [MatButtonModule, MatDialogModule, MatIconModule, FotoMiniatura],
  templateUrl: './foto-visor-dialog.html',
  styleUrl: './foto-visor-dialog.scss',
  host: { '(keydown)': 'teclado($event)' },
})
export class FotoVisorDialog {
  protected readonly datos = inject<FotoVisorDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<FotoVisorDialog>>(MatDialogRef);

  protected readonly indice = signal(this.datos.indice);
  protected readonly foto = computed(() => this.datos.fotos[this.indice()]);

  protected anterior(): void {
    this.mover(-1);
  }

  protected siguiente(): void {
    this.mover(1);
  }

  protected teclado(evento: KeyboardEvent): void {
    if (this.datos.fotos.length < 2) {
      return;
    }
    if (evento.key === 'ArrowLeft') {
      this.anterior();
    } else if (evento.key === 'ArrowRight') {
      this.siguiente();
    }
  }

  protected cerrar(): void {
    this.dialogRef.close();
  }

  private mover(paso: number): void {
    const total = this.datos.fotos.length;
    this.indice.update((i) => (i + paso + total) % total);
  }
}
