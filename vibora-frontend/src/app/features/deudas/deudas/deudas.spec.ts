import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, throwError } from 'rxjs';
import { Cargo, EstadoCargo, Saldos } from '../../../shared/models/cargo.model';
import { CargoService } from '../cargo.service';
import { Deudas } from './deudas';

function cargo(id: number, estado: EstadoCargo = 'PENDIENTE', cambios: Partial<Cargo> = {}): Cargo {
  return {
    id,
    reservaId: 100 + id,
    clubNombre: 'Club Víbora Demo',
    pistaNombre: `Pista ${id}`,
    inicio: '2026-07-15T16:00:00Z',
    importe: 11.25,
    estado,
    deudor: { id: 3, nombre: 'Marta', apellidos: 'Ruiz' },
    acreedor: { id: 1, nombre: 'Ana', apellidos: 'García López' },
    pagadoEn: estado === 'PAGADO' ? '2026-07-10T09:30:00Z' : null,
    ...cambios,
  };
}

const SALDOS: Saldos = {
  saldos: [
    { usuarioId: 1, nombre: 'Ana', apellidos: 'García', debo: 22.5, meDeben: 0, neto: -22.5 },
    { usuarioId: 2, nombre: 'Luis', apellidos: null, debo: 0, meDeben: 11.25, neto: 11.25 },
  ],
  totalDebo: 22.5,
  totalMeDeben: 11.25,
  totalNeto: -11.25,
};

describe('Deudas', () => {
  let fixture: ComponentFixture<Deudas>;
  const cargos = { mios: vi.fn(), porCobrar: vi.fn(), pagar: vi.fn(), saldos: vi.fn() };
  const dialogo = { open: vi.fn() };
  const snack = { open: vi.fn() };

  async function crear(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [Deudas],
      providers: [
        { provide: CargoService, useValue: cargos },
        { provide: MatDialog, useValue: dialogo },
        { provide: MatSnackBar, useValue: snack },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(Deudas);
    await fixture.whenStable();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const lista = (nombre: 'debo' | 'me-deben'): HTMLElement[] =>
    Array.from(el().querySelectorAll<HTMLElement>(`[data-lista="${nombre}"] .cargo`));
  /** Material solo pinta el contenido de la pestaña activa: hay que seleccionarla como lo haría el usuario. */
  async function abrirMeDeben(): Promise<void> {
    el().querySelectorAll<HTMLElement>('[role="tab"]')[1].click();
    await fixture.whenStable();
  }
  const texto = (sel: string): string =>
    el().querySelector(sel)?.textContent?.replace(/\s+/g, ' ').trim() ?? '';

  beforeEach(() => {
    vi.resetAllMocks();
    cargos.mios.mockReturnValue(of([cargo(1), cargo(2, 'PAGADO')]));
    cargos.porCobrar.mockReturnValue(
      of([cargo(3, 'PENDIENTE', { deudor: { id: 4, nombre: 'Pepe', apellidos: 'Gil' } })]),
    );
    cargos.saldos.mockReturnValue(of(SALDOS));
    dialogo.open.mockReturnValue({ afterClosed: () => of(true) });
  });

  it('al entrar pide los pendientes de ambas listas y los saldos', async () => {
    await crear();

    expect(cargos.mios).toHaveBeenCalledWith('PENDIENTE');
    expect(cargos.porCobrar).toHaveBeenCalledWith('PENDIENTE');
    expect(cargos.saldos).toHaveBeenCalledTimes(1);
  });

  describe('resumen de saldos', () => {
    it('muestra lo que debo, lo que me deben y el neto', async () => {
      await crear();

      expect(texto('.total-debo')).toMatch(/22,50\s€/);
      expect(texto('.total-me-deben')).toMatch(/11,25\s€/);
      expect(texto('.total-neto')).toMatch(/-11,25\s€/);
    });

    it('colorea el neto: negativo cuando debo más, positivo con "+" cuando me deben más', async () => {
      await crear();
      expect(el().querySelector('.total-neto')!.classList).toContain('negativo');

      cargos.saldos.mockReturnValue(of({ ...SALDOS, totalDebo: 0, totalMeDeben: 5, totalNeto: 5 }));
      fixture.componentInstance['cargar']();
      await fixture.whenStable();

      expect(el().querySelector('.total-neto')!.classList).toContain('positivo');
      expect(texto('.total-neto')).toMatch(/^\+5,00\s€/);
    });

    it('lista el saldo con cada persona', async () => {
      await crear();

      const personas = Array.from(el().querySelectorAll('.saldo-persona')).map((p) =>
        p.textContent!.replace(/\s+/g, ' ').trim(),
      );
      expect(personas[0]).toContain('Ana García');
      expect(personas[0]).toMatch(/-22,50\s€/);
      expect(personas[1]).toContain('Luis');
      expect(personas[1]).toMatch(/\+11,25\s€/);
    });

    it('sin saldos pendientes lo dice', async () => {
      cargos.saldos.mockReturnValue(
        of({ saldos: [], totalDebo: 0, totalMeDeben: 0, totalNeto: 0 }),
      );
      await crear();

      expect(el().textContent).toContain('No tienes saldos pendientes');
    });
  });

  describe('pestañas y listas', () => {
    it('tiene las pestañas "Debo" y "Me deben"', async () => {
      await crear();

      const pestanas = Array.from(el().querySelectorAll('[role="tab"]')).map((t) =>
        t.textContent!.trim(),
      );
      expect(pestanas).toEqual(['Debo', 'Me deben']);
    });

    it('cada cargo muestra club · pista, fecha, importe, estado y contraparte', async () => {
      await crear();

      const primero = lista('debo')[0].textContent!.replace(/\s+/g, ' ');
      expect(primero).toContain('Club Víbora Demo · Pista 1');
      expect(primero).toContain('15 de julio de 2026');
      expect(primero).toContain('18:00');
      expect(primero).toMatch(/11,25\s€/);
      expect(primero).toContain('Pendiente');
      expect(primero).toContain('A: Ana García López');
    });

    it('en "Me deben" la contraparte es el deudor', async () => {
      await crear();
      await abrirMeDeben();

      expect(lista('me-deben')[0].textContent).toContain('De: Pepe Gil');
    });

    it('un cargo pagado muestra cuándo se pagó', async () => {
      await crear();

      const pagado = lista('debo')[1].textContent!.replace(/\s+/g, ' ');
      expect(pagado).toContain('Pagado');
      expect(pagado).toContain('Pagado el 10 de julio de 2026');
    });

    it('"Pagar" solo aparece en los PENDIENTES de "Debo"', async () => {
      await crear();

      expect(lista('debo')[0].querySelector('button[data-accion="pagar"]')).not.toBeNull();
      expect(lista('debo')[1].querySelector('button[data-accion="pagar"]')).toBeNull();
      await abrirMeDeben();
      expect(lista('me-deben')[0].querySelector('button[data-accion="pagar"]')).toBeNull();
    });

    it.each<[EstadoCargo, string]>([
      ['ANULADO', 'Anulado'],
      ['REEMBOLSADO', 'Reembolsado'],
    ])('el estado %s se muestra como "%s" y no se puede pagar', async (estado, etiqueta) => {
      cargos.mios.mockReturnValue(of([cargo(9, estado)]));
      await crear();

      expect(lista('debo')[0].textContent).toContain(etiqueta);
      expect(lista('debo')[0].querySelector('button[data-accion="pagar"]')).toBeNull();
    });

    it('muestra estados vacíos en cada lista', async () => {
      cargos.mios.mockReturnValue(of([]));
      cargos.porCobrar.mockReturnValue(of([]));
      await crear();
      expect(el().textContent).toContain('No tienes deudas pendientes');

      await abrirMeDeben();
      expect(el().textContent).toContain('Nadie te debe nada');
    });

    it('muestra el error si falla la carga', async () => {
      cargos.mios.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
      await crear();

      expect(el().querySelector('[role="alert"]')).not.toBeNull();
    });
  });

  describe('filtro de estado', () => {
    it('empieza en "Pendientes" y "Todos" quita el filtro de estado', async () => {
      await crear();
      const chips = Array.from(el().querySelectorAll('mat-chip-option'));
      expect(chips.map((c) => c.textContent!.trim())).toEqual(['Pendientes', 'Todos']);
      expect(chips[0].classList).toContain('mdc-evolution-chip--selected');
      cargos.mios.mockClear();
      cargos.porCobrar.mockClear();

      chips[1].querySelector<HTMLElement>('button.mdc-evolution-chip__action--primary')!.click();
      await fixture.whenStable();

      expect(cargos.mios).toHaveBeenCalledWith(undefined);
      expect(cargos.porCobrar).toHaveBeenCalledWith(undefined);
    });
  });

  describe('pagar', () => {
    const pulsarPagar = async (): Promise<void> => {
      lista('debo')[0].querySelector<HTMLButtonElement>('button[data-accion="pagar"]')!.click();
      await fixture.whenStable();
    };

    it('pide confirmación aclarando que el pago es simulado', async () => {
      dialogo.open.mockReturnValue({ afterClosed: () => of(false) });
      await crear();

      await pulsarPagar();

      const datos = dialogo.open.mock.calls[0][1].data;
      expect(datos.mensaje.toLowerCase()).toContain('simulado');
      expect(datos.mensaje.toLowerCase()).toContain('no se mueve dinero real');
      expect(datos.lineas).toContainEqual({ etiqueta: 'Pagar a', valor: 'Ana García López' });
      expect(cargos.pagar).not.toHaveBeenCalled();
    });

    it('tras confirmar paga, avisa y recarga ambas listas y los saldos', async () => {
      cargos.pagar.mockReturnValue(of(cargo(1, 'PAGADO')));
      await crear();
      cargos.mios.mockClear();
      cargos.porCobrar.mockClear();
      cargos.saldos.mockClear();

      await pulsarPagar();

      expect(cargos.pagar).toHaveBeenCalledWith(1);
      expect(snack.open.mock.calls[0][0]).toContain('Pago simulado');
      expect(cargos.mios).toHaveBeenCalledTimes(1);
      expect(cargos.porCobrar).toHaveBeenCalledTimes(1);
      expect(cargos.saldos).toHaveBeenCalledTimes(1);
    });

    it('si se descarta el diálogo no paga', async () => {
      dialogo.open.mockReturnValue({ afterClosed: () => of(false) });
      await crear();

      await pulsarPagar();

      expect(cargos.pagar).not.toHaveBeenCalled();
    });

    it('si el backend rechaza el pago muestra su mensaje y refresca', async () => {
      cargos.pagar.mockReturnValue(
        throwError(
          () => new HttpErrorResponse({ status: 409, error: { error: 'El cargo ya está pagado' } }),
        ),
      );
      await crear();
      cargos.saldos.mockClear();

      await pulsarPagar();

      expect(snack.open.mock.calls[0][0]).toBe('El cargo ya está pagado');
      expect(cargos.saldos).toHaveBeenCalledTimes(1);
    });
  });
});
