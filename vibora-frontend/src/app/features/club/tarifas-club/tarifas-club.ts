import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { mensajeDeError } from '../../../core/http/error.util';
import { formatearPrecio } from '../../../core/util/fecha';
import {
  ConfirmDialog,
  ConfirmDialogData,
} from '../../../shared/components/confirm-dialog/confirm-dialog';
import { aHoraCorta } from '../../../shared/forms/rango-horas';
import {
  DIAS_SEMANA,
  DiaSemana,
  ETIQUETA_DIA,
  ETIQUETA_TIPO_TARIFA,
  Tarifa,
} from '../../../shared/models/tarifa.model';
import { TarifaService } from '../tarifa.service';
import { TarifaDialog, TarifaDialogData } from './tarifa-dialog';

export interface DiaConTarifas {
  dia: DiaSemana;
  etiqueta: string;
  franjas: Tarifa[];
}

/** Agrupa por día (de lunes a domingo, solo los que tienen franjas) y ordena cada día por hora de inicio. */
export function agruparPorDia(tarifas: readonly Tarifa[]): DiaConTarifas[] {
  return DIAS_SEMANA.map((dia) => ({
    dia,
    etiqueta: ETIQUETA_DIA[dia],
    franjas: tarifas
      .filter((t) => t.diaSemana === dia)
      .sort((a, b) => aHoraCorta(a.horaInicio).localeCompare(aHoraCorta(b.horaInicio))),
  })).filter((grupo) => grupo.franjas.length > 0);
}

@Component({
  selector: 'app-tarifas-club',
  imports: [MatButtonModule, MatChipsModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './tarifas-club.html',
  styleUrl: './tarifas-club.scss',
})
export class TarifasClub implements OnInit {
  private readonly tarifaService = inject(TarifaService);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);

  readonly clubId = input.required<number>();

  protected readonly tarifas = signal<Tarifa[]>([]);
  protected readonly dias = computed(() => agruparPorDia(this.tarifas()));
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  /** Franja con un borrado en vuelo. */
  protected readonly eliminando = signal<number | null>(null);

  protected readonly etiquetaTipo = ETIQUETA_TIPO_TARIFA;
  protected readonly formatearPrecio = formatearPrecio;

  /** `clubId` es un input requerido: no se puede leer en el constructor, solo a partir de ngOnInit. */
  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(conSpinner = true): void {
    if (conSpinner) {
      this.cargando.set(true);
    }
    this.error.set(null);
    this.tarifaService.listar(this.clubId()).subscribe({
      next: (tarifas) => {
        this.tarifas.set(tarifas);
        this.cargando.set(false);
      },
      error: (err: unknown) => {
        this.error.set(mensajeDeError(err, 'No se pudieron cargar las tarifas.'));
        this.cargando.set(false);
      },
    });
  }

  /** "09:00 – 14:00" */
  protected horario(tarifa: Tarifa): string {
    return `${aHoraCorta(tarifa.horaInicio)} – ${aHoraCorta(tarifa.horaFin)}`;
  }

  /** "del lunes de 09:00 a 14:00": para las etiquetas accesibles de los botones de icono. */
  protected descripcion(tarifa: Tarifa): string {
    const dia = ETIQUETA_DIA[tarifa.diaSemana].toLowerCase();
    return `la tarifa del ${dia} de ${aHoraCorta(tarifa.horaInicio)} a ${aHoraCorta(tarifa.horaFin)}`;
  }

  protected nueva(): void {
    this.abrirFormulario({ clubId: this.clubId() }, 'Tarifa creada.');
  }

  protected editar(tarifa: Tarifa): void {
    this.abrirFormulario({ clubId: this.clubId(), tarifa }, 'Tarifa actualizada.');
  }

  protected eliminar(tarifa: Tarifa): void {
    if (this.eliminando() !== null) {
      return;
    }
    const datos: ConfirmDialogData = {
      titulo: 'Eliminar tarifa',
      mensaje:
        'Las casillas de esta franja dejarán de poder reservarse hasta que haya otra tarifa que las cubra. Las reservas ya hechas conservan su precio.',
      lineas: [
        { etiqueta: 'Día', valor: ETIQUETA_DIA[tarifa.diaSemana] },
        { etiqueta: 'Horario', valor: this.horario(tarifa) },
        { etiqueta: 'Precio', valor: `${formatearPrecio(tarifa.precioHora)}/h` },
      ],
      textoConfirmar: 'Eliminar',
      textoCancelar: 'Mantener',
    };
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, { data: datos })
      .afterClosed()
      .subscribe((confirmado) => {
        if (confirmado) {
          this.ejecutarBorrado(tarifa);
        }
      });
  }

  private ejecutarBorrado(tarifa: Tarifa): void {
    this.eliminando.set(tarifa.id);
    this.tarifaService.eliminar(this.clubId(), tarifa.id).subscribe({
      next: () => {
        this.eliminando.set(null);
        this.aviso('Tarifa eliminada.');
        this.cargar(false);
      },
      error: (err: unknown) => {
        this.eliminando.set(null);
        this.aviso(mensajeDeError(err, 'No se pudo eliminar la tarifa.'));
      },
    });
  }

  private abrirFormulario(datos: TarifaDialogData, exito: string): void {
    this.dialog
      .open<TarifaDialog, TarifaDialogData, Tarifa>(TarifaDialog, { data: datos })
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
