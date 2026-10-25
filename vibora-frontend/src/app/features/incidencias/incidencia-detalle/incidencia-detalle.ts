import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, Injector, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  EMPTY,
  Observable,
  Subject,
  catchError,
  concatMap,
  debounceTime,
  filter,
  from,
  map,
  of,
  switchMap,
  toArray,
} from 'rxjs';
import { ClubService } from '../../../core/api/club.service';
import { AuthService } from '../../../core/auth/auth.service';
import { NotificacionesStore } from '../../../core/notificaciones/notificaciones.store';
import { formatearFechaHora } from '../../../core/util/fecha';
import { tiempoRelativo } from '../../../core/util/tiempo-relativo';
import {
  ConfirmDialog,
  ConfirmDialogData,
} from '../../../shared/components/confirm-dialog/confirm-dialog';
import { Insignia } from '../../../shared/components/insignia/insignia';
import { BloqueoCreado } from '../../../shared/models/bloqueo.model';
import {
  BloqueoActivo,
  ESTADOS_TERMINALES,
  ETIQUETA_CATEGORIA,
  ETIQUETA_ESTADO_INCIDENCIA,
  ETIQUETA_PRIORIDAD,
  EventoIncidencia,
  FotoIncidencia,
  IncidenciaDetalle,
  MAX_FOTOS_INCIDENCIA,
  TIPOS_FOTO_PERMITIDOS,
  TipoEventoIncidencia,
} from '../../../shared/models/incidencia.model';
import { TipoNotificacion } from '../../../shared/models/notificacion.model';
import { BloqueoService } from '../../club/bloqueo.service';
import { AccionesBloqueo, BloqueoRef } from '../../club/bloqueos-club/acciones-bloqueo';
import { mensajePistaBloqueada } from '../../club/bloqueos-club/afectadas-textos';
import { AsignarDialog, AsignarDialogData } from '../asignar-dialog/asignar-dialog';
import { EstadoDialog, EstadoDialogData } from '../estado-dialog/estado-dialog';
import { esConflictoDeVersion, mensajeDeAccion } from '../errores-incidencia';
import { FotoBlobCache } from '../foto-blob-cache';
import { FotoIncidenciaService } from '../foto-incidencia.service';
import { FotoMiniatura } from '../foto-miniatura/foto-miniatura';
import { FotoVisorDialog, FotoVisorDialogData } from '../foto-visor-dialog/foto-visor-dialog';
import {
  AccionIncidencia,
  accionesDisponibles,
  contextoDeIncidencia,
  puedeAdjuntarFotos,
  puedeComentar,
} from '../incidencia-estados';
import { IncidenciaService } from '../incidencia.service';
import { ResultadoAccion } from '../resultado-accion';
import { validarFotos } from '../validacion-fotos';

/** Notificaciones que cambian lo que muestra el detalle. */
const TIPOS_QUE_AFECTAN: ReadonlySet<TipoNotificacion> = new Set<TipoNotificacion>([
  'INCIDENCIA_NUEVA',
  'INCIDENCIA_ASIGNADA',
  'INCIDENCIA_ESTADO',
  'INCIDENCIA_COMENTARIO',
]);

/** Ventana para agrupar varios push seguidos en un único refresco. */
const RETARDO_REFRESCO_MS = 300;

const AVISO_CONFLICTO =
  'La incidencia fue modificada por otra persona; ya tienes la versión actual.';

interface PresentacionAccion {
  etiqueta: string;
  /** Aviso (snackbar) al aplicarse. */
  aviso: string;
}

const ACCIONES: Record<Exclude<AccionIncidencia, 'ASIGNAR'>, PresentacionAccion> = {
  INICIAR: { etiqueta: 'Marcar en curso', aviso: 'Incidencia marcada en curso.' },
  RESOLVER: { etiqueta: 'Resolver', aviso: 'Incidencia resuelta.' },
  CERRAR: { etiqueta: 'Cerrar', aviso: 'Incidencia cerrada.' },
  REABRIR: { etiqueta: 'Reabrir', aviso: 'Incidencia reabierta.' },
  RECHAZAR: { etiqueta: 'Rechazar', aviso: 'Incidencia rechazada.' },
};

const ICONO_EVENTO: Record<TipoEventoIncidencia, string> = {
  CREADA: 'flag',
  ASIGNADA: 'person_add',
  ESTADO: 'sync_alt',
  COMENTARIO: 'chat_bubble',
  PRIORIDAD: 'priority_high',
  FOTO_ANADIDA: 'add_a_photo',
  FOTO_ELIMINADA: 'no_photography',
  BLOQUEO_CREADO: 'block',
  BLOQUEO_LEVANTADO: 'lock_open',
};

const FRASE_EVENTO: Record<TipoEventoIncidencia, string> = {
  CREADA: 'reportó la incidencia',
  ASIGNADA: 'cambió la asignación',
  ESTADO: 'cambió el estado',
  COMENTARIO: 'comentó',
  PRIORIDAD: 'cambió la prioridad',
  FOTO_ANADIDA: 'añadió una foto',
  FOTO_ELIMINADA: 'eliminó una foto',
  BLOQUEO_CREADO: 'bloqueó la pista',
  BLOQUEO_LEVANTADO: 'levantó el bloqueo de la pista',
};

/**
 * Detalle de una incidencia: datos, fotos, historial, comentarios y las acciones que su rol permite en su estado
 * actual (ver incidencia-estados.ts). El backend es la autoridad: sus 403/409 se muestran tal cual, y un 409 de
 * "modificada por otra persona" recarga en silencio.
 */
@Component({
  selector: 'app-detalle-incidencia',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    FotoMiniatura,
    Insignia,
  ],
  // La caché de blobs vive lo que la página: al destruirse revoca las URLs de las fotos
  providers: [FotoBlobCache],
  templateUrl: './incidencia-detalle.html',
  styleUrl: './incidencia-detalle.scss',
})
export class DetalleIncidencia {
  private readonly route = inject(ActivatedRoute);
  private readonly incidenciaService = inject(IncidenciaService);
  private readonly fotoService = inject(FotoIncidenciaService);
  private readonly clubService = inject(ClubService);
  private readonly bloqueoService = inject(BloqueoService);
  protected readonly accionesBloqueo = inject(AccionesBloqueo);
  private readonly notificaciones = inject(NotificacionesStore);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly cache = inject(FotoBlobCache);
  private readonly injector = inject(Injector);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AuthService);

  protected readonly incidencia = signal<IncidenciaDetalle | null>(null);
  protected readonly clubNombre = signal<string | null>(null);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  private readonly id = signal<number | null>(null);
  protected readonly idValido = computed(() => this.id() !== null);
  private clubConsultado: number | null = null;

  protected readonly comentario = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.pattern(/\S/), Validators.maxLength(2000)],
  });
  protected readonly formComentario = new FormGroup({ comentario: this.comentario });
  protected readonly comentando = signal(false);
  protected readonly errorComentario = signal<string | null>(null);

  protected readonly subiendoFotos = signal(false);
  protected readonly erroresFotos = signal<string[]>([]);

  protected readonly etiquetaEstado = ETIQUETA_ESTADO_INCIDENCIA;
  protected readonly etiquetaPrioridad = ETIQUETA_PRIORIDAD;
  protected readonly etiquetaCategoria = ETIQUETA_CATEGORIA;
  protected readonly maxFotos = MAX_FOTOS_INCIDENCIA;
  protected readonly aceptarFotos = TIPOS_FOTO_PERMITIDOS.join(',');
  protected readonly exacta = formatearFechaHora;
  protected readonly relativa = tiempoRelativo;

  private readonly contexto = computed(() => {
    const incidencia = this.incidencia();
    return incidencia === null ? null : contextoDeIncidencia(incidencia, this.auth.usuario());
  });
  protected readonly acciones = computed(() => {
    const incidencia = this.incidencia();
    const contexto = this.contexto();
    return incidencia === null || contexto === null
      ? []
      : accionesDisponibles(incidencia.estado, contexto);
  });
  protected readonly puedeComentar = computed(() => {
    const incidencia = this.incidencia();
    return incidencia !== null && puedeComentar(incidencia.estado);
  });
  protected readonly puedeAnadirFotos = computed(() => {
    const incidencia = this.incidencia();
    return (
      incidencia !== null &&
      puedeAdjuntarFotos(incidencia.estado) &&
      incidencia.fotos.length < MAX_FOTOS_INCIDENCIA
    );
  });
  /** Bloqueo de pista ligado a la incidencia: informativo, solo para quien gestiona (admin y mantenimiento). */
  protected readonly bloqueo = computed(() => {
    const incidencia = this.incidencia();
    if (incidencia === null || incidencia.bloqueoActivo === null) {
      return null;
    }
    const gestiona =
      this.contexto()?.esAdmin === true ||
      (this.auth.rol() === 'MANTENIMIENTO' && this.auth.clubId() === incidencia.clubId);
    return gestiona ? incidencia.bloqueoActivo : null;
  });
  /** Gestionar el bloqueo (ver y cancelar afectadas, levantar, bloquear) es solo del administrador del club. */
  protected readonly puedeGestionarBloqueo = computed(
    () => this.bloqueo() !== null && this.contexto()?.esAdmin === true,
  );
  /** Bloquear la pista de la incidencia: con pista, sin estado terminal y sin otro bloqueo activo ya. */
  protected readonly puedeBloquear = computed(() => {
    const incidencia = this.incidencia();
    return (
      incidencia !== null &&
      this.contexto()?.esAdmin === true &&
      incidencia.pistaId !== null &&
      !ESTADOS_TERMINALES.includes(incidencia.estado) &&
      incidencia.bloqueoActivo === null
    );
  });
  protected readonly bloqueando = signal(false);

  private readonly cargar$ = new Subject<boolean>();

  constructor() {
    this.cargar$
      .pipe(
        switchMap((conSpinner) => this.consultar(conSpinner)),
        takeUntilDestroyed(),
      )
      .subscribe((incidencia) => this.aplicar(incidencia));

    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      const id = Number(params.get('id'));
      if (!Number.isInteger(id) || id <= 0) {
        this.id.set(null);
        this.cargando.set(false);
        this.error.set('La dirección de la incidencia no es válida.');
        return;
      }
      this.id.set(id);
      this.cargar$.next(true);
    });

    // Refresco silencioso (sin spinner, borrador de comentario intacto) agrupando ráfagas en una sola petición.
    this.notificaciones.recibidas$
      .pipe(
        filter((n) => TIPOS_QUE_AFECTAN.has(n.tipo)),
        debounceTime(RETARDO_REFRESCO_MS),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.cargar$.next(false));
  }

  protected cargar(): void {
    if (this.id() !== null) {
      this.cargar$.next(true);
    }
  }

  // ---- acciones de estado ----

  protected etiquetaAccion(accion: AccionIncidencia): string {
    if (accion === 'ASIGNAR') {
      return this.incidencia()?.asignadaA ? 'Reasignar' : 'Asignar';
    }
    return ACCIONES[accion].etiqueta;
  }

  protected ejecutar(accion: AccionIncidencia): void {
    const incidencia = this.incidencia();
    if (incidencia === null) {
      return;
    }
    if (accion === 'ASIGNAR') {
      const datos: AsignarDialogData = {
        incidenciaId: incidencia.id,
        clubId: incidencia.clubId,
        prioridad: incidencia.prioridad,
        asignadaA: incidencia.asignadaA,
      };
      this.dialog
        .open<AsignarDialog, AsignarDialogData, ResultadoAccion>(AsignarDialog, { data: datos })
        .afterClosed()
        .subscribe((resultado) => this.alCerrarDialogo(resultado, 'Incidencia asignada.'));
      return;
    }
    const datos: EstadoDialogData = { incidenciaId: incidencia.id, accion };
    this.dialog
      .open<EstadoDialog, EstadoDialogData, ResultadoAccion>(EstadoDialog, { data: datos })
      .afterClosed()
      .subscribe((resultado) => this.alCerrarDialogo(resultado, ACCIONES[accion].aviso));
  }

  private alCerrarDialogo(resultado: ResultadoAccion | undefined, aviso: string): void {
    if (resultado === undefined) {
      return;
    }
    if (resultado.tipo === 'ok') {
      this.aplicar(resultado.incidencia); // la respuesta ya trae el detalle completo y actualizado
      this.aviso(aviso);
    } else {
      this.recargarPorConflicto();
    }
  }

  // ---- comentarios ----

  protected comentar(): void {
    const incidencia = this.incidencia();
    if (incidencia === null || this.comentario.invalid || this.comentando()) {
      return;
    }
    this.comentando.set(true);
    this.errorComentario.set(null);
    this.incidenciaService.comentar(incidencia.id, this.comentario.value.trim()).subscribe({
      next: (actualizada) => {
        this.comentando.set(false);
        this.comentario.reset('');
        this.aplicar(actualizada);
      },
      error: (err: unknown) => {
        this.comentando.set(false);
        if (esConflictoDeVersion(err)) {
          this.recargarPorConflicto();
        } else {
          this.errorComentario.set(
            mensajeDeAccion(err, 'No se pudo enviar el comentario. Inténtalo de nuevo.'),
          );
        }
      },
    });
  }

  // ---- fotos ----

  protected puedeEliminarFoto(foto: FotoIncidencia): boolean {
    const incidencia = this.incidencia();
    if (incidencia === null || !puedeAdjuntarFotos(incidencia.estado)) {
      return false;
    }
    return this.contexto()?.esAdmin === true || foto.subidaPor.id === this.auth.usuario()?.id;
  }

  protected abrirFoto(indice: number): void {
    const incidencia = this.incidencia();
    if (incidencia === null) {
      return;
    }
    const datos: FotoVisorDialogData = {
      incidenciaId: incidencia.id,
      fotos: incidencia.fotos,
      indice,
    };
    this.dialog.open<FotoVisorDialog, FotoVisorDialogData>(FotoVisorDialog, {
      data: datos,
      injector: this.injector, // para que el visor use la misma caché de blobs que la página
      maxWidth: '95vw',
    });
  }

  protected eliminarFoto(foto: FotoIncidencia): void {
    const incidencia = this.incidencia();
    if (incidencia === null) {
      return;
    }
    const datos: ConfirmDialogData = {
      titulo: 'Eliminar foto',
      mensaje: 'La foto se borrará de la incidencia. No se puede deshacer.',
      lineas: [{ etiqueta: 'Foto', valor: foto.nombreOriginal }],
      textoConfirmar: 'Eliminar',
    };
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, { data: datos })
      .afterClosed()
      .subscribe((confirmado) => {
        if (confirmado) {
          this.ejecutarEliminacion(incidencia.id, foto);
        }
      });
  }

  protected alElegirFotos(evento: Event): void {
    const incidencia = this.incidencia();
    const entrada = evento.target as HTMLInputElement;
    const elegidos = Array.from(entrada.files ?? []);
    entrada.value = '';
    if (incidencia === null || this.subiendoFotos()) {
      return;
    }
    const { aceptadas, errores } = validarFotos(incidencia.fotos.length, elegidos);
    this.erroresFotos.set(errores);
    if (aceptadas.length > 0) {
      this.subirFotos(incidencia.id, aceptadas, errores);
    }
  }

  /** Una detrás de otra; las que fallen se listan con su motivo y no impiden subir las siguientes. */
  private subirFotos(incidenciaId: number, archivos: File[], erroresPrevios: string[]): void {
    this.subiendoFotos.set(true);
    from(archivos)
      .pipe(
        concatMap((archivo) =>
          this.fotoService.subir(incidenciaId, archivo).pipe(
            map((): string | null => null),
            catchError((err: unknown) =>
              of(`«${archivo.name}»: ${mensajeDeAccion(err, 'No se pudo subir la foto.')}`),
            ),
          ),
        ),
        toArray(),
      )
      .subscribe((resultados) => {
        this.subiendoFotos.set(false);
        const fallos = resultados.filter((r): r is string => r !== null);
        this.erroresFotos.set([...erroresPrevios, ...fallos]);
        this.cargar$.next(false); // trae las fotos nuevas y el historial
      });
  }

  private ejecutarEliminacion(incidenciaId: number, foto: FotoIncidencia): void {
    this.fotoService.eliminar(incidenciaId, foto.id).subscribe({
      next: () => {
        this.cache.olvidar(foto.id);
        this.cargar$.next(false);
      },
      error: (err: unknown) => {
        this.aviso(mensajeDeAccion(err, 'No se pudo eliminar la foto. Inténtalo de nuevo.'));
        this.cargar$.next(false);
      },
    });
  }

  // ---- bloqueo de la pista (administrador) ----

  protected bloquearPista(): void {
    const incidencia = this.incidencia();
    if (incidencia === null || this.bloqueando()) {
      return;
    }
    this.bloqueando.set(true);
    this.bloqueoService.bloquearPistaDeIncidencia(incidencia.id).subscribe({
      next: (creado) => {
        this.bloqueando.set(false);
        this.cargar$.next(false); // trae el aviso del bloqueo y el evento nuevo del historial
        this.alBloquear(incidencia, creado);
      },
      error: (err: unknown) => {
        this.bloqueando.set(false);
        this.aviso(mensajeDeAccion(err, 'No se pudo bloquear la pista. Inténtalo de nuevo.'));
        this.cargar$.next(false); // por si otra persona ya la bloqueó o cambió la incidencia
      },
    });
  }

  protected verAfectadas(): void {
    const referencia = this.referenciaBloqueo();
    if (referencia !== null) {
      this.accionesBloqueo.verAfectadas(referencia);
    }
  }

  protected cancelarAfectadas(): void {
    const referencia = this.referenciaBloqueo();
    if (referencia !== null) {
      this.accionesBloqueo
        .cancelarAfectadas(referencia)
        .subscribe((recargar) => this.recargarSi(recargar));
    }
  }

  protected levantarBloqueo(): void {
    const referencia = this.referenciaBloqueo();
    if (referencia !== null) {
      this.accionesBloqueo.levantar(referencia).subscribe((recargar) => this.recargarSi(recargar));
    }
  }

  private alBloquear(incidencia: IncidenciaDetalle, { bloqueo, afectadas }: BloqueoCreado): void {
    this.aviso(mensajePistaBloqueada(afectadas.length));
    if (afectadas.length === 0) {
      return;
    }
    this.accionesBloqueo.verAfectadas(
      {
        clubId: incidencia.clubId,
        pistaId: bloqueo.pistaId,
        pistaNombre: bloqueo.pistaNombre,
        bloqueoId: bloqueo.id,
      },
      afectadas,
    );
  }

  /** El bloqueo activo de la incidencia, con la pista y el club a los que pertenece. */
  private referenciaBloqueo(): BloqueoRef | null {
    const incidencia = this.incidencia();
    if (incidencia === null || incidencia.bloqueoActivo === null || incidencia.pistaId === null) {
      return null;
    }
    return {
      clubId: incidencia.clubId,
      pistaId: incidencia.pistaId,
      pistaNombre: incidencia.pistaNombre ?? 'la pista',
      bloqueoId: incidencia.bloqueoActivo.id,
      motivo: incidencia.bloqueoActivo.motivo,
    };
  }

  private recargarSi(recargar: boolean): void {
    if (recargar) {
      this.cargar$.next(false);
    }
  }

  // ---- historial ----

  protected textoBloqueo(bloqueo: BloqueoActivo): string {
    const hasta =
      bloqueo.hasta === null
        ? ', sin fecha de fin'
        : ` hasta el ${formatearFechaHora(bloqueo.hasta)}`;
    return `Pista bloqueada por esta incidencia desde el ${formatearFechaHora(bloqueo.desde)}${hasta}. Motivo: ${bloqueo.motivo}`;
  }

  /** Un tipo de evento que este frontend aún no conoce (de un backend más nuevo) cae en un icono genérico. */
  protected iconoDe(tipo: TipoEventoIncidencia): string {
    return ICONO_EVENTO[tipo] ?? 'info';
  }

  protected fraseDe(tipo: TipoEventoIncidencia): string {
    return FRASE_EVENTO[tipo] ?? 'actualizó la incidencia';
  }

  /** "Abierta → Asignada"; en los eventos de prioridad usa las etiquetas de prioridad. */
  protected cambio(evento: EventoIncidencia): string | null {
    const { estadoAnterior, estadoNuevo, tipo } = evento;
    if (estadoAnterior === null || estadoNuevo === null) {
      return null;
    }
    const etiquetas: Record<string, string> =
      tipo === 'PRIORIDAD' ? this.etiquetaPrioridad : this.etiquetaEstado;
    return `${etiquetas[estadoAnterior] ?? estadoAnterior} → ${etiquetas[estadoNuevo] ?? estadoNuevo}`;
  }

  // ---- carga ----

  private consultar(conSpinner: boolean): Observable<IncidenciaDetalle> {
    const id = this.id();
    if (id === null) {
      return EMPTY;
    }
    if (conSpinner) {
      this.cargando.set(true);
      this.error.set(null);
    }
    return this.incidenciaService.obtener(id).pipe(
      catchError((err: unknown) => {
        // Una recarga silenciosa que falla por red conserva lo que se veía; si la incidencia ya no es visible
        // (404/403) sí se dice.
        const noVisible =
          err instanceof HttpErrorResponse && (err.status === 404 || err.status === 403);
        if (conSpinner || noVisible) {
          this.error.set(mensajeDeAccion(err, 'No se pudo cargar la incidencia.'));
          this.incidencia.set(null);
          this.cargando.set(false);
        }
        return EMPTY;
      }),
    );
  }

  private aplicar(nueva: IncidenciaDetalle): void {
    const actuales = new Set(nueva.fotos.map((f) => f.id));
    this.incidencia()
      ?.fotos.filter((f) => !actuales.has(f.id))
      .forEach((f) => this.cache.olvidar(f.id));
    this.incidencia.set(nueva);
    this.error.set(null);
    this.cargando.set(false);
    this.cargarClub(nueva.clubId);
  }

  private cargarClub(clubId: number): void {
    if (this.clubConsultado === clubId) {
      return;
    }
    this.clubConsultado = clubId;
    this.clubService
      .obtener(clubId)
      .pipe(
        map((club) => club.nombre as string | null),
        catchError(() => of(null)), // el nombre es un adorno: sin él la página funciona igual
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((nombre) => this.clubNombre.set(nombre));
  }

  private recargarPorConflicto(): void {
    this.cargar$.next(false);
    this.aviso(AVISO_CONFLICTO);
  }

  private aviso(mensaje: string): void {
    this.snack.open(mensaje, 'Cerrar', { duration: 6000 });
  }
}
