import { Injectable, OnDestroy, Signal, inject, signal, untracked } from '@angular/core';
import { Subscription } from 'rxjs';
import { FotoIncidenciaService } from './foto-incidencia.service';

export type EstadoFotoBlob =
  { tipo: 'cargando' } | { tipo: 'lista'; url: string } | { tipo: 'error' };

interface Entrada {
  incidenciaId: number;
  estado: ReturnType<typeof signal<EstadoFotoBlob>>;
  url: string | null;
  descarga: Subscription | null;
}

/**
 * Fotos autenticadas como URLs de objeto. Un `<img src>` no envía el JWT, así que cada foto se descarga con
 * HttpClient como blob y se muestra con `URL.createObjectURL`. Se pide UNA vez por foto, se guarda mientras viva
 * quien la provee (se declara en `providers` del componente) y, al destruirse, se revocan todas las URLs para
 * que el navegador libere los blobs.
 */
@Injectable()
export class FotoBlobCache implements OnDestroy {
  private readonly api = inject(FotoIncidenciaService);
  private readonly entradas = new Map<number, Entrada>();
  private destruida = false;

  /** Estado de la foto; la primera consulta lanza la descarga. */
  estado(incidenciaId: number, fotoId: number): Signal<EstadoFotoBlob> {
    const existente = this.entradas.get(fotoId);
    if (existente) {
      return existente.estado.asReadonly();
    }
    const entrada: Entrada = {
      incidenciaId,
      estado: signal<EstadoFotoBlob>({ tipo: 'cargando' }),
      url: null,
      descarga: null,
    };
    this.entradas.set(fotoId, entrada);
    // Suele llamarse desde un `computed` (la miniatura): lanzar la descarga fuera de su seguimiento, porque un
    // origen síncrono escribiría en el signal y Angular prohíbe escribir señales dentro de un `computed`.
    untracked(() => this.descargar(fotoId, entrada));
    return entrada.estado.asReadonly();
  }

  /** Vuelve a pedir una foto que falló. No hace nada si ya está cargada o cargando. */
  reintentar(incidenciaId: number, fotoId: number): void {
    const entrada = this.entradas.get(fotoId);
    if (!entrada || entrada.estado().tipo !== 'error') {
      return;
    }
    entrada.incidenciaId = incidenciaId;
    entrada.estado.set({ tipo: 'cargando' });
    this.descargar(fotoId, entrada);
  }

  /** Libera la foto (p. ej. se ha borrado): revoca su URL y cancela su descarga. */
  olvidar(fotoId: number): void {
    const entrada = this.entradas.get(fotoId);
    if (!entrada) {
      return;
    }
    this.liberar(entrada);
    this.entradas.delete(fotoId);
  }

  ngOnDestroy(): void {
    this.destruida = true;
    this.entradas.forEach((entrada) => this.liberar(entrada));
    this.entradas.clear();
  }

  private descargar(fotoId: number, entrada: Entrada): void {
    entrada.descarga = this.api.obtenerBlob(entrada.incidenciaId, fotoId).subscribe({
      next: (blob) => {
        if (this.destruida || this.entradas.get(fotoId) !== entrada) {
          return;
        }
        entrada.url = URL.createObjectURL(blob);
        entrada.estado.set({ tipo: 'lista', url: entrada.url });
      },
      error: () => {
        if (!this.destruida && this.entradas.get(fotoId) === entrada) {
          entrada.estado.set({ tipo: 'error' });
        }
      },
    });
  }

  private liberar(entrada: Entrada): void {
    entrada.descarga?.unsubscribe();
    if (entrada.url !== null) {
      URL.revokeObjectURL(entrada.url);
      entrada.url = null;
    }
  }
}
