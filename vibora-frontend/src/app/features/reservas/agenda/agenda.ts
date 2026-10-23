import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { EMPTY, Subject, catchError, map, of, switchMap, tap } from 'rxjs';
import { ClubService } from '../../../core/api/club.service';
import { mensajeDeError } from '../../../core/http/error.util';
import {
  formatearFechaLarga,
  formatearPrecio,
  formatearRangoHoras,
  hoyEnMadrid,
} from '../../../core/util/fecha';
import {
  ConfirmDialog,
  ConfirmDialogData,
} from '../../../shared/components/confirm-dialog/confirm-dialog';
import { Club } from '../../../shared/models/club.model';
import { Pista } from '../../../shared/models/pista.model';
import { CasillaAgenda } from '../../../shared/models/reserva.model';
import { ReservaService } from '../reserva.service';

@Component({
  selector: 'app-agenda',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './agenda.html',
  styleUrl: './agenda.scss',
})
export class Agenda {
  private readonly clubService = inject(ClubService);
  private readonly reservaService = inject(ReservaService);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);

  /** Un día anterior a hoy (en Madrid) no tiene casillas reservables. */
  protected readonly minFecha = hoyEnMadrid();

  // Selección actual (se conserva mientras el componente vive)
  readonly clubCtrl = new FormControl<number | null>(null);
  readonly pistaCtrl = new FormControl<number | null>(null);
  readonly fechaCtrl = new FormControl(hoyEnMadrid(), {
    nonNullable: true,
    validators: [Validators.required],
  });
  protected readonly clubId = toSignal(this.clubCtrl.valueChanges, { initialValue: null });
  protected readonly pistaId = toSignal(this.pistaCtrl.valueChanges, { initialValue: null });

  protected readonly clubes = signal<Club[]>([]);
  protected readonly pistas = signal<Pista[]>([]);
  protected readonly casillas = signal<CasillaAgenda[]>([]);
  protected readonly cargandoClubes = signal(true);
  protected readonly cargandoPistas = signal(false);
  protected readonly cargandoAgenda = signal(false);
  protected readonly reservando = signal(false);
  protected readonly errorCarga = signal<string | null>(null);

  protected readonly formatearRango = formatearRangoHoras;
  protected readonly formatearPrecio = formatearPrecio;

  private readonly recargar$ = new Subject<void>();

  constructor() {
    this.clubCtrl.valueChanges
      .pipe(
        tap(() => {
          this.pistas.set([]);
          this.casillas.set([]);
          this.errorCarga.set(null);
          this.pistaCtrl.setValue(null, { emitEvent: false });
        }),
        switchMap((clubId) => this.pistasDe(clubId)),
        takeUntilDestroyed(),
      )
      .subscribe((pistas) => {
        this.pistas.set(pistas);
        this.cargandoPistas.set(false);
        this.pistaCtrl.setValue(pistas[0]?.id ?? null); // dispara la carga de la agenda
      });

    this.pistaCtrl.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.recargar$.next());
    this.fechaCtrl.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.recargar$.next());

    this.recargar$
      .pipe(
        switchMap(() => this.agendaActual()),
        takeUntilDestroyed(),
      )
      .subscribe((casillas) => {
        this.casillas.set(casillas);
        this.cargandoAgenda.set(false);
      });

    this.cargarClubes();
  }

  protected estado(casilla: CasillaAgenda): string {
    if (!casilla.disponible) {
      return 'No disponible';
    }
    return casilla.precio === null ? 'Sin tarifa' : 'Disponible';
  }

  protected esReservable(casilla: CasillaAgenda): boolean {
    return casilla.disponible && casilla.precio !== null;
  }

  protected reservar(casilla: CasillaAgenda): void {
    const club = this.clubes().find((c) => c.id === this.clubId());
    const pista = this.pistas().find((p) => p.id === this.pistaId());
    if (!club || !pista || !this.esReservable(casilla) || this.reservando()) {
      return;
    }

    const datos: ConfirmDialogData = {
      titulo: 'Confirmar reserva',
      lineas: [
        { etiqueta: 'Club', valor: club.nombre },
        { etiqueta: 'Pista', valor: pista.nombre },
        { etiqueta: 'Fecha', valor: formatearFechaLarga(casilla.inicio) },
        { etiqueta: 'Hora', valor: formatearRangoHoras(casilla.inicio, casilla.fin) },
        { etiqueta: 'Precio', valor: formatearPrecio(casilla.precio) },
      ],
      textoConfirmar: 'Reservar',
    };

    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, { data: datos })
      .afterClosed()
      .subscribe((confirmado) => {
        if (confirmado) {
          this.crearReserva(club.id, pista.id, casilla);
        }
      });
  }

  private crearReserva(clubId: number, pistaId: number, casilla: CasillaAgenda): void {
    this.reservando.set(true);
    this.reservaService.crear(clubId, pistaId, casilla.inicio).subscribe({
      next: () => {
        this.reservando.set(false);
        this.aviso(
          `Reserva confirmada para las ${formatearRangoHoras(casilla.inicio, casilla.fin)}.`,
        );
        this.recargar$.next();
      },
      error: (err: unknown) => {
        this.reservando.set(false);
        this.aviso(mensajeDeError(err, 'No se pudo completar la reserva. Inténtalo de nuevo.'));
        // Otra persona pudo quedarse la casilla: mostramos la agenda al día
        this.recargar$.next();
      },
    });
  }

  private cargarClubes(): void {
    this.clubService.listar().subscribe({
      next: (clubes) => {
        this.clubes.set(clubes);
        this.cargandoClubes.set(false);
        if (clubes.length > 0) {
          this.clubCtrl.setValue(clubes[0].id);
        }
      },
      error: (err: unknown) => {
        this.cargandoClubes.set(false);
        this.errorCarga.set(mensajeDeError(err, 'No se pudieron cargar los clubes.'));
      },
    });
  }

  private pistasDe(clubId: number | null) {
    if (clubId === null) {
      return of<Pista[]>([]);
    }
    this.cargandoPistas.set(true);
    return this.clubService.pistas(clubId).pipe(
      catchError((err: unknown) => {
        this.errorCarga.set(mensajeDeError(err, 'No se pudieron cargar las pistas.'));
        return of<Pista[]>([]);
      }),
      map((pistas) => pistas.filter((p) => p.activa)),
    );
  }

  private agendaActual() {
    const clubId = this.clubCtrl.value;
    const pistaId = this.pistaCtrl.value;
    const fecha = this.fechaCtrl.value;
    if (clubId === null || pistaId === null || !fecha) {
      this.casillas.set([]);
      this.cargandoAgenda.set(false);
      return EMPTY;
    }
    this.cargandoAgenda.set(true);
    this.errorCarga.set(null);
    return this.reservaService.agenda(clubId, pistaId, fecha).pipe(
      catchError((err: unknown) => {
        this.errorCarga.set(mensajeDeError(err, 'No se pudo cargar la agenda.'));
        this.cargandoAgenda.set(false);
        this.casillas.set([]);
        return EMPTY;
      }),
    );
  }

  private aviso(mensaje: string): void {
    this.snack.open(mensaje, 'Cerrar', { duration: 6000 });
  }
}
