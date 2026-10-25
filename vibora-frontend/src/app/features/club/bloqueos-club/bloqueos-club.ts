import { Component, OnInit, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { EMPTY, Observable, Subject, catchError, merge, switchMap } from 'rxjs';
import { ClubService } from '../../../core/api/club.service';
import { mensajeDeError } from '../../../core/http/error.util';
import { formatearFechaHora } from '../../../core/util/fecha';
import {
  BloqueoCreado,
  BloqueoPista,
  ETIQUETA_ESTADO_BLOQUEO,
  ETIQUETA_FILTRO_BLOQUEOS,
  EstadoBloqueo,
  FILTROS_BLOQUEOS,
  FiltroBloqueos,
} from '../../../shared/models/bloqueo.model';
import { Pista } from '../../../shared/models/pista.model';
import { BloqueoService } from '../bloqueo.service';
import { AccionesBloqueo, BloqueoRef } from './acciones-bloqueo';
import { mensajePistaBloqueada } from './afectadas-textos';
import { BloqueoDialog, BloqueoDialogData } from './bloqueo-dialog';
import { estadoBloqueo } from './bloqueo-estado';

/** Qué pide cada filtro al backend: `activos` es tri-estado (ausente = todos). */
const ACTIVOS_DE_FILTRO: Record<FiltroBloqueos, boolean | undefined> = {
  ACTIVOS: true,
  LEVANTADOS: false,
  TODOS: undefined,
};

const MENSAJE_VACIO: Record<FiltroBloqueos, string> = {
  ACTIVOS: 'No hay bloqueos activos en esta pista.',
  LEVANTADOS: 'No hay bloqueos levantados ni caducados en esta pista.',
  TODOS: 'Esta pista no tiene bloqueos.',
};

/**
 * Bloqueos de pista por mantenimiento: una pista cada vez, filtrable por estado, con las acciones del administrador
 * (ver y cancelar las reservas afectadas, levantar) y el alta de nuevos bloqueos.
 */
@Component({
  selector: 'app-bloqueos-club',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatChipsModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatTableModule,
  ],
  templateUrl: './bloqueos-club.html',
  styleUrl: './bloqueos-club.scss',
})
export class BloqueosClub implements OnInit {
  private readonly clubService = inject(ClubService);
  private readonly bloqueoService = inject(BloqueoService);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  protected readonly acciones = inject(AccionesBloqueo);

  readonly clubId = input.required<number>();

  readonly pistaCtrl = new FormControl<number | null>(null);
  readonly filtroCtrl = new FormControl<FiltroBloqueos>('ACTIVOS', { nonNullable: true });

  protected readonly pistas = signal<Pista[]>([]);
  protected readonly cargandoPistas = signal(true);
  protected readonly errorPistas = signal<string | null>(null);
  protected readonly bloqueos = signal<BloqueoPista[]>([]);
  protected readonly cargando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly filtros = FILTROS_BLOQUEOS;
  protected readonly etiquetaFiltro = ETIQUETA_FILTRO_BLOQUEOS;
  protected readonly etiquetaEstado = ETIQUETA_ESTADO_BLOQUEO;
  protected readonly mensajeVacio = MENSAJE_VACIO;
  protected readonly formatearFechaHora = formatearFechaHora;
  protected readonly columnas = ['pista', 'motivo', 'vigencia', 'estado', 'creador', 'acciones'];

  /** Emite `true` si la carga debe mostrar el spinner (entrada, cambio de pista o de filtro) o `false` si es un refresco. */
  private readonly cargar$ = new Subject<boolean>();

  constructor() {
    this.cargar$
      .pipe(
        switchMap((conSpinner) => this.consultar(conSpinner)),
        takeUntilDestroyed(),
      )
      .subscribe((bloqueos) => {
        this.bloqueos.set(bloqueos);
        this.cargando.set(false);
      });

    merge(this.pistaCtrl.valueChanges, this.filtroCtrl.valueChanges)
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.cargar$.next(true));
  }

  /** `clubId` es un input requerido: no se puede leer en el constructor, solo a partir de ngOnInit. */
  ngOnInit(): void {
    this.cargarPistas();
  }

  protected cargarPistas(): void {
    this.cargandoPistas.set(true);
    this.errorPistas.set(null);
    this.clubService.pistas(this.clubId()).subscribe({
      next: (pistas) => {
        this.pistas.set(pistas);
        this.cargandoPistas.set(false);
        if (pistas.length > 0) {
          this.pistaCtrl.setValue(pistas[0].id); // dispara la carga de sus bloqueos
        }
      },
      error: (err: unknown) => {
        this.errorPistas.set(mensajeDeError(err, 'No se pudieron cargar las pistas.'));
        this.cargandoPistas.set(false);
      },
    });
  }

  protected recargar(): void {
    this.cargar$.next(true);
  }

  protected etiquetaPista(pista: Pista): string {
    return pista.activa ? pista.nombre : `${pista.nombre} (de baja)`;
  }

  protected estado(bloqueo: BloqueoPista): EstadoBloqueo {
    return estadoBloqueo(bloqueo);
  }

  protected vigencia(bloqueo: BloqueoPista): string {
    const hasta =
      bloqueo.hasta === null ? 'hasta que se resuelva' : formatearFechaHora(bloqueo.hasta);
    return `${formatearFechaHora(bloqueo.desde)} → ${hasta}`;
  }

  /** Nombre accesible de un botón de la fila: empieza por su texto visible y concreta de qué bloqueo habla. */
  protected etiquetaAccion(accion: string, bloqueo: BloqueoPista): string {
    return `${accion} del bloqueo «${bloqueo.motivo}» de ${bloqueo.pistaNombre}`;
  }

  protected etiquetaAccionLevantar(bloqueo: BloqueoPista): string {
    return `Levantar bloqueo «${bloqueo.motivo}» de ${bloqueo.pistaNombre}`;
  }

  // ---- acciones ----

  protected verAfectadas(bloqueo: BloqueoPista): void {
    this.acciones.verAfectadas(this.referencia(bloqueo));
  }

  protected cancelarAfectadas(bloqueo: BloqueoPista): void {
    this.acciones
      .cancelarAfectadas(this.referencia(bloqueo))
      .subscribe((recargar) => this.recargarSi(recargar));
  }

  protected levantar(bloqueo: BloqueoPista): void {
    this.acciones
      .levantar(this.referencia(bloqueo))
      .subscribe((recargar) => this.recargarSi(recargar));
  }

  protected nuevo(): void {
    const pistaId = this.pistaCtrl.value;
    if (pistaId === null) {
      return;
    }
    const datos: BloqueoDialogData = { clubId: this.clubId(), pistas: this.pistas(), pistaId };
    this.dialog
      .open<BloqueoDialog, BloqueoDialogData, BloqueoCreado>(BloqueoDialog, {
        data: datos,
        maxWidth: '95vw',
      })
      .afterClosed()
      .subscribe((creado) => {
        if (creado) {
          this.alCrear(creado);
        }
      });
  }

  private alCrear(creado: BloqueoCreado): void {
    const { bloqueo, afectadas } = creado;
    // El nuevo bloqueo tiene que verse: en su pista y entre los activos
    const cambiaVista =
      this.pistaCtrl.value !== bloqueo.pistaId || this.filtroCtrl.value === 'LEVANTADOS';
    this.pistaCtrl.setValue(bloqueo.pistaId, { emitEvent: false });
    if (this.filtroCtrl.value === 'LEVANTADOS') {
      this.filtroCtrl.setValue('ACTIVOS', { emitEvent: false });
    }
    this.cargar$.next(cambiaVista);

    this.aviso(mensajePistaBloqueada(afectadas.length));
    if (afectadas.length === 0) {
      return;
    }
    this.acciones.verAfectadas(this.referencia(bloqueo), afectadas);
  }

  private referencia(bloqueo: BloqueoPista): BloqueoRef {
    return {
      clubId: this.clubId(),
      pistaId: bloqueo.pistaId,
      pistaNombre: bloqueo.pistaNombre,
      bloqueoId: bloqueo.id,
      motivo: bloqueo.motivo,
    };
  }

  private recargarSi(recargar: boolean): void {
    if (recargar) {
      this.cargar$.next(false);
    }
  }

  private consultar(conSpinner: boolean): Observable<BloqueoPista[]> {
    const pistaId = this.pistaCtrl.value;
    if (pistaId === null) {
      return EMPTY;
    }
    if (conSpinner) {
      this.cargando.set(true);
    }
    this.error.set(null);
    return this.bloqueoService
      .listar(this.clubId(), pistaId, ACTIVOS_DE_FILTRO[this.filtroCtrl.value])
      .pipe(
        catchError((err: unknown) => {
          this.error.set(mensajeDeError(err, 'No se pudieron cargar los bloqueos.'));
          this.cargando.set(false);
          return EMPTY;
        }),
      );
  }

  private aviso(mensaje: string): void {
    this.snack.open(mensaje, 'Cerrar', { duration: 6000 });
  }
}
