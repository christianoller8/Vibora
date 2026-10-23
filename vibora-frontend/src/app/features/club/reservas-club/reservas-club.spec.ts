import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject, of, throwError } from 'rxjs';
import { NotificacionesStore } from '../../../core/notificaciones/notificaciones.store';
import { hoyEnMadrid } from '../../../core/util/fecha';
import { Notificacion, TipoNotificacion } from '../../../shared/models/notificacion.model';
import { EstadoReserva, ReservaClub } from '../../../shared/models/reserva.model';
import { crearNotificacion, escribir } from '../../../testing/test-utils';
import { ReservaService } from '../../reservas/reserva.service';
import { ReservasClub } from './reservas-club';

const HORA = 3_600_000;

function reserva(
  id: number,
  estado: EstadoReserva,
  inicioEnMs: number,
  cambios: Partial<ReservaClub> = {},
): ReservaClub {
  const inicio = new Date(Date.now() + inicioEnMs);
  return {
    id,
    pistaId: 10 + id,
    pistaNombre: `Pista ${id}`,
    inicio: inicio.toISOString(),
    fin: new Date(inicio.getTime() + 1.5 * HORA).toISOString(),
    estado,
    precioTotal: 18,
    usuario: {
      id: 40 + id,
      nombre: 'Luis',
      apellidos: `Pérez ${id}`,
      email: `luis${id}@vibora.test`,
    },
    ...cambios,
  };
}

const error = (status: number, mensaje: string): HttpErrorResponse =>
  new HttpErrorResponse({ status, error: { error: mensaje } });

describe('ReservasClub', () => {
  let fixture: ComponentFixture<ReservasClub>;
  const reservas = { delClub: vi.fn(), cancelar: vi.fn() };
  const dialogo = { open: vi.fn() };
  const snack = { open: vi.fn() };
  let recibidas: Subject<Notificacion>;

  const FUTURA = reserva(1, 'CONFIRMADA', 48 * HORA);
  const PASADA = reserva(2, 'CONFIRMADA', -48 * HORA);
  const CANCELADA = reserva(3, 'CANCELADA', 72 * HORA);

  async function crear(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [ReservasClub],
      providers: [
        { provide: NotificacionesStore, useValue: { recibidas$: recibidas.asObservable() } },
        { provide: ReservaService, useValue: reservas },
        { provide: MatDialog, useValue: dialogo },
        { provide: MatSnackBar, useValue: snack },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ReservasClub);
    fixture.componentRef.setInput('clubId', 5);
    await fixture.whenStable();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const filas = (): HTMLElement[] => Array.from(el().querySelectorAll<HTMLElement>('tr.reserva'));
  const fecha = (): HTMLInputElement =>
    el().querySelector<HTMLInputElement>('input[name="fecha"]')!;
  const botonCancelar = (fila: HTMLElement): HTMLButtonElement | null =>
    fila.querySelector('button[data-accion="cancelar"]');

  beforeEach(() => {
    vi.resetAllMocks();
    recibidas = new Subject<Notificacion>();
    reservas.delClub.mockReturnValue(of([FUTURA, PASADA, CANCELADA]));
    dialogo.open.mockReturnValue({ afterClosed: () => of(true) });
  });

  it('por defecto muestra hoy (Madrid) y pide las reservas de ese día', async () => {
    await crear();

    expect(fecha().type).toBe('date');
    expect(fecha().value).toBe(hoyEnMadrid());
    expect(reservas.delClub).toHaveBeenCalledWith(5, hoyEnMadrid());
  });

  it('al cambiar la fecha vuelve a pedir las reservas de ese día', async () => {
    await crear();

    escribir(fecha(), '2026-07-20');
    await fixture.whenStable();

    expect(reservas.delClub).toHaveBeenLastCalledWith(5, '2026-07-20');
  });

  it('si se borra la fecha no pide nada', async () => {
    await crear();
    reservas.delClub.mockClear();

    escribir(fecha(), '');
    await fixture.whenStable();

    expect(reservas.delClub).not.toHaveBeenCalled();
  });

  it('cada fila muestra horario de Madrid, pista, jugador con email, estado y precio', async () => {
    await crear();

    expect(filas()).toHaveLength(3);
    const texto = filas()[1].textContent!.replace(/\s+/g, ' '); // la futura (ordenadas por hora)
    expect(texto).toMatch(/\d{2}:\d{2} – \d{2}:\d{2}/);
    expect(texto).toContain('Pista 1');
    expect(texto).toContain('Luis Pérez 1');
    expect(texto).toContain('luis1@vibora.test');
    expect(texto).toContain('Confirmada');
    expect(texto).toMatch(/18,00\s€/);
    expect(filas()[2].textContent).toContain('Cancelada');
  });

  it('muestra el jugador sin nombre usando solo los apellidos', async () => {
    reservas.delClub.mockReturnValue(
      of([
        reserva(4, 'CONFIRMADA', HORA, {
          usuario: { id: 1, nombre: null, apellidos: 'García', email: 'g@v.es' },
        }),
      ]),
    );
    await crear();

    expect(filas()[0].textContent).toContain('García');
    expect(filas()[0].textContent).not.toContain('null');
  });

  it('ordena por hora de inicio', async () => {
    await crear();

    // PASADA (-48 h), FUTURA (+48 h), CANCELADA (+72 h)
    expect(filas()[0].textContent).toContain('Pista 2');
    expect(filas()[1].textContent).toContain('Pista 1');
    expect(filas()[2].textContent).toContain('Pista 3');
  });

  it('solo ofrece "Cancelar" en las CONFIRMADAS que aún no han empezado', async () => {
    await crear();

    expect(botonCancelar(filas()[0])).toBeNull(); // pasada
    expect(botonCancelar(filas()[1])).not.toBeNull(); // futura confirmada
    expect(botonCancelar(filas()[2])).toBeNull(); // ya cancelada
  });

  it('el botón de cancelar es accesible: nombra pista y jugador', async () => {
    await crear();

    expect(botonCancelar(filas()[1])!.getAttribute('aria-label')).toContain('Pista 1');
    expect(botonCancelar(filas()[1])!.getAttribute('aria-label')).toContain('Luis Pérez 1');
  });

  it('muestra un estado vacío si ese día no hay reservas', async () => {
    reservas.delClub.mockReturnValue(of([]));
    await crear();

    expect(el().textContent).toContain('No hay reservas');
  });

  it('muestra el error si falla la carga', async () => {
    reservas.delClub.mockReturnValue(throwError(() => error(500, 'boom')));
    await crear();

    expect(el().querySelector('[role="alert"]')).not.toBeNull();
  });

  it('muestra el spinner mientras carga', async () => {
    reservas.delClub.mockReturnValue(new Subject<ReservaClub[]>());
    await crear();

    expect(el().querySelector('mat-spinner')).not.toBeNull();
  });

  describe('cancelar', () => {
    it('pide confirmación avisando de que se notificará a los jugadores', async () => {
      dialogo.open.mockReturnValue({ afterClosed: () => of(false) });
      await crear();

      botonCancelar(filas()[1])!.click();
      await fixture.whenStable();

      const datos = dialogo.open.mock.calls[0][1].data;
      expect(datos.mensaje).toContain('Se avisará a los jugadores');
      expect(datos.lineas).toContainEqual({ etiqueta: 'Pista', valor: 'Pista 1' });
      expect(datos.lineas).toContainEqual({ etiqueta: 'Jugador', valor: 'Luis Pérez 1' });
      expect(reservas.cancelar).not.toHaveBeenCalled();
    });

    it('tras confirmar hace POST de cancelar, avisa y recarga', async () => {
      reservas.cancelar.mockReturnValue(of({ id: 1, estado: 'CANCELADA' }));
      await crear();
      reservas.delClub.mockClear();

      botonCancelar(filas()[1])!.click();
      await fixture.whenStable();

      expect(reservas.cancelar).toHaveBeenCalledWith(1);
      expect(snack.open.mock.calls[0][0]).toContain('Reserva cancelada');
      expect(reservas.delClub).toHaveBeenCalledTimes(1);
    });

    it('si se descarta el diálogo no cancela', async () => {
      dialogo.open.mockReturnValue({ afterClosed: () => of(false) });
      await crear();

      botonCancelar(filas()[1])!.click();
      await fixture.whenStable();

      expect(reservas.cancelar).not.toHaveBeenCalled();
    });

    it('un 409 muestra el mensaje del backend y refresca para mostrar el estado real', async () => {
      reservas.cancelar.mockReturnValue(throwError(() => error(409, 'La reserva ya ha comenzado')));
      await crear();
      reservas.delClub.mockClear();

      botonCancelar(filas()[1])!.click();
      await fixture.whenStable();

      expect(snack.open.mock.calls[0][0]).toBe('La reserva ya ha comenzado');
      expect(reservas.delClub).toHaveBeenCalledTimes(1);
    });

    it('otros errores muestran el mensaje sin recargar', async () => {
      reservas.cancelar.mockReturnValue(throwError(() => error(500, 'Fallo interno')));
      await crear();
      reservas.delClub.mockClear();

      botonCancelar(filas()[1])!.click();
      await fixture.whenStable();

      expect(snack.open.mock.calls[0][0]).toBe('Fallo interno');
      expect(reservas.delClub).not.toHaveBeenCalled();
    });
  });

  describe('notificaciones en tiempo real', () => {
    const push = (tipo: TipoNotificacion): void =>
      recibidas.next(crearNotificacion(Math.floor(Math.random() * 1e6), { tipo }));

    /** Crea la página con reloj real (whenStable lo necesita) y luego congela el tiempo para el debounce. */
    async function arrancar(): Promise<void> {
      await crear();
      vi.useFakeTimers();
      reservas.delClub.mockClear();
    }

    afterEach(() => {
      vi.useRealTimers();
    });

    it.each<TipoNotificacion>(['JUGADOR_SE_APUNTO', 'JUGADOR_SALIO', 'RESERVA_CANCELADA'])(
      'recarga la fecha mostrada al llegar %s',
      async (tipo) => {
        await arrancar();

        push(tipo);
        vi.advanceTimersByTime(300);

        expect(reservas.delClub).toHaveBeenCalledTimes(1);
        expect(reservas.delClub).toHaveBeenCalledWith(5, hoyEnMadrid());
      },
    );

    it.each<TipoNotificacion>(['CARGO_NUEVO', 'PAGO_RECIBIDO', 'PARTIDO_COMPLETO'])(
      'ignora %s: no cambia las reservas del club',
      async (tipo) => {
        await arrancar();

        push(tipo);
        vi.advanceTimersByTime(1000);

        expect(reservas.delClub).not.toHaveBeenCalled();
      },
    );

    it('agrupa ráfagas: diez push seguidos hacen una sola petición', async () => {
      await arrancar();

      for (let i = 0; i < 10; i++) {
        push('JUGADOR_SE_APUNTO');
        vi.advanceTimersByTime(50);
      }
      expect(reservas.delClub).not.toHaveBeenCalled();
      vi.advanceTimersByTime(300);

      expect(reservas.delClub).toHaveBeenCalledTimes(1);
    });

    it('la recarga es silenciosa: sin spinner y con la lista anterior a la vista', async () => {
      await arrancar();
      const pendiente = new Subject<ReservaClub[]>();
      reservas.delClub.mockReturnValue(pendiente);

      push('RESERVA_CANCELADA');
      vi.advanceTimersByTime(300);
      fixture.detectChanges();

      expect(el().querySelector('mat-spinner')).toBeNull();
      expect(filas()).toHaveLength(3);

      pendiente.next([{ ...FUTURA, estado: 'CANCELADA' }]);
      pendiente.complete();
      fixture.detectChanges();
      expect(filas()).toHaveLength(1);
      expect(filas()[0].textContent).toContain('Cancelada');
    });

    it('al destruir la página deja de escuchar: no recarga aunque llegue un push', async () => {
      await arrancar();

      push('JUGADOR_SE_APUNTO'); // pendiente de debounce
      fixture.destroy();
      vi.advanceTimersByTime(300);
      push('JUGADOR_SALIO');
      vi.advanceTimersByTime(300);

      expect(reservas.delClub).not.toHaveBeenCalled();
      expect(recibidas.observed).toBe(false);
    });
  });
});
