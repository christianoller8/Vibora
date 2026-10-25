import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable, catchError, concatMap, from, map, of, switchMap, tap } from 'rxjs';
import { ClubService } from '../../../core/api/club.service';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/http/error.util';
import { Club } from '../../../shared/models/club.model';
import {
  CATEGORIAS_INCIDENCIA,
  CategoriaIncidencia,
  ETIQUETA_CATEGORIA,
  IncidenciaRequest,
} from '../../../shared/models/incidencia.model';
import { Pista } from '../../../shared/models/pista.model';
import { elegirClubInicial, guardarClubPreferido, leerClubPreferido } from '../club-preferido';
import { FotoIncidenciaService } from '../foto-incidencia.service';
import { IncidenciaService } from '../incidencia.service';
import { SelectorFotos } from '../selector-fotos/selector-fotos';

const MAX_TITULO = 120;
const MAX_DESCRIPCION = 2000;

const MENSAJE_SIN_VINCULO =
  'Solo puedes reportar incidencias en clubes donde has reservado o jugado.';

interface Subida {
  nombre: string;
  estado: 'pendiente' | 'subiendo' | 'subida' | 'error';
  error: string | null;
}

/**
 * Reportar una incidencia: primero se crea y después se suben las fotos, una a una. Si alguna foto falla la
 * incidencia se conserva (ya existe) y se lleva a su detalle, donde se puede reintentar la subida.
 */
@Component({
  selector: 'app-incidencia-nueva',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    SelectorFotos,
  ],
  templateUrl: './incidencia-nueva.html',
  styleUrl: './incidencia-nueva.scss',
})
export class IncidenciaNueva {
  private readonly clubService = inject(ClubService);
  private readonly incidenciaService = inject(IncidenciaService);
  private readonly fotoService = inject(FotoIncidenciaService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly snack = inject(MatSnackBar);

  protected readonly categorias = CATEGORIAS_INCIDENCIA;
  protected readonly etiquetaCategoria = ETIQUETA_CATEGORIA;
  protected readonly maxTitulo = MAX_TITULO;
  protected readonly maxDescripcion = MAX_DESCRIPCION;

  protected readonly form = inject(FormBuilder).nonNullable.group({
    club: ['', [Validators.required]],
    pistaId: [''],
    titulo: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(MAX_TITULO)]],
    descripcion: [
      '',
      [Validators.required, Validators.pattern(/\S/), Validators.maxLength(MAX_DESCRIPCION)],
    ],
    categoria: ['', [Validators.required]],
  });
  protected readonly fotos = signal<File[]>([]);

  protected readonly clubes = signal<Club[]>([]);
  protected readonly pistas = signal<Pista[]>([]);
  protected readonly cargandoClubes = signal(true);
  protected readonly errorClubes = signal<string | null>(null);
  protected readonly errorPistas = signal<string | null>(null);

  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Id de la incidencia una vez creada: a partir de ahí el formulario ya no se edita ni se vuelve a enviar. */
  protected readonly creadaId = signal<number | null>(null);
  protected readonly subidas = signal<Subida[]>([]);
  protected readonly hayFallos = signal(false);

  constructor() {
    this.form.controls.club.valueChanges
      .pipe(
        tap(() => {
          this.pistas.set([]);
          this.errorPistas.set(null);
          this.form.controls.pistaId.setValue('');
        }),
        switchMap((club) => this.pistasDe(club)),
        takeUntilDestroyed(),
      )
      .subscribe((pistas) => this.pistas.set(pistas));

    this.clubService.listar().subscribe({
      next: (clubes) => {
        this.clubes.set(clubes);
        this.cargandoClubes.set(false);
        const inicial = this.clubInicial(clubes);
        if (inicial !== null) {
          this.form.controls.club.setValue(String(inicial));
        }
      },
      error: (err: unknown) => {
        this.cargandoClubes.set(false);
        this.errorClubes.set(mensajeDeError(err, 'No se pudieron cargar los clubes.'));
      },
    });
  }

  protected enviar(): void {
    if (this.form.invalid || this.enviando()) {
      this.form.markAllAsTouched();
      return;
    }
    const valores = this.form.getRawValue();
    const clubId = Number(valores.club);
    const peticion: IncidenciaRequest = {
      titulo: valores.titulo.trim(),
      descripcion: valores.descripcion.trim(),
      categoria: valores.categoria as CategoriaIncidencia,
      pistaId: valores.pistaId === '' ? null : Number(valores.pistaId),
    };
    this.enviando.set(true);
    this.error.set(null);
    this.incidenciaService.crear(clubId, peticion).subscribe({
      next: (creada) => {
        guardarClubPreferido(clubId);
        this.creadaId.set(creada.id);
        this.subirFotos(creada.id, this.fotos());
      },
      error: (err: unknown) => {
        this.enviando.set(false);
        this.error.set(
          err instanceof HttpErrorResponse && err.status === 403
            ? MENSAJE_SIN_VINCULO
            : mensajeDeError(err, 'No se pudo crear la incidencia. Inténtalo de nuevo.'),
        );
      },
    });
  }

  /** Sube las fotos una detrás de otra; las que fallen quedan marcadas y no impiden subir las siguientes. */
  private subirFotos(incidenciaId: number, archivos: File[]): void {
    if (archivos.length === 0) {
      this.terminar(incidenciaId);
      return;
    }
    this.subidas.set(archivos.map((a) => ({ nombre: a.name, estado: 'pendiente', error: null })));
    from(archivos.map((archivo, indice) => ({ archivo, indice })))
      .pipe(
        concatMap(({ archivo, indice }) => {
          this.marcar(indice, { estado: 'subiendo' });
          return this.fotoService.subir(incidenciaId, archivo).pipe(
            map(() => ({ indice, error: null as string | null })),
            catchError((err: unknown) =>
              of({ indice, error: mensajeDeError(err, 'No se pudo subir la foto.') }),
            ),
          );
        }),
      )
      .subscribe({
        next: ({ indice, error }) =>
          this.marcar(indice, error === null ? { estado: 'subida' } : { estado: 'error', error }),
        complete: () => this.terminar(incidenciaId),
      });
  }

  private terminar(incidenciaId: number): void {
    if (this.subidas().some((s) => s.estado === 'error')) {
      this.hayFallos.set(true); // se queda aquí: dice cuáles fallaron y ofrece ir al detalle
      return;
    }
    this.snack.open('Incidencia reportada.', 'Cerrar', { duration: 6000 });
    void this.router.navigate(['/incidencias', incidenciaId]);
  }

  private marcar(indice: number, cambios: Partial<Subida>): void {
    this.subidas.update((lista) => lista.map((s, i) => (i === indice ? { ...s, ...cambios } : s)));
  }

  private clubInicial(clubes: Club[]): number | null {
    const pedido = Number(this.route.snapshot.queryParamMap.get('club'));
    if (Number.isInteger(pedido) && clubes.some((c) => c.id === pedido)) {
      return pedido;
    }
    return elegirClubInicial(clubes, this.auth.clubId(), leerClubPreferido());
  }

  private pistasDe(club: string): Observable<Pista[]> {
    const clubId = Number(club);
    if (club === '' || !Number.isInteger(clubId)) {
      return of([]);
    }
    return this.clubService.pistas(clubId).pipe(
      map((pistas) => pistas.filter((p) => p.activa)),
      catchError((err: unknown) => {
        this.errorPistas.set(mensajeDeError(err, 'No se pudieron cargar las pistas.'));
        return of<Pista[]>([]);
      }),
    );
  }
}
