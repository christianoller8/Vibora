import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject, of, throwError } from 'rxjs';
import { ClubService } from '../../../core/api/club.service';
import { NotificacionesStore } from '../../../core/notificaciones/notificaciones.store';
import { hoyEnMadrid } from '../../../core/util/fecha';
import { Notificacion, TipoNotificacion } from '../../../shared/models/notificacion.model';
import { Partido } from '../../../shared/models/partido.model';
import { crearNotificacion } from '../../../testing/test-utils';
import { PartidoService } from '../partido.service';
import { PartidosAbiertos } from './partidos-abiertos';

function partido(id: number, cambios: Partial<Partido> = {}): Partido {
  return {
    reservaId: id,
    club: { id: 1, nombre: 'Club Víbora Demo' },
    pistaNombre: `Pista ${id}`,
    inicio: '2026-07-15T16:00:00Z', // 18:00 en Madrid
    fin: '2026-07-15T17:30:00Z',
    cuotaPorJugador: 11.25,
    plazasLibres: 2,
    jugadores: [
      { usuarioId: 1, nombre: 'Ana', inicialApellido: 'G' },
      { usuarioId: 2, nombre: 'Luis', inicialApellido: '' },
    ],
    yaApuntado: false,
    esOrganizador: false,
    ...cambios,
  };
}

const error = (status: number, mensaje: string): HttpErrorResponse =>
  new HttpErrorResponse({ status, error: { error: mensaje } });

describe('PartidosAbiertos', () => {
  let fixture: ComponentFixture<PartidosAbiertos>;
  const partidos = { listar: vi.fn(), apuntarse: vi.fn(), salir: vi.fn() };
  const clubes = { listar: vi.fn() };
  const dialogo = { open: vi.fn() };
  const snack = { open: vi.fn() };
  let recibidas: Subject<Notificacion>;

  async function crear(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [PartidosAbiertos],
      providers: [
        { provide: NotificacionesStore, useValue: { recibidas$: recibidas.asObservable() } },
        { provide: PartidoService, useValue: partidos },
        { provide: ClubService, useValue: clubes },
        { provide: MatDialog, useValue: dialogo },
        { provide: MatSnackBar, useValue: snack },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(PartidosAbiertos);
    await fixture.whenStable();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const tarjetas = (): HTMLElement[] => Array.from(el().querySelectorAll<HTMLElement>('.partido'));
  const accion = (tarjeta: HTMLElement, nombre: string): HTMLButtonElement | null =>
    tarjeta.querySelector(`button[data-accion="${nombre}"]`);

  beforeEach(() => {
    vi.resetAllMocks();
    recibidas = new Subject<Notificacion>();
    partidos.listar.mockReturnValue(of([partido(1), partido(2)]));
    clubes.listar.mockReturnValue(
      of([
        { id: 1, nombre: 'Club Víbora Demo' },
        { id: 2, nombre: 'Otro Club' },
      ]),
    );
    dialogo.open.mockReturnValue({ afterClosed: () => of(true) });
  });

  it('carga los partidos abiertos sin filtros al entrar', async () => {
    await crear();

    expect(partidos.listar).toHaveBeenCalledWith({ clubId: undefined, fecha: undefined });
    expect(tarjetas()).toHaveLength(2);
  });

  it('cada tarjeta muestra club, pista, fecha, hora de Madrid, cuota y plazas libres', async () => {
    await crear();

    const texto = tarjetas()[0].textContent!;
    expect(texto).toContain('Club Víbora Demo');
    expect(texto).toContain('Pista 1');
    expect(texto).toContain('15 de julio de 2026');
    expect(texto).toContain('18:00 – 19:30');
    expect(texto).toMatch(/11,25\s€/);
    expect(texto).toContain('2 plazas libres');
  });

  it('usa el singular con una sola plaza libre y avisa si está completo', async () => {
    partidos.listar.mockReturnValue(
      of([
        partido(1, { plazasLibres: 1 }),
        partido(2, { plazasLibres: 0, yaApuntado: true, esOrganizador: true }),
      ]),
    );
    await crear();

    expect(tarjetas()[0].textContent).toContain('1 plaza libre');
    expect(tarjetas()[1].textContent).toContain('Completo');
  });

  it('muestra los jugadores (nombre e inicial) y plazas vacías hasta completar 4 asientos', async () => {
    await crear();

    const tarjeta = tarjetas()[0];
    const jugadores = Array.from(tarjeta.querySelectorAll('.jugador')).map((j) =>
      j.textContent!.replace(/\s+/g, ' ').trim(),
    );
    expect(jugadores[0]).toContain('Ana G.');
    expect(jugadores[1]).toContain('Luis'); // sin apellido: no se pinta un punto suelto
    expect(jugadores[1]).not.toContain('.');
    expect(tarjeta.querySelectorAll('.asiento-libre')).toHaveLength(2);
    expect(tarjeta.querySelectorAll('.jugador, .asiento-libre')).toHaveLength(4);
  });

  it('muestra las insignias "Organizador" y "Ya apuntado"', async () => {
    partidos.listar.mockReturnValue(
      of([
        partido(1, { esOrganizador: true, yaApuntado: true }),
        partido(2, { yaApuntado: true }),
        partido(3),
      ]),
    );
    await crear();

    expect(tarjetas()[0].textContent).toContain('Organizador');
    expect(tarjetas()[1].textContent).toContain('Ya apuntado');
    expect(tarjetas()[1].textContent).not.toContain('Organizador');
    expect(tarjetas()[2].textContent).not.toContain('Ya apuntado');
  });

  it('ofrece "Apuntarme" solo si no estás apuntado y hay plaza', async () => {
    partidos.listar.mockReturnValue(
      of([
        partido(1),
        partido(2, { yaApuntado: true }),
        partido(3, { plazasLibres: 0 }),
        partido(4, { yaApuntado: true, esOrganizador: true }),
      ]),
    );
    await crear();

    expect(accion(tarjetas()[0], 'apuntarse')).not.toBeNull();
    expect(accion(tarjetas()[1], 'apuntarse')).toBeNull();
    expect(accion(tarjetas()[2], 'apuntarse')).toBeNull();
    expect(accion(tarjetas()[3], 'apuntarse')).toBeNull();
  });

  it('ofrece "Salir" solo a los apuntados que no son organizadores', async () => {
    partidos.listar.mockReturnValue(
      of([
        partido(1),
        partido(2, { yaApuntado: true }),
        partido(3, { yaApuntado: true, esOrganizador: true }),
      ]),
    );
    await crear();

    expect(accion(tarjetas()[0], 'salir')).toBeNull();
    expect(accion(tarjetas()[1], 'salir')).not.toBeNull();
    expect(accion(tarjetas()[2], 'salir')).toBeNull();
  });

  describe('filtros', () => {
    it('lista los clubes con la opción "Todos los clubes"', async () => {
      await crear();

      const opciones = Array.from(el().querySelectorAll('select[data-filtro="club"] option')).map(
        (o) => o.textContent!.trim(),
      );
      expect(opciones).toEqual(['Todos los clubes', 'Club Víbora Demo', 'Otro Club']);
    });

    it('al elegir un club vuelve a pedir los partidos de ese club', async () => {
      await crear();

      fixture.componentInstance.clubCtrl.setValue(2);
      await fixture.whenStable();

      expect(partidos.listar).toHaveBeenLastCalledWith({ clubId: 2, fecha: undefined });
    });

    it('al elegir fecha la envía; al borrarla la omite', async () => {
      await crear();

      fixture.componentInstance.fechaCtrl.setValue('2026-07-20');
      await fixture.whenStable();
      expect(partidos.listar).toHaveBeenLastCalledWith({ clubId: undefined, fecha: '2026-07-20' });

      fixture.componentInstance.fechaCtrl.setValue('');
      await fixture.whenStable();
      expect(partidos.listar).toHaveBeenLastCalledWith({ clubId: undefined, fecha: undefined });
    });

    it('la fecha no puede ser anterior a hoy (Madrid)', async () => {
      await crear();

      const entrada = el().querySelector<HTMLInputElement>('input[data-filtro="fecha"]')!;
      expect(entrada.type).toBe('date');
      expect(entrada.min).toBe(hoyEnMadrid());
    });
  });

  describe('notificaciones en tiempo real', () => {
    const push = (tipo: TipoNotificacion): void =>
      recibidas.next(crearNotificacion(Math.floor(Math.random() * 1e6), { tipo }));

    /** Crea la página con reloj real (whenStable lo necesita) y luego congela el tiempo para el debounce. */
    async function arrancar(): Promise<void> {
      await crear();
      vi.useFakeTimers();
    }

    afterEach(() => {
      vi.useRealTimers();
    });

    it.each<TipoNotificacion>([
      'JUGADOR_SE_APUNTO',
      'JUGADOR_SALIO',
      'PARTIDO_COMPLETO',
      'RESERVA_CANCELADA',
    ])('recarga la lista al llegar %s', async (tipo) => {
      await arrancar();
      partidos.listar.mockClear();

      push(tipo);
      vi.advanceTimersByTime(300);

      expect(partidos.listar).toHaveBeenCalledTimes(1);
    });

    it.each<TipoNotificacion>(['CARGO_NUEVO', 'PAGO_RECIBIDO'])(
      'ignora %s: no afecta a los partidos abiertos',
      async (tipo) => {
        await arrancar();
        partidos.listar.mockClear();

        push(tipo);
        vi.advanceTimersByTime(1000);

        expect(partidos.listar).not.toHaveBeenCalled();
      },
    );

    it('agrupa ráfagas: diez push seguidos hacen una sola petición', async () => {
      await arrancar();
      partidos.listar.mockClear();

      for (let i = 0; i < 10; i++) {
        push('JUGADOR_SE_APUNTO');
        vi.advanceTimersByTime(50);
      }
      expect(partidos.listar).not.toHaveBeenCalled(); // la ventana se renueva con cada push
      vi.advanceTimersByTime(300);

      expect(partidos.listar).toHaveBeenCalledTimes(1);
    });

    it('la recarga es silenciosa: sin spinner, con los datos visibles y los filtros aplicados', async () => {
      await arrancar();
      fixture.componentInstance.clubCtrl.setValue(2);
      fixture.componentInstance.fechaCtrl.setValue('2026-07-20');
      partidos.listar.mockClear();
      const pendiente = new Subject<Partido[]>();
      partidos.listar.mockReturnValue(pendiente);

      push('PARTIDO_COMPLETO');
      vi.advanceTimersByTime(300);
      fixture.detectChanges();

      expect(partidos.listar).toHaveBeenCalledWith({ clubId: 2, fecha: '2026-07-20' });
      expect(el().querySelector('mat-spinner')).toBeNull();
      expect(tarjetas()).toHaveLength(2); // lo anterior sigue a la vista mientras llega lo nuevo

      pendiente.next([partido(1, { plazasLibres: 0 })]);
      pendiente.complete();
      fixture.detectChanges();
      expect(tarjetas()).toHaveLength(1);
      expect(tarjetas()[0].textContent).toContain('Completo');
    });

    it('si la recarga silenciosa falla muestra el error', async () => {
      await arrancar();
      partidos.listar.mockReturnValue(throwError(() => error(500, 'boom')));

      push('JUGADOR_SALIO');
      vi.advanceTimersByTime(300);
      fixture.detectChanges();

      expect(el().querySelector('[role="alert"]')).not.toBeNull();
    });

    it('al destruir la página deja de escuchar: no recarga aunque llegue un push', async () => {
      await arrancar();
      partidos.listar.mockClear();

      push('JUGADOR_SE_APUNTO'); // pendiente de debounce
      fixture.destroy();
      vi.advanceTimersByTime(300);
      push('JUGADOR_SALIO');
      vi.advanceTimersByTime(300);

      expect(partidos.listar).not.toHaveBeenCalled();
      expect(recibidas.observed).toBe(false);
    });
  });

  describe('estados', () => {
    it('muestra el estado vacío', async () => {
      partidos.listar.mockReturnValue(of([]));
      await crear();

      expect(el().textContent).toContain('No hay partidos abiertos');
    });

    it('muestra el error si falla la carga', async () => {
      partidos.listar.mockReturnValue(throwError(() => error(500, 'boom')));
      await crear();

      expect(el().querySelector('[role="alert"]')).not.toBeNull();
      expect(tarjetas()).toHaveLength(0);
    });

    it('muestra el spinner mientras carga', async () => {
      partidos.listar.mockReturnValue(new Subject<Partido[]>());
      await crear();

      expect(el().querySelector('mat-spinner')).not.toBeNull();
    });
  });

  describe('apuntarse', () => {
    it('pide confirmación con el detalle y la parte que pagarás, y avisa de que el cargo irá a Deudas', async () => {
      dialogo.open.mockReturnValue({ afterClosed: () => of(false) });
      await crear();

      accion(tarjetas()[0], 'apuntarse')!.click();
      await fixture.whenStable();

      const datos = dialogo.open.mock.calls[0][1].data;
      expect(datos.lineas).toContainEqual({ etiqueta: 'Club', valor: 'Club Víbora Demo' });
      expect(datos.lineas).toContainEqual({ etiqueta: 'Pista', valor: 'Pista 1' });
      expect(datos.lineas).toContainEqual({ etiqueta: 'Hora', valor: '18:00 – 19:30' });
      const parte = datos.lineas.find((l: { etiqueta: string }) => l.etiqueta === 'Tu parte');
      expect(parte.valor).toMatch(/11,25\s€/);
      expect(datos.mensaje).toContain('Deudas');
      expect(partidos.apuntarse).not.toHaveBeenCalled();
    });

    it('tras confirmar se apunta, avisa y recarga la lista', async () => {
      partidos.apuntarse.mockReturnValue(of(partido(1, { yaApuntado: true })));
      await crear();
      partidos.listar.mockClear();

      accion(tarjetas()[0], 'apuntarse')!.click();
      await fixture.whenStable();

      expect(partidos.apuntarse).toHaveBeenCalledWith(1);
      expect(snack.open.mock.calls[0][0]).toContain('Te has apuntado');
      expect(partidos.listar).toHaveBeenCalledTimes(1);
    });

    it('si se descarta el diálogo no hace nada', async () => {
      dialogo.open.mockReturnValue({ afterClosed: () => of(false) });
      await crear();

      accion(tarjetas()[0], 'apuntarse')!.click();
      await fixture.whenStable();

      expect(partidos.apuntarse).not.toHaveBeenCalled();
    });

    it('un 409 (completo) muestra el mensaje del backend y refresca la lista', async () => {
      partidos.apuntarse.mockReturnValue(
        throwError(() => error(409, 'El partido ya está completo')),
      );
      await crear();
      partidos.listar.mockClear();

      accion(tarjetas()[0], 'apuntarse')!.click();
      await fixture.whenStable();

      expect(snack.open.mock.calls[0][0]).toBe('El partido ya está completo');
      expect(partidos.listar).toHaveBeenCalledTimes(1);
    });

    it('otros errores muestran el mensaje sin recargar', async () => {
      partidos.apuntarse.mockReturnValue(throwError(() => error(500, 'Fallo interno')));
      await crear();
      partidos.listar.mockClear();

      accion(tarjetas()[0], 'apuntarse')!.click();
      await fixture.whenStable();

      expect(snack.open.mock.calls[0][0]).toBe('Fallo interno');
      expect(partidos.listar).not.toHaveBeenCalled();
    });
  });

  describe('salir', () => {
    beforeEach(() => {
      partidos.listar.mockReturnValue(of([partido(1, { yaApuntado: true })]));
    });

    it('pide confirmación antes de salir', async () => {
      dialogo.open.mockReturnValue({ afterClosed: () => of(false) });
      await crear();

      accion(tarjetas()[0], 'salir')!.click();
      await fixture.whenStable();

      expect(dialogo.open).toHaveBeenCalledTimes(1);
      expect(partidos.salir).not.toHaveBeenCalled();
    });

    it('tras confirmar sale, avisa y recarga', async () => {
      partidos.salir.mockReturnValue(of(partido(1)));
      await crear();
      partidos.listar.mockClear();

      accion(tarjetas()[0], 'salir')!.click();
      await fixture.whenStable();

      expect(partidos.salir).toHaveBeenCalledWith(1);
      expect(snack.open.mock.calls[0][0]).toContain('Has salido');
      expect(partidos.listar).toHaveBeenCalledTimes(1);
    });

    it('si el backend responde 409 (p. ej. ya pagado) muestra su mensaje', async () => {
      partidos.salir.mockReturnValue(
        throwError(() => error(409, 'No puedes salir: ya has pagado tu parte')),
      );
      await crear();

      accion(tarjetas()[0], 'salir')!.click();
      await fixture.whenStable();

      expect(snack.open.mock.calls[0][0]).toBe('No puedes salir: ya has pagado tu parte');
    });
  });
});
