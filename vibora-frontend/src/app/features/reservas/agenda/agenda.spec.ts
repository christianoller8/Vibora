import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, throwError } from 'rxjs';
import { ClubService } from '../../../core/api/club.service';
import { hoyEnMadrid } from '../../../core/util/fecha';
import { CasillaAgenda } from '../../../shared/models/reserva.model';
import { Club } from '../../../shared/models/club.model';
import { Pista } from '../../../shared/models/pista.model';
import { ReservaService } from '../reserva.service';
import { Agenda } from './agenda';

const CLUBES: Club[] = [
  {
    id: 1,
    nombre: 'Club Víbora',
    direccion: null,
    ciudad: 'Madrid',
    horaApertura: '09:00:00',
    horaCierre: '22:00:00',
  },
  {
    id: 2,
    nombre: 'Club Norte',
    direccion: null,
    ciudad: 'Bilbao',
    horaApertura: '09:00:00',
    horaCierre: '22:00:00',
  },
];

const pista = (id: number, nombre: string, clubId: number): Pista => ({
  id,
  nombre,
  tipo: 'INTERIOR',
  activa: true,
  clubId,
});

// 16:00Z = 18:00 en Madrid (verano)
const LIBRE: CasillaAgenda = {
  inicio: '2026-07-15T16:00:00Z',
  fin: '2026-07-15T17:30:00Z',
  disponible: true,
  precio: 12,
};
const OCUPADA: CasillaAgenda = {
  inicio: '2026-07-15T17:30:00Z',
  fin: '2026-07-15T19:00:00Z',
  disponible: false,
  precio: 12,
};
const SIN_TARIFA: CasillaAgenda = {
  inicio: '2026-07-15T19:00:00Z',
  fin: '2026-07-15T20:30:00Z',
  disponible: true,
  precio: null,
};

describe('Agenda', () => {
  let fixture: ComponentFixture<Agenda>;
  let componente: Agenda;
  const clubes = { listar: vi.fn(), pistas: vi.fn() };
  const reservas = { agenda: vi.fn(), crear: vi.fn() };
  const dialogo = { open: vi.fn() };
  const snack = { open: vi.fn() };

  function respuestaDialogo(confirmado: boolean): void {
    dialogo.open.mockReturnValue({ afterClosed: () => of(confirmado) });
  }

  async function crear(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [Agenda],
      providers: [
        { provide: ClubService, useValue: clubes },
        { provide: ReservaService, useValue: reservas },
        { provide: MatDialog, useValue: dialogo },
        { provide: MatSnackBar, useValue: snack },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(Agenda);
    componente = fixture.componentInstance;
    await fixture.whenStable();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const casillas = (): HTMLButtonElement[] =>
    Array.from(el().querySelectorAll<HTMLButtonElement>('button.casilla'));

  beforeEach(() => {
    vi.resetAllMocks();
    clubes.listar.mockReturnValue(of(CLUBES));
    clubes.pistas.mockImplementation((clubId: number) =>
      of(
        clubId === 1
          ? [pista(10, 'Pista 1', 1), pista(11, 'Pista 2', 1)]
          : [pista(20, 'Central', 2)],
      ),
    );
    reservas.agenda.mockReturnValue(of([LIBRE, OCUPADA, SIN_TARIFA]));
    reservas.crear.mockReturnValue(of({ id: 99 }));
    respuestaDialogo(true);
  });

  it('carga clubes, preselecciona el primero, carga sus pistas y la agenda de hoy', async () => {
    await crear();

    expect(clubes.listar).toHaveBeenCalledTimes(1);
    expect(clubes.pistas).toHaveBeenCalledWith(1);
    expect(reservas.agenda).toHaveBeenCalledWith(1, 10, hoyEnMadrid());
    expect(componente.clubCtrl.value).toBe(1);
    expect(componente.pistaCtrl.value).toBe(10);
    expect(componente.fechaCtrl.value).toBe(hoyEnMadrid());
  });

  it('pinta las casillas con hora de Madrid, precio en euros y su estado', async () => {
    await crear();

    const [libre, ocupada, sinTarifa] = casillas();
    expect(casillas()).toHaveLength(3);

    expect(libre.textContent).toContain('18:00 – 19:30');
    expect(libre.textContent).toMatch(/12,00\s€/);
    expect(libre.textContent).toContain('Disponible');
    expect(libre.disabled).toBe(false);

    expect(ocupada.textContent).toContain('19:30 – 21:00');
    expect(ocupada.textContent).toContain('No disponible');
    expect(ocupada.disabled).toBe(true);

    expect(sinTarifa.textContent).toContain('Sin tarifa');
    expect(sinTarifa.disabled).toBe(true);
  });

  it('recarga pistas y agenda al cambiar de club', async () => {
    await crear();

    componente.clubCtrl.setValue(2);
    await fixture.whenStable();

    expect(clubes.pistas).toHaveBeenCalledWith(2);
    expect(componente.pistaCtrl.value).toBe(20);
    expect(reservas.agenda).toHaveBeenLastCalledWith(2, 20, hoyEnMadrid());
  });

  it('recarga la agenda al cambiar de pista y de fecha', async () => {
    await crear();

    componente.pistaCtrl.setValue(11);
    await fixture.whenStable();
    expect(reservas.agenda).toHaveBeenLastCalledWith(1, 11, hoyEnMadrid());

    componente.fechaCtrl.setValue('2026-08-01');
    await fixture.whenStable();
    expect(reservas.agenda).toHaveBeenLastCalledWith(1, 11, '2026-08-01');
  });

  it('muestra un mensaje amable si todavía no hay clubes', async () => {
    clubes.listar.mockReturnValue(of([]));
    await crear();

    expect(el().textContent).toContain('Todavía no hay clubes');
    expect(reservas.agenda).not.toHaveBeenCalled();
  });

  it('muestra un estado vacío si el club no tiene pistas', async () => {
    clubes.pistas.mockReturnValue(of([]));
    await crear();

    expect(el().textContent).toContain('no tiene pistas');
    expect(reservas.agenda).not.toHaveBeenCalled();
  });

  it('muestra un estado vacío si la agenda no trae casillas', async () => {
    reservas.agenda.mockReturnValue(of([]));
    await crear();

    expect(el().textContent).toContain('No hay casillas');
  });

  it('muestra el error si falla la carga de la agenda', async () => {
    reservas.agenda.mockReturnValue(
      throwError(
        () => new HttpErrorResponse({ status: 404, error: { error: 'Pista no encontrada' } }),
      ),
    );
    await crear();

    expect(el().querySelector('[role="alert"]')?.textContent).toContain('Pista no encontrada');
  });

  it('abre el diálogo de confirmación con los datos de la casilla al pulsar una libre', async () => {
    await crear();

    casillas()[0].click();

    expect(dialogo.open).toHaveBeenCalledTimes(1);
    const datos = dialogo.open.mock.calls[0][1].data;
    expect(datos.lineas).toEqual(
      expect.arrayContaining([
        { etiqueta: 'Club', valor: 'Club Víbora' },
        { etiqueta: 'Pista', valor: 'Pista 1' },
        { etiqueta: 'Hora', valor: '18:00 – 19:30' },
      ]),
    );
    expect(JSON.stringify(datos.lineas)).toMatch(/12,00/);
  });

  it('no abre el diálogo al pulsar una casilla no disponible', async () => {
    await crear();

    casillas()[1].click();

    expect(dialogo.open).not.toHaveBeenCalled();
  });

  it('al confirmar crea la reserva, avisa y refresca la agenda', async () => {
    reservas.crear.mockReturnValue(of({ id: 99 }));
    await crear();
    reservas.agenda.mockClear();

    casillas()[0].click();
    await fixture.whenStable();

    expect(reservas.crear).toHaveBeenCalledWith(1, 10, LIBRE.inicio);
    expect(snack.open.mock.calls[0][0]).toContain('Reserva confirmada');
    expect(reservas.agenda).toHaveBeenCalledTimes(1);
  });

  it('si se cierra el diálogo sin confirmar no reserva nada', async () => {
    respuestaDialogo(false);
    await crear();

    casillas()[0].click();
    await fixture.whenStable();

    expect(reservas.crear).not.toHaveBeenCalled();
  });

  it('ante un 409 muestra el mensaje y refresca la agenda', async () => {
    reservas.crear.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { error: 'La pista ya está reservada en ese horario' },
          }),
      ),
    );
    await crear();
    reservas.agenda.mockClear();

    casillas()[0].click();
    await fixture.whenStable();

    expect(snack.open.mock.calls[0][0]).toContain('La pista ya está reservada en ese horario');
    expect(reservas.agenda).toHaveBeenCalledTimes(1);
  });

  it('ante un 400 muestra el mensaje del backend', async () => {
    reservas.crear.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: { error: 'No se puede reservar una casilla que ya ha empezado' },
          }),
      ),
    );
    await crear();

    casillas()[0].click();
    await fixture.whenStable();

    expect(snack.open.mock.calls[0][0]).toContain('ya ha empezado');
  });
});
