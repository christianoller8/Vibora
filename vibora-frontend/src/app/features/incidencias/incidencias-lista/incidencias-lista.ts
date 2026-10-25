import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatChipListbox, MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import {
  EMPTY,
  Observable,
  Subject,
  catchError,
  debounceTime,
  filter,
  map,
  of,
  switchMap,
} from 'rxjs';
import { ClubService } from '../../../core/api/club.service';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/http/error.util';
import { NotificacionesStore } from '../../../core/notificaciones/notificaciones.store';
import { formatearFechaHora } from '../../../core/util/fecha';
import { tiempoRelativo } from '../../../core/util/tiempo-relativo';
import { Insignia } from '../../../shared/components/insignia/insignia';
import { Club } from '../../../shared/models/club.model';
import {
  ESTADOS_INCIDENCIA,
  ETIQUETA_CATEGORIA,
  ETIQUETA_ESTADO_INCIDENCIA,
  ETIQUETA_PRIORIDAD,
  EstadoIncidencia,
  FiltrosIncidencias,
  IncidenciaResumen,
  MiembroPersonal,
} from '../../../shared/models/incidencia.model';
import { TipoNotificacion } from '../../../shared/models/notificacion.model';
import { Pista } from '../../../shared/models/pista.model';
import { PersonalService } from '../../club/personal.service';
import { elegirClubInicial, guardarClubPreferido, leerClubPreferido } from '../club-preferido';
import { IncidenciaService } from '../incidencia.service';

/** Notificaciones que cambian lo que muestra el listado (una incidencia nueva, asignada, con otro estado o comentario). */
const TIPOS_QUE_AFECTAN: ReadonlySet<TipoNotificacion> = new Set<TipoNotificacion>([
  'INCIDENCIA_NUEVA',
  'INCIDENCIA_ASIGNADA',
  'INCIDENCIA_ESTADO',
  'INCIDENCIA_COMENTARIO',
]);

/** Ventana para agrupar varios push seguidos en un único refresco. */
const RETARDO_REFRESCO_MS = 300;
const POR_PAGINA = 20;
/** Tope del backend por petición (`LIMITE_MAXIMO`). */
const LIMITE_MAXIMO = 100;

/** Valor de la opción "Sin asignar" del filtro de asignación (el resto son ids de personas). */
const SIN_ASIGNAR = 'SIN';

type Modo = 'inicial' | 'silenciosa' | 'mas';

interface Pagina {
  modo: Modo;
  datos: IncidenciaResumen[];
  limite: number;
}

/**
 * Incidencias de un club. El backend filtra por visibilidad (administrador: todas; mantenimiento: las asignadas;
 * resto: las propias), así que aquí solo se elige club y filtros. Se refresca en silencio cuando llega un aviso.
 */
@Component({
  selector: 'app-incidencias-lista',
  imports: [
    RouterLink,
    MatButtonModule,
    MatChipsModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    Insignia,
  ],
  templateUrl: './incidencias-lista.html',
  styleUrl: './incidencias-lista.scss',
})
export class IncidenciasLista {
  private readonly clubService = inject(ClubService);
  private readonly incidenciaService = inject(IncidenciaService);
  private readonly personalService = inject(PersonalService);
  private readonly notificaciones = inject(NotificacionesStore);
  private readonly snack = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly auth = inject(AuthService);

  protected readonly clubes = signal<Club[]>([]);
  protected readonly clubId = signal<number | null>(null);
  protected readonly estado = signal<EstadoIncidencia | null>(null);
  protected readonly pistaId = signal<number | null>(null);
  /** '' = todas; 'SIN' = sin asignar (en el cliente); si no, el id de la persona de mantenimiento. */
  protected readonly asignacion = signal('');

  protected readonly pistas = signal<Pista[]>([]);
  protected readonly mantenimiento = signal<MiembroPersonal[]>([]);
  protected readonly items = signal<IncidenciaResumen[]>([]);
  protected readonly hayMas = signal(false);

  protected readonly cargandoClubes = signal(true);
  protected readonly errorClubes = signal<string | null>(null);
  protected readonly cargando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly cargandoMas = signal(false);

  protected readonly estados = ESTADOS_INCIDENCIA;
  protected readonly etiquetaEstado = ETIQUETA_ESTADO_INCIDENCIA;
  protected readonly etiquetaPrioridad = ETIQUETA_PRIORIDAD;
  protected readonly etiquetaCategoria = ETIQUETA_CATEGORIA;
  protected readonly exacta = formatearFechaHora;
  protected readonly relativa = tiempoRelativo;
  protected readonly sinAsignar = SIN_ASIGNAR;

  /** Administrador del club que se está viendo: solo él tiene filtros de pista y de asignación. */
  protected readonly esAdminDelClub = computed(
    () => this.auth.esAdminClub() && this.auth.clubId() === this.clubId(),
  );
  private readonly esMantenimientoDelClub = computed(
    () => this.auth.rol() === 'MANTENIMIENTO' && this.auth.clubId() === this.clubId(),
  );
  protected readonly visibles = computed(() =>
    this.asignacion() === SIN_ASIGNAR
      ? this.items().filter((i) => i.asignadaA === null)
      : this.items(),
  );
  protected readonly hayFiltros = computed(
    () => this.estado() !== null || this.pistaId() !== null || this.asignacion() !== '',
  );
  protected readonly mensajeVacio = computed(() => {
    if (this.hayFiltros()) {
      return 'Ninguna incidencia coincide con los filtros.';
    }
    if (this.esAdminDelClub()) {
      return 'No hay incidencias en este club.';
    }
    return this.esMantenimientoDelClub()
      ? 'No tienes incidencias asignadas en este club.'
      : 'No tienes incidencias en este club.';
  });

  private readonly cargar$ = new Subject<Modo>();

  constructor() {
    this.cargar$
      .pipe(
        switchMap((modo) => this.consultar(modo)),
        takeUntilDestroyed(),
      )
      .subscribe((pagina) => this.aplicar(pagina));

    // Refresco silencioso (sin spinner, filtros intactos) agrupando ráfagas en una sola petición.
    this.notificaciones.recibidas$
      .pipe(
        filter((n) => TIPOS_QUE_AFECTAN.has(n.tipo)),
        debounceTime(RETARDO_REFRESCO_MS),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.cargar$.next('silenciosa'));

    this.cargarClubes();
  }

  protected elegirClub(valor: string): void {
    const id = Number(valor);
    if (!Number.isInteger(id) || id === this.clubId()) {
      return;
    }
    guardarClubPreferido(id);
    this.fijarClub(id);
  }

  protected elegirEstado(
    valor: EstadoIncidencia | 'TODAS' | undefined,
    lista: MatChipListbox,
  ): void {
    if (valor === undefined) {
      lista.value = this.estado() ?? 'TODAS'; // pulsar el chip ya elegido no lo deselecciona
      return;
    }
    const nuevo = valor === 'TODAS' ? null : valor;
    if (nuevo !== this.estado()) {
      this.estado.set(nuevo);
      this.cargar();
    }
  }

  protected elegirPista(valor: string): void {
    this.pistaId.set(valor === '' ? null : Number(valor));
    this.cargar();
  }

  protected elegirAsignacion(valor: string): void {
    const antes = this.asignacion();
    this.asignacion.set(valor);
    // "Sin asignar" se resuelve en el cliente (el backend no filtra por ausencia de responsable): solo hay que
    // pedir al servidor cuando se entra o se sale de un filtro por persona
    const deServidor = (v: string): boolean => v !== '' && v !== SIN_ASIGNAR;
    if (deServidor(antes) || deServidor(valor)) {
      this.cargar();
    }
  }

  protected valorDe(evento: Event): string {
    return (evento.target as HTMLSelectElement).value;
  }

  protected cargar(): void {
    this.cargar$.next('inicial');
  }

  protected cargarMas(): void {
    if (!this.cargandoMas() && this.hayMas()) {
      this.cargar$.next('mas');
    }
  }

  protected nombreCompleto(p: MiembroPersonal): string {
    return `${p.nombre ?? ''} ${p.apellidos}`.trim();
  }

  private cargarClubes(): void {
    this.clubService.listar().subscribe({
      next: (clubes) => {
        this.clubes.set(clubes);
        this.cargandoClubes.set(false);
        const inicial = elegirClubInicial(clubes, this.auth.clubId(), leerClubPreferido());
        if (inicial !== null) {
          this.fijarClub(inicial);
        }
      },
      error: (err: unknown) => {
        this.cargandoClubes.set(false);
        this.errorClubes.set(mensajeDeError(err, 'No se pudieron cargar los clubes.'));
      },
    });
  }

  /** Cambia de club: los filtros propios del club (pista, responsable) se reinician y se recarga todo. */
  private fijarClub(id: number): void {
    this.clubId.set(id);
    this.pistaId.set(null);
    this.asignacion.set('');
    this.pistas.set([]);
    this.mantenimiento.set([]);
    if (this.esAdminDelClub()) {
      this.clubService
        .pistas(id)
        .pipe(
          catchError(() => of<Pista[]>([])),
          takeUntilDestroyed(this.destroyRef),
        )
        .subscribe((pistas) => id === this.clubId() && this.pistas.set(pistas));
      this.personalService
        .listar(id)
        .pipe(
          catchError(() => of<MiembroPersonal[]>([])),
          takeUntilDestroyed(this.destroyRef),
        )
        .subscribe(
          (personal) =>
            id === this.clubId() &&
            this.mantenimiento.set(personal.filter((p) => p.rol === 'MANTENIMIENTO')),
        );
    }
    this.cargar();
  }

  private consultar(modo: Modo): Observable<Pagina> {
    if (modo !== 'mas') {
      // switchMap acaba de cancelar un "Cargar más" que siguiera en vuelo: nadie más va a limpiar su marca
      this.cargandoMas.set(false);
    }
    const clubId = this.clubId();
    if (clubId === null) {
      return EMPTY;
    }
    let pagina = 0;
    let limite = POR_PAGINA;
    if (modo === 'inicial') {
      this.cargando.set(true);
      this.error.set(null);
    } else if (modo === 'mas') {
      pagina = Math.floor(this.items().length / POR_PAGINA);
      this.cargandoMas.set(true);
    } else {
      // recarga silenciosa: vuelve a pedir lo que ya se había cargado (hasta el tope del backend)
      const paginasCargadas = Math.max(1, Math.ceil(this.items().length / POR_PAGINA));
      limite = Math.min(LIMITE_MAXIMO, paginasCargadas * POR_PAGINA);
    }
    return this.incidenciaService.listar(clubId, this.filtros(pagina, limite)).pipe(
      map((datos): Pagina => ({ modo, datos, limite })),
      catchError((err: unknown) => {
        if (modo === 'inicial') {
          this.error.set(mensajeDeError(err, 'No se pudieron cargar las incidencias.'));
          this.cargando.set(false);
        } else if (modo === 'mas') {
          this.cargandoMas.set(false);
          this.snack.open(mensajeDeError(err, 'No se pudieron cargar más incidencias.'), 'Cerrar', {
            duration: 6000,
          });
        }
        return EMPTY; // una recarga silenciosa fallida conserva lo que ya se veía
      }),
    );
  }

  private aplicar({ modo, datos, limite }: Pagina): void {
    if (modo === 'mas') {
      this.items.update((actuales) => [...actuales, ...datos]);
      this.cargandoMas.set(false);
    } else {
      this.items.set(datos);
      this.cargando.set(false);
    }
    this.hayMas.set(datos.length === limite);
  }

  private filtros(page: number, limit: number): FiltrosIncidencias {
    const filtros: FiltrosIncidencias = { page, limit };
    const estado = this.estado();
    if (estado !== null) {
      filtros.estado = estado;
    }
    if (this.esAdminDelClub()) {
      const pistaId = this.pistaId();
      if (pistaId !== null) {
        filtros.pistaId = pistaId;
      }
      const asignacion = this.asignacion();
      if (asignacion !== '' && asignacion !== SIN_ASIGNAR) {
        filtros.asignadaA = Number(asignacion);
      }
    }
    return filtros;
  }
}
