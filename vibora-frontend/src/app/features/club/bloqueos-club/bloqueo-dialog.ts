import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormBuilder,
  FormGroupDirective,
  NgForm,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { ErrorStateMatcher } from '@angular/material/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { mensajeDeError } from '../../../core/http/error.util';
import { madridAInstante } from '../../../core/util/fecha';
import {
  BloqueoCreado,
  BloqueoRequest,
  MAX_MOTIVO_BLOQUEO,
} from '../../../shared/models/bloqueo.model';
import { ESTADOS_TERMINALES, IncidenciaResumen } from '../../../shared/models/incidencia.model';
import { Pista } from '../../../shared/models/pista.model';
import { IncidenciaService } from '../../incidencias/incidencia.service';
import { BloqueoService } from '../bloqueo.service';

export interface BloqueoDialogData {
  clubId: number;
  pistas: Pista[];
  /** Pista preseleccionada. */
  pistaId: number;
}

/** Tope del listado de incidencias del backend (`LIMITE_MAXIMO`). */
const MAX_INCIDENCIAS = 100;

/** Vacío vale (campo opcional); si hay texto debe ser una hora que exista en Madrid. */
function fechaHoraMadrid(control: AbstractControl): ValidationErrors | null {
  const valor = String(control.value ?? '');
  return valor === '' || madridAInstante(valor) !== null ? null : { fechaInvalida: true };
}

/**
 * Validador de grupo: el fin debe ser posterior al inicio; si no hay inicio, el inicio es "ahora" (así lo interpreta el
 * backend). Con un campo ilegible no opina: de eso se encarga `fechaHoraMadrid`.
 */
function rangoFechas(grupo: AbstractControl): ValidationErrors | null {
  const hasta = String(grupo.get('hasta')?.value ?? '');
  if (hasta === '') {
    return null;
  }
  const desde = String(grupo.get('desde')?.value ?? '');
  const hastaIso = madridAInstante(hasta);
  const desdeIso = desde === '' ? new Date().toISOString() : madridAInstante(desde);
  if (hastaIso === null || desdeIso === null) {
    return null;
  }
  return Date.parse(hastaIso) > Date.parse(desdeIso) ? null : { rangoFechas: true };
}

/** Marca el campo de fin como erróneo cuando falla la regla del grupo (Material solo mira el propio control). */
class RangoFechasMatcher implements ErrorStateMatcher {
  isErrorState(control: AbstractControl | null, form: FormGroupDirective | NgForm | null): boolean {
    const rangoMal = form?.form.hasError('rangoFechas') ?? false;
    return !!control && (control.invalid || rangoMal) && (control.touched || !!form?.submitted);
  }
}

/**
 * Alta de un bloqueo de pista. Hace la petición él mismo para mostrar los 400/409 del backend sin cerrarse, y se cierra
 * con el bloqueo creado y las reservas que ya existían en su rango (no se tocan).
 */
@Component({
  selector: 'app-bloqueo-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  templateUrl: './bloqueo-dialog.html',
  styleUrl: './bloqueo-dialog.scss',
})
export class BloqueoDialog {
  protected readonly datos = inject<BloqueoDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<BloqueoDialog, BloqueoCreado>>(MatDialogRef);
  private readonly bloqueoService = inject(BloqueoService);
  private readonly incidenciaService = inject(IncidenciaService);

  protected readonly maxMotivo = MAX_MOTIVO_BLOQUEO;
  protected readonly matcherFin = new RangoFechasMatcher();

  protected readonly form = inject(FormBuilder).nonNullable.group(
    {
      pistaId: [String(this.datos.pistaId), [Validators.required]],
      motivo: [
        '',
        [Validators.required, Validators.pattern(/\S/), Validators.maxLength(MAX_MOTIVO_BLOQUEO)],
      ],
      desde: ['', [fechaHoraMadrid]],
      hasta: ['', [fechaHoraMadrid]],
      incidenciaId: [''],
    },
    { validators: [rangoFechas] },
  );

  private readonly incidencias = signal<IncidenciaResumen[]>([]);
  protected readonly errorIncidencias = signal<string | null>(null);
  private readonly pistaElegida = toSignal(this.form.controls.pistaId.valueChanges, {
    initialValue: this.form.controls.pistaId.value,
  });
  /** No terminales y coherentes con la pista elegida (las de otra pista las rechazaría el backend con 400). */
  protected readonly elegibles = computed(() => this.elegiblesPara(Number(this.pistaElegida())));

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.incidenciaService
      .listar(this.datos.clubId, { limit: MAX_INCIDENCIAS })
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: (lista) => this.incidencias.set(lista),
        error: () =>
          this.errorIncidencias.set(
            'No se pudieron cargar las incidencias. Puedes bloquear la pista sin asociar ninguna.',
          ),
      });

    // Si la incidencia elegida no encaja con la nueva pista, se deselecciona en vez de dejar un 400 esperando
    this.form.controls.pistaId.valueChanges.pipe(takeUntilDestroyed()).subscribe((pistaId) => {
      const elegida = this.form.controls.incidenciaId.value;
      if (
        elegida !== '' &&
        !this.elegiblesPara(Number(pistaId)).some((i) => String(i.id) === elegida)
      ) {
        this.form.controls.incidenciaId.setValue('');
      }
    });
  }

  protected etiquetaIncidencia(incidencia: IncidenciaResumen): string {
    return incidencia.pistaId === null
      ? `${incidencia.titulo} (sin pista concreta)`
      : incidencia.titulo;
  }

  protected etiquetaPista(pista: Pista): string {
    return pista.activa ? pista.nombre : `${pista.nombre} (de baja)`;
  }

  protected guardar(): void {
    this.form.updateValueAndValidity(); // "ahora" cambia con el tiempo: se revisa el rango al enviar
    if (this.form.invalid || this.guardando()) {
      this.form.markAllAsTouched();
      return;
    }
    const { pistaId, motivo, desde, hasta, incidenciaId } = this.form.getRawValue();
    const desdeIso = desde === '' ? null : madridAInstante(desde);
    const hastaIso = hasta === '' ? null : madridAInstante(hasta);
    const peticion: BloqueoRequest = {
      motivo: motivo.trim(),
      ...(desdeIso !== null ? { desde: desdeIso } : {}),
      ...(hastaIso !== null ? { hasta: hastaIso } : {}),
      ...(incidenciaId !== '' ? { incidenciaId: Number(incidenciaId) } : {}),
    };
    this.guardando.set(true);
    this.error.set(null);
    this.bloqueoService.crear(this.datos.clubId, Number(pistaId), peticion).subscribe({
      next: (creado) => this.dialogRef.close(creado),
      error: (err: unknown) => {
        this.error.set(mensajeDeError(err, 'No se pudo bloquear la pista. Inténtalo de nuevo.'));
        this.guardando.set(false);
      },
    });
  }

  protected cancelar(): void {
    this.dialogRef.close();
  }

  private elegiblesPara(pistaId: number): IncidenciaResumen[] {
    return this.incidencias().filter(
      (i) =>
        !ESTADOS_TERMINALES.includes(i.estado) && (i.pistaId === null || i.pistaId === pistaId),
    );
  }
}
