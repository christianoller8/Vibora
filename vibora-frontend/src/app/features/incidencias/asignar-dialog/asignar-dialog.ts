import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { mensajeDeError } from '../../../core/http/error.util';
import {
  AsignarIncidenciaRequest,
  ETIQUETA_PRIORIDAD,
  MiembroPersonal,
  PRIORIDADES_INCIDENCIA,
  PersonaIncidencia,
  PrioridadIncidencia,
} from '../../../shared/models/incidencia.model';
import { PersonalService } from '../../club/personal.service';
import { esConflictoDeVersion, mensajeDeAccion } from '../errores-incidencia';
import { IncidenciaService } from '../incidencia.service';
import { ResultadoAccion } from '../resultado-accion';

export interface AsignarDialogData {
  incidenciaId: number;
  clubId: number;
  /** Prioridad actual: la lista arranca en ella y solo se envía si se cambia. */
  prioridad: PrioridadIncidencia;
  /** Responsable actual (si lo hay): se precarga al reasignar. */
  asignadaA: PersonaIncidencia | null;
}

const nombreCompleto = (p: MiembroPersonal): string => `${p.nombre ?? ''} ${p.apellidos}`.trim();

/** Asignar o reasignar una incidencia a alguien de mantenimiento del club, con prioridad opcional. */
@Component({
  selector: 'app-asignar-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './asignar-dialog.html',
  styleUrl: './asignar-dialog.scss',
})
export class AsignarDialog {
  protected readonly datos = inject<AsignarDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<AsignarDialog, ResultadoAccion>>(MatDialogRef);
  private readonly personalService = inject(PersonalService);
  private readonly incidenciaService = inject(IncidenciaService);

  protected readonly prioridades = PRIORIDADES_INCIDENCIA;
  protected readonly etiquetaPrioridad = ETIQUETA_PRIORIDAD;
  protected readonly nombreCompleto = nombreCompleto;

  protected readonly form = inject(FormBuilder).nonNullable.group({
    usuarioId: ['', [Validators.required]],
    prioridad: [this.datos.prioridad as PrioridadIncidencia],
  });
  protected readonly mantenimiento = signal<MiembroPersonal[]>([]);
  protected readonly cargando = signal(true);
  protected readonly errorCarga = signal<string | null>(null);
  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.personalService
      .listar(this.datos.clubId)
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: (personal) => {
          const delMantenimiento = personal.filter((p) => p.rol === 'MANTENIMIENTO');
          this.mantenimiento.set(delMantenimiento);
          this.cargando.set(false);
          const actual = this.datos.asignadaA;
          if (actual !== null && delMantenimiento.some((p) => p.usuarioId === actual.id)) {
            this.form.controls.usuarioId.setValue(String(actual.id));
          }
        },
        error: (err: unknown) => {
          this.errorCarga.set(mensajeDeError(err, 'No se pudo cargar el personal del club.'));
          this.cargando.set(false);
        },
      });
  }

  protected guardar(): void {
    if (this.form.invalid || this.enviando()) {
      this.form.markAllAsTouched();
      return;
    }
    const { usuarioId, prioridad } = this.form.getRawValue();
    const peticion: AsignarIncidenciaRequest = {
      usuarioId: Number(usuarioId),
      ...(prioridad !== this.datos.prioridad ? { prioridad } : {}),
    };
    this.enviando.set(true);
    this.error.set(null);
    this.incidenciaService.asignar(this.datos.incidenciaId, peticion).subscribe({
      next: (incidencia) => this.dialogRef.close({ tipo: 'ok', incidencia }),
      error: (err: unknown) => {
        const mensaje = mensajeDeAccion(
          err,
          'No se pudo asignar la incidencia. Inténtalo de nuevo.',
        );
        if (esConflictoDeVersion(err)) {
          this.dialogRef.close({ tipo: 'conflicto', mensaje });
          return;
        }
        this.error.set(mensaje);
        this.enviando.set(false);
      },
    });
  }

  protected cancelar(): void {
    this.dialogRef.close();
  }
}
