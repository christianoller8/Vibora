import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { NEVER, Subject, of, throwError } from 'rxjs';
import { ClubService } from '../../../core/api/club.service';
import { ConfirmDialog } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { BloqueoCreado, BloqueoPista, ReservaAfectada } from '../../../shared/models/bloqueo.model';
import { Pista } from '../../../shared/models/pista.model';
import { BloqueoService } from '../bloqueo.service';
import { AfectadasDialog } from './afectadas-dialog';
import { BloqueoDialog } from './bloqueo-dialog';
import { BloqueosClub } from './bloqueos-club';

const pista = (id: number, nombre: string, activa = true): Pista => ({
  id,
  nombre,
  tipo: 'INTERIOR',
  activa,
  clubId: 5,
});

const PISTAS = [pista(10, 'Pista 1'), pista(11, 'Pista 2'), pista(12, 'Pista 3', false)];

function bloqueo(id: number, c: Partial<BloqueoPista> = {}): BloqueoPista {
  return {
    id,
    pistaId: 10,
    pistaNombre: 'Pista 1',
    incidenciaId: null,
    motivo: `Motivo ${id}`,
    desde: '2026-07-15T16:00:00Z',
    hasta: null,
    creadoPor: { id: 9, nombre: 'Marta Ruiz' },
    creadoEn: '2026-07-15T15:00:00Z',
    levantadoEn: null,
    levantadoPor: null,
    activo: true,
    ...c,
  };
}

const ACTIVO = bloqueo(3, { motivo: 'Foco fundido', incidenciaId: 7 });
const LEVANTADO = bloqueo(4, {
  motivo: 'Red cambiada',
  levantadoEn: '2026-07-16T09:00:00Z',
  levantadoPor: { id: 9, nombre: 'Marta Ruiz' },
  activo: false,
});
const CADUCADO = bloqueo(5, {
  motivo: 'Pintura',
  hasta: '2020-01-01T10:00:00Z',
  desde: '2020-01-01T08:00:00Z',
  activo: false,
});

const AFECTADA = (id: number): ReservaAfectada => ({
  id,
  inicio: '2026-07-15T16:00:00Z',
  fin: '2026-07-15T17:30:00Z',
  organizador: { id: 1, nombre: 'Ana García López', email: 'ana@x.es' },
  jugadores: [{ id: 1, nombre: 'Ana García López' }],
});

const http = (status: number, error?: unknown): HttpErrorResponse =>
  new HttpErrorResponse({ status, error });

describe('BloqueosClub', () => {
  let fixture: ComponentFixture<BloqueosClub>;
  const clubes = { pistas: vi.fn() };
  const bloqueos = {
    listar: vi.fn(),
    afectadas: vi.fn(),
    levantar: vi.fn(),
    cancelarAfectadas: vi.fn(),
    crear: vi.fn(),
  };
  const dialogo = { open: vi.fn() };
  const snack = { open: vi.fn() };

  async function crear(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [BloqueosClub],
      providers: [
        provideRouter([]),
        { provide: ClubService, useValue: clubes },
        { provide: BloqueoService, useValue: bloqueos },
        { provide: MatDialog, useValue: dialogo },
        { provide: MatSnackBar, useValue: snack },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(BloqueosClub);
    fixture.componentRef.setInput('clubId', 5);
    await fixture.whenStable();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const q = <T extends Element>(s: string): T | null => el().querySelector<T>(s);
  const texto = (e: Element | null): string => e?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  const filas = (): HTMLElement[] => Array.from(el().querySelectorAll<HTMLElement>('tr.bloqueo'));
  const accion = (fila: HTMLElement, nombre: string): HTMLButtonElement | null =>
    fila.querySelector<HTMLButtonElement>(`button[data-accion="${nombre}"]`);
  const cierraCon = (resultado: unknown): void => {
    dialogo.open.mockReturnValue({ afterClosed: () => of(resultado) });
  };
  const aplicar = async (): Promise<void> => {
    await fixture.whenStable();
    fixture.detectChanges();
  };
  const componente = (): BloqueosClub => fixture.componentInstance;

  beforeEach(() => {
    vi.resetAllMocks();
    clubes.pistas.mockReturnValue(of(PISTAS));
    bloqueos.listar.mockReturnValue(of([ACTIVO]));
    cierraCon(undefined);
  });

  describe('carga y filtros', () => {
    it('pide las pistas del club, elige la primera y pide SUS bloqueos activos', async () => {
      await crear();

      expect(clubes.pistas).toHaveBeenCalledWith(5);
      expect(bloqueos.listar).toHaveBeenCalledWith(5, 10, true);
      expect(componente().pistaCtrl.value).toBe(10);
      expect(componente().filtroCtrl.value).toBe('ACTIVOS');
    });

    it('el selector lleva todas las pistas del club, también las dadas de baja', async () => {
      await crear();

      const opciones = Array.from(
        el().querySelectorAll<HTMLOptionElement>('select[name="pista"] option'),
      ).map((o) => texto(o));
      expect(opciones).toEqual(['Pista 1', 'Pista 2', 'Pista 3 (de baja)']);
    });

    it('el filtro tiene tres opciones en español: Activos, Levantados y Todos', async () => {
      await crear();

      const toggles = Array.from(el().querySelectorAll('mat-button-toggle')).map((t) => texto(t));
      expect(toggles).toEqual(['Activos', 'Levantados', 'Todos']);
      expect(q('mat-button-toggle-group')?.getAttribute('aria-label')).toBe(
        'Filtrar bloqueos por estado',
      );
    });

    it('cada filtro llama al backend con su valor de "activos": true, false o ninguno', async () => {
      await crear();

      componente().filtroCtrl.setValue('LEVANTADOS');
      await aplicar();
      expect(bloqueos.listar).toHaveBeenLastCalledWith(5, 10, false);

      componente().filtroCtrl.setValue('TODOS');
      await aplicar();
      expect(bloqueos.listar).toHaveBeenLastCalledWith(5, 10, undefined);

      componente().filtroCtrl.setValue('ACTIVOS');
      await aplicar();
      expect(bloqueos.listar).toHaveBeenLastCalledWith(5, 10, true);
    });

    it('cambiar de pista recarga con el filtro vigente', async () => {
      await crear();
      componente().filtroCtrl.setValue('TODOS');
      await aplicar();

      componente().pistaCtrl.setValue(11);
      await aplicar();

      expect(bloqueos.listar).toHaveBeenLastCalledWith(5, 11, undefined);
    });

    it('mientras carga muestra un spinner accesible', async () => {
      bloqueos.listar.mockReturnValue(NEVER);

      await crear();

      expect(q('mat-spinner')?.getAttribute('aria-label')).toBe('Cargando bloqueos');
      expect(q('table')).toBeNull();
    });

    it('un club sin pistas lo dice, no pide bloqueos y no deja crear', async () => {
      clubes.pistas.mockReturnValue(of([]));

      await crear();

      expect(texto(el())).toContain('no tiene pistas');
      expect(bloqueos.listar).not.toHaveBeenCalled();
      expect(q<HTMLButtonElement>('button[data-accion="nuevo"]')!.disabled).toBe(true);
    });

    it('si fallan las pistas muestra el error y permite reintentar', async () => {
      clubes.pistas.mockReturnValueOnce(throwError(() => http(500)));
      await crear();

      expect(q('[role="alert"]')?.textContent).toContain('No se pudieron cargar las pistas');
      q<HTMLButtonElement>('button[data-accion="reintentar"]')!.click();
      await aplicar();

      expect(filas()).toHaveLength(1);
    });

    it('si falla la lista de bloqueos muestra el motivo y permite reintentar', async () => {
      bloqueos.listar.mockReturnValueOnce(
        throwError(() => http(403, { error: 'Acceso denegado' })),
      );
      await crear();

      expect(q('[role="alert"]')?.textContent).toContain('Acceso denegado');
      q<HTMLButtonElement>('button[data-accion="reintentar"]')!.click();
      await aplicar();

      expect(filas()).toHaveLength(1);
      expect(q('[role="alert"]')).toBeNull();
    });

    it.each<[string, string]>([
      ['ACTIVOS', 'No hay bloqueos activos en esta pista.'],
      ['LEVANTADOS', 'No hay bloqueos levantados ni caducados en esta pista.'],
      ['TODOS', 'Esta pista no tiene bloqueos.'],
    ])('lista vacía con el filtro %s dice "%s"', async (filtro, mensaje) => {
      bloqueos.listar.mockReturnValue(of([]));
      await crear();

      componente().filtroCtrl.setValue(filtro as 'ACTIVOS' | 'LEVANTADOS' | 'TODOS');
      await aplicar();

      expect(texto(q('.vacio'))).toBe(mensaje);
    });
  });

  describe('filas', () => {
    beforeEach(() => {
      bloqueos.listar.mockReturnValue(of([ACTIVO, LEVANTADO, CADUCADO]));
    });

    it('muestra pista, motivo, vigencia en hora de Madrid, estado y quién lo creó', async () => {
      await crear();

      expect(q('table')?.getAttribute('aria-label')).toBe('Bloqueos de la pista');
      expect(filas()).toHaveLength(3);
      const fila = filas()[0];
      expect(texto(fila.querySelector('.pista'))).toBe('Pista 1');
      expect(texto(fila.querySelector('.motivo'))).toBe('Foco fundido');
      expect(texto(fila.querySelector('.vigencia'))).toBe(
        '15 de julio de 2026, 18:00 → hasta que se resuelva',
      );
      expect(texto(fila.querySelector('.creador'))).toContain('Marta Ruiz');
    });

    it('con fecha de fin la muestra', async () => {
      bloqueos.listar.mockReturnValue(
        of([bloqueo(8, { hasta: '2099-07-20T10:00:00Z', desde: '2099-07-19T10:00:00Z' })]),
      );

      await crear();

      expect(texto(filas()[0].querySelector('.vigencia'))).toBe(
        '19 de julio de 2099, 12:00 → 20 de julio de 2099, 12:00',
      );
    });

    it('el estado sale de levantadoEn y hasta: Activo, Levantado y Caducado', async () => {
      await crear();

      const estados = filas().map((f) => texto(f.querySelector('mat-chip')));
      expect(estados).toEqual(['Activo', 'Levantado', 'Caducado']);
    });

    it('un bloqueo ligado a una incidencia enlaza a su detalle; los demás no llevan enlace', async () => {
      await crear();

      const enlace = filas()[0].querySelector<HTMLAnchorElement>('a.incidencia');
      expect(enlace?.getAttribute('href')).toBe('/incidencias/7');
      expect(enlace?.getAttribute('aria-label')).toBe('Ver la incidencia 7');
      expect(filas()[1].querySelector('a.incidencia')).toBeNull();
    });

    it('un bloqueo levantado muestra quién lo levantó', async () => {
      await crear();

      expect(texto(filas()[1])).toContain('Levantado');
      expect(texto(filas()[1].querySelector('.levantado'))).toContain('Marta Ruiz');
    });

    it('las acciones solo aparecen en los bloqueos activos', async () => {
      await crear();

      for (const nombre of ['ver-afectadas', 'cancelar-afectadas', 'levantar']) {
        expect(accion(filas()[0], nombre)).not.toBeNull();
        expect(accion(filas()[1], nombre)).toBeNull();
        expect(accion(filas()[2], nombre)).toBeNull();
      }
    });

    it('los botones de la fila tienen etiqueta accesible con el motivo', async () => {
      await crear();

      // el nombre accesible empieza por el texto visible del botón
      expect(accion(filas()[0], 'ver-afectadas')!.getAttribute('aria-label')).toBe(
        'Ver afectadas del bloqueo «Foco fundido» de Pista 1',
      );
      expect(accion(filas()[0], 'cancelar-afectadas')!.getAttribute('aria-label')).toBe(
        'Cancelar afectadas del bloqueo «Foco fundido» de Pista 1',
      );
      expect(accion(filas()[0], 'levantar')!.getAttribute('aria-label')).toBe(
        'Levantar bloqueo «Foco fundido» de Pista 1',
      );
    });
  });

  describe('ver afectadas', () => {
    it('abre el diálogo de afectadas con el club, la pista y el bloqueo (él las carga)', async () => {
      await crear();

      accion(filas()[0], 'ver-afectadas')!.click();

      const [componenteDialogo, config] = dialogo.open.mock.calls[0];
      expect(componenteDialogo).toBe(AfectadasDialog);
      expect(config.data).toEqual({
        clubId: 5,
        pistaId: 10,
        pistaNombre: 'Pista 1',
        bloqueoId: 3,
      });
      expect(config.maxWidth).toBe('95vw');
    });
  });

  describe('cancelar afectadas', () => {
    it('averigua cuántas son y pide confirmación diciendo cuántas se cancelan, sin cancelar aún', async () => {
      bloqueos.afectadas.mockReturnValue(of([AFECTADA(1), AFECTADA(2)]));
      await crear();

      accion(filas()[0], 'cancelar-afectadas')!.click();
      await aplicar();

      expect(bloqueos.afectadas).toHaveBeenCalledWith(5, 10, 3);
      const [componenteDialogo, config] = dialogo.open.mock.calls[0];
      expect(componenteDialogo).toBe(ConfirmDialog);
      expect(config.data.mensaje).toContain('2 reservas');
      expect(config.data.mensaje).toContain('cargos');
      expect(config.data.mensaje).toContain('jugadores');
      expect(bloqueos.cancelarAfectadas).not.toHaveBeenCalled();
    });

    it('al confirmar cancela y muestra el recuento de canceladas', async () => {
      bloqueos.afectadas.mockReturnValue(of([AFECTADA(1), AFECTADA(2)]));
      bloqueos.cancelarAfectadas.mockReturnValue(of({ canceladas: 2, ids: [1, 2] }));
      await crear();
      cierraCon(true);

      accion(filas()[0], 'cancelar-afectadas')!.click();
      await aplicar();

      expect(bloqueos.cancelarAfectadas).toHaveBeenCalledWith(5, 10, 3);
      expect(snack.open.mock.calls[0][0]).toContain('Se cancelaron 2 reservas');
    });

    it('si se rechaza la confirmación no se cancela nada', async () => {
      bloqueos.afectadas.mockReturnValue(of([AFECTADA(1)]));
      await crear();
      cierraCon(false);

      accion(filas()[0], 'cancelar-afectadas')!.click();
      await aplicar();

      expect(bloqueos.cancelarAfectadas).not.toHaveBeenCalled();
      expect(snack.open).not.toHaveBeenCalled();
    });

    it('si no hay afectadas lo dice sin pedir confirmación ni cancelar (repetir la acción no hace nada)', async () => {
      bloqueos.afectadas.mockReturnValue(of([]));
      await crear();

      accion(filas()[0], 'cancelar-afectadas')!.click();
      await aplicar();

      expect(snack.open.mock.calls[0][0]).toBe('No había reservas afectadas.');
      expect(dialogo.open).not.toHaveBeenCalled();
      expect(bloqueos.cancelarAfectadas).not.toHaveBeenCalled();
    });

    it('si al cancelar el servidor devuelve 0 (otra persona se adelantó) dice "No había reservas afectadas"', async () => {
      bloqueos.afectadas.mockReturnValue(of([AFECTADA(1)]));
      bloqueos.cancelarAfectadas.mockReturnValue(of({ canceladas: 0, ids: [] }));
      await crear();
      cierraCon(true);

      accion(filas()[0], 'cancelar-afectadas')!.click();
      await aplicar();

      expect(snack.open.mock.calls[0][0]).toBe('No había reservas afectadas.');
    });

    it('un 409 (el bloqueo ya no está activo) se avisa con el mensaje del backend y se recarga la lista', async () => {
      bloqueos.afectadas.mockReturnValue(of([AFECTADA(1)]));
      bloqueos.cancelarAfectadas.mockReturnValue(
        throwError(() => http(409, { error: 'El bloqueo ya no está activo' })),
      );
      await crear();
      cierraCon(true);
      bloqueos.listar.mockClear();
      bloqueos.listar.mockReturnValue(of([]));

      accion(filas()[0], 'cancelar-afectadas')!.click();
      await aplicar();

      expect(snack.open.mock.calls[0][0]).toBe('El bloqueo ya no está activo');
      expect(bloqueos.listar).toHaveBeenCalledTimes(1);
    });

    it('si falla averiguar las afectadas avisa y no abre la confirmación', async () => {
      bloqueos.afectadas.mockReturnValue(throwError(() => http(0)));
      await crear();

      accion(filas()[0], 'cancelar-afectadas')!.click();
      await aplicar();

      expect(snack.open.mock.calls[0][0]).toContain('No se pudo conectar');
      expect(dialogo.open).not.toHaveBeenCalled();
    });

    it('mientras hay una petición en vuelo los botones de acción se deshabilitan', async () => {
      bloqueos.afectadas.mockReturnValue(new Subject<ReservaAfectada[]>());
      await crear();

      accion(filas()[0], 'cancelar-afectadas')!.click();
      await aplicar();

      expect(accion(filas()[0], 'levantar')!.disabled).toBe(true);
      expect(accion(filas()[0], 'cancelar-afectadas')!.disabled).toBe(true);
    });
  });

  describe('levantar', () => {
    it('pide confirmación con el motivo y la pista', async () => {
      await crear();

      accion(filas()[0], 'levantar')!.click();

      const [componenteDialogo, config] = dialogo.open.mock.calls[0];
      expect(componenteDialogo).toBe(ConfirmDialog);
      expect(config.data.titulo).toContain('Levantar');
      expect(config.data.lineas).toEqual(
        expect.arrayContaining([
          { etiqueta: 'Pista', valor: 'Pista 1' },
          { etiqueta: 'Motivo', valor: 'Foco fundido' },
        ]),
      );
      expect(bloqueos.levantar).not.toHaveBeenCalled();
    });

    it('al confirmar levanta, avisa y recarga la lista', async () => {
      bloqueos.levantar.mockReturnValue(of(undefined));
      await crear();
      cierraCon(true);
      bloqueos.listar.mockClear();
      bloqueos.listar.mockReturnValue(of([]));

      accion(filas()[0], 'levantar')!.click();
      await aplicar();

      expect(bloqueos.levantar).toHaveBeenCalledWith(5, 10, 3);
      expect(snack.open.mock.calls[0][0]).toContain('Bloqueo levantado');
      expect(bloqueos.listar).toHaveBeenCalledTimes(1);
      expect(filas()).toHaveLength(0);
    });

    it('si se rechaza la confirmación no se levanta', async () => {
      await crear();
      cierraCon(false);

      accion(filas()[0], 'levantar')!.click();
      await aplicar();

      expect(bloqueos.levantar).not.toHaveBeenCalled();
    });

    it('un error se avisa con el mensaje del backend', async () => {
      bloqueos.levantar.mockReturnValue(
        throwError(() => http(404, { error: 'Bloqueo no encontrado' })),
      );
      await crear();
      cierraCon(true);

      accion(filas()[0], 'levantar')!.click();
      await aplicar();

      expect(snack.open.mock.calls[0][0]).toBe('Bloqueo no encontrado');
    });
  });

  describe('nuevo bloqueo', () => {
    const creado = (
      afectadas: ReservaAfectada[],
      c: Partial<BloqueoPista> = {},
    ): BloqueoCreado => ({
      bloqueo: bloqueo(9, { motivo: 'Reparación', ...c }),
      afectadas,
    });

    it('abre el diálogo con el club, las pistas y la pista que se está viendo', async () => {
      await crear();
      componente().pistaCtrl.setValue(11);
      await aplicar();

      q<HTMLButtonElement>('button[data-accion="nuevo"]')!.click();

      const [componenteDialogo, config] = dialogo.open.mock.calls[0];
      expect(componenteDialogo).toBe(BloqueoDialog);
      expect(config.data).toEqual({ clubId: 5, pistas: PISTAS, pistaId: 11 });
    });

    it('si se cierra sin crear nada no avisa ni recarga', async () => {
      await crear();
      bloqueos.listar.mockClear();

      q<HTMLButtonElement>('button[data-accion="nuevo"]')!.click();
      await aplicar();

      expect(snack.open).not.toHaveBeenCalled();
      expect(bloqueos.listar).not.toHaveBeenCalled();
      expect(dialogo.open).toHaveBeenCalledTimes(1);
    });

    it('tras crearlo con reservas afectadas las muestra enseguida en el diálogo de afectadas y recarga', async () => {
      await crear();
      const respuesta = creado([AFECTADA(1), AFECTADA(2)]);
      dialogo.open
        .mockReturnValueOnce({ afterClosed: () => of(respuesta) })
        .mockReturnValue({ afterClosed: () => of(undefined) });
      bloqueos.listar.mockClear();

      q<HTMLButtonElement>('button[data-accion="nuevo"]')!.click();
      await aplicar();

      const [componenteDialogo, config] = dialogo.open.mock.calls[1];
      expect(componenteDialogo).toBe(AfectadasDialog);
      expect(config.data).toEqual({
        clubId: 5,
        pistaId: 10,
        pistaNombre: 'Pista 1',
        bloqueoId: 9,
        afectadas: respuesta.afectadas,
      });
      expect(snack.open.mock.calls[0][0]).toContain('Pista bloqueada');
      expect(snack.open.mock.calls[0][0]).toContain('2 reservas');
      expect(bloqueos.listar).toHaveBeenCalledTimes(1);
      expect(bloqueos.afectadas).not.toHaveBeenCalled();
    });

    it('si no hay reservas afectadas lo avisa y no abre el diálogo de afectadas', async () => {
      await crear();
      dialogo.open.mockReturnValueOnce({ afterClosed: () => of(creado([])) });

      q<HTMLButtonElement>('button[data-accion="nuevo"]')!.click();
      await aplicar();

      expect(snack.open.mock.calls[0][0]).toContain('No hay reservas afectadas');
      expect(dialogo.open).toHaveBeenCalledTimes(1);
    });

    it('si el bloqueo es de otra pista, la lista pasa a mostrar esa pista', async () => {
      await crear();
      dialogo.open.mockReturnValueOnce({
        afterClosed: () => of(creado([], { pistaId: 11, pistaNombre: 'Pista 2' })),
      });

      q<HTMLButtonElement>('button[data-accion="nuevo"]')!.click();
      await aplicar();

      expect(componente().pistaCtrl.value).toBe(11);
      expect(bloqueos.listar).toHaveBeenLastCalledWith(5, 11, true);
    });

    it('si se estaba viendo "Levantados", al crear pasa a "Activos" para que se vea el nuevo', async () => {
      await crear();
      componente().filtroCtrl.setValue('LEVANTADOS');
      await aplicar();
      dialogo.open.mockReturnValueOnce({ afterClosed: () => of(creado([])) });

      q<HTMLButtonElement>('button[data-accion="nuevo"]')!.click();
      await aplicar();

      expect(componente().filtroCtrl.value).toBe('ACTIVOS');
      expect(bloqueos.listar).toHaveBeenLastCalledWith(5, 10, true);
    });
  });
});
