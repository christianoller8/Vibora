import { Component, OnInit, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSlideToggleChange, MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { ClubService } from '../../../core/api/club.service';
import { mensajeDeError } from '../../../core/http/error.util';
import {
  ConfirmDialog,
  ConfirmDialogData,
} from '../../../shared/components/confirm-dialog/confirm-dialog';
import { ETIQUETA_TIPO_PISTA, Pista, TipoPista } from '../../../shared/models/pista.model';
import { PistaService } from '../pista.service';
import { PistaDialog, PistaDialogData } from './pista-dialog';

function sinElemento(ids: ReadonlySet<number>, id: number): ReadonlySet<number> {
  const copia = new Set(ids);
  copia.delete(id);
  return copia;
}

@Component({
  selector: 'app-pistas-club',
  imports: [
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSlideToggleModule,
    MatTableModule,
  ],
  templateUrl: './pistas-club.html',
  styleUrl: './pistas-club.scss',
})
export class PistasClub implements OnInit {
  private readonly clubService = inject(ClubService);
  private readonly pistaService = inject(PistaService);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);

  readonly clubId = input.required<number>();

  protected readonly pistas = signal<Pista[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  /** Pistas con un cambio de «activa» o una baja en vuelo (se bloquean sus controles). */
  protected readonly ocupadas = signal<ReadonlySet<number>>(new Set());

  protected readonly columnas = ['nombre', 'tipo', 'activa', 'acciones'];

  protected etiquetaDeTipo(tipo: TipoPista): string {
    return ETIQUETA_TIPO_PISTA[tipo];
  }

  /** `clubId` es un input requerido: no se puede leer en el constructor, solo a partir de ngOnInit. */
  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(conSpinner = true): void {
    if (conSpinner) {
      this.cargando.set(true);
    }
    this.error.set(null);
    this.clubService.pistas(this.clubId()).subscribe({
      next: (pistas) => {
        this.pistas.set(pistas);
        this.cargando.set(false);
      },
      error: (err: unknown) => {
        this.error.set(mensajeDeError(err, 'No se pudieron cargar las pistas.'));
        this.cargando.set(false);
      },
    });
  }

  protected nueva(): void {
    this.abrirFormulario({ clubId: this.clubId() }, 'Pista creada.');
  }

  protected editar(pista: Pista): void {
    this.abrirFormulario({ clubId: this.clubId(), pista }, 'Pista actualizada.');
  }

  /** El backend solo cambia `activa` si viene en el PUT, así que se reenvían nombre y tipo tal cual. */
  protected alternarActiva(pista: Pista, evento: MatSlideToggleChange): void {
    if (this.ocupadas().has(pista.id)) {
      return;
    }
    this.ocupadas.update((ids) => new Set(ids).add(pista.id));
    this.pistaService
      .actualizar(this.clubId(), pista.id, {
        nombre: pista.nombre,
        tipo: pista.tipo,
        activa: evento.checked,
      })
      .subscribe({
        next: (actualizada) => {
          this.ocupadas.update((ids) => sinElemento(ids, pista.id));
          this.pistas.update((lista) => lista.map((p) => (p.id === pista.id ? actualizada : p)));
        },
        error: (err: unknown) => {
          this.ocupadas.update((ids) => sinElemento(ids, pista.id));
          // el interruptor ya cambió por dentro y el estado no: se restaura explícitamente
          evento.source.checked = pista.activa;
          this.aviso(mensajeDeError(err, 'No se pudo cambiar el estado de la pista.'));
        },
      });
  }

  protected darDeBaja(pista: Pista): void {
    if (this.ocupadas().has(pista.id)) {
      return;
    }
    const datos: ConfirmDialogData = {
      titulo: 'Dar de baja la pista',
      mensaje:
        'La pista dejará de poder reservarse. Las reservas que ya tiene se mantienen y puedes volver a activarla cuando quieras.',
      lineas: [{ etiqueta: 'Pista', valor: pista.nombre }],
      textoConfirmar: 'Dar de baja',
      textoCancelar: 'Mantener',
    };
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, { data: datos })
      .afterClosed()
      .subscribe((confirmado) => {
        if (confirmado) {
          this.ejecutarBaja(pista);
        }
      });
  }

  private ejecutarBaja(pista: Pista): void {
    this.ocupadas.update((ids) => new Set(ids).add(pista.id));
    this.pistaService.darDeBaja(this.clubId(), pista.id).subscribe({
      next: () => {
        this.ocupadas.update((ids) => sinElemento(ids, pista.id));
        this.aviso(`Pista «${pista.nombre}» dada de baja.`);
        this.cargar(false);
      },
      error: (err: unknown) => {
        this.ocupadas.update((ids) => sinElemento(ids, pista.id));
        this.aviso(mensajeDeError(err, 'No se pudo dar de baja la pista.'));
      },
    });
  }

  private abrirFormulario(datos: PistaDialogData, exito: string): void {
    this.dialog
      .open<PistaDialog, PistaDialogData, Pista>(PistaDialog, { data: datos })
      .afterClosed()
      .subscribe((guardada) => {
        if (guardada) {
          this.aviso(exito);
          this.cargar(false);
        }
      });
  }

  private aviso(mensaje: string): void {
    this.snack.open(mensaje, 'Cerrar', { duration: 6000 });
  }
}
