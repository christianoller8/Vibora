import { Component, DestroyRef, computed, inject, input, model, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import {
  MAX_FOTOS_INCIDENCIA,
  TIPOS_FOTO_PERMITIDOS,
} from '../../../shared/models/incidencia.model';
import { validarFotos } from '../validacion-fotos';

/**
 * Elección de hasta 4 fotos antes de enviarlas (las sube el padre). Comprueba tipo y tamaño en el cliente, muestra
 * una miniatura de cada una (URL de objeto, revocada al quitarla y al destruirse) y deja quitarlas.
 */
@Component({
  selector: 'app-selector-fotos',
  imports: [MatButtonModule, MatIconModule],
  templateUrl: './selector-fotos.html',
  styleUrl: './selector-fotos.scss',
})
export class SelectorFotos {
  /** Fotos elegidas (enlace de doble sentido con el padre). */
  readonly archivos = model<File[]>([]);
  readonly deshabilitado = input(false);

  protected readonly errores = signal<string[]>([]);
  protected readonly maximo = MAX_FOTOS_INCIDENCIA;
  protected readonly aceptar = TIPOS_FOTO_PERMITIDOS.join(',');
  protected readonly lleno = computed(() => this.archivos().length >= MAX_FOTOS_INCIDENCIA);

  private readonly urls = new Map<File, string>();

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.urls.forEach((url) => URL.revokeObjectURL(url));
      this.urls.clear();
    });
  }

  protected alElegir(evento: Event): void {
    const entrada = evento.target as HTMLInputElement;
    const elegidos = Array.from(entrada.files ?? []);
    entrada.value = ''; // permite volver a elegir el mismo archivo
    const { aceptadas, errores } = validarFotos(this.archivos().length, elegidos);
    this.errores.set(errores);
    if (aceptadas.length > 0) {
      this.archivos.update((actuales) => [...actuales, ...aceptadas]);
    }
  }

  protected quitar(archivo: File): void {
    const url = this.urls.get(archivo);
    if (url !== undefined) {
      URL.revokeObjectURL(url);
      this.urls.delete(archivo);
    }
    this.errores.set([]);
    this.archivos.update((actuales) => actuales.filter((a) => a !== archivo));
  }

  /** URL de vista previa del archivo (se crea una vez y se revoca al quitarlo o destruir el componente). */
  protected urlDe(archivo: File): string {
    let url = this.urls.get(archivo);
    if (url === undefined) {
      url = URL.createObjectURL(archivo);
      this.urls.set(archivo, url);
    }
    return url;
  }
}
