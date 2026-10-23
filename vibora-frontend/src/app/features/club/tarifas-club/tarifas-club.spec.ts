import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject, of, throwError } from 'rxjs';
import { Tarifa } from '../../../shared/models/tarifa.model';
import { TarifaService } from '../tarifa.service';
import { TarifaDialog } from './tarifa-dialog';
import { TarifasClub } from './tarifas-club';

function tarifa(id: number, cambios: Partial<Tarifa> = {}): Tarifa {
  return {
    id,
    clubId: 5,
    diaSemana: 'MONDAY',
    horaInicio: '09:00:00',
    horaFin: '14:00:00',
    precioHora: 12.5,
    tipo: 'NORMAL',
    ...cambios,
  };
}

/** Desordenadas a propósito: domingo primero y, dentro del lunes, la tarde antes que la mañana. */
const TARIFAS: Tarifa[] = [
  tarifa(1, { diaSemana: 'SUNDAY', horaInicio: '10:00:00', horaFin: '12:00:00', precioHora: 20 }),
  tarifa(2, {
    diaSemana: 'MONDAY',
    horaInicio: '14:00:00',
    horaFin: '22:00:00',
    tipo: 'HORA_PUNTA',
    precioHora: 18,
  }),
  tarifa(3, { diaSemana: 'MONDAY' }),
  tarifa(4, {
    diaSemana: 'WEDNESDAY',
    horaInicio: '18:00:00',
    horaFin: '23:00:00',
    tipo: 'LUZ_ARTIFICIAL',
  }),
];

const error = (status: number, mensaje: string): HttpErrorResponse =>
  new HttpErrorResponse({ status, error: { error: mensaje } });

describe('TarifasClub', () => {
  let fixture: ComponentFixture<TarifasClub>;
  const tarifas = { listar: vi.fn(), eliminar: vi.fn() };
  const dialogo = { open: vi.fn() };
  const snack = { open: vi.fn() };

  async function crear(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [TarifasClub],
      providers: [
        { provide: TarifaService, useValue: tarifas },
        { provide: MatDialog, useValue: dialogo },
        { provide: MatSnackBar, useValue: snack },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(TarifasClub);
    fixture.componentRef.setInput('clubId', 5);
    await fixture.whenStable();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const dias = (): HTMLElement[] => Array.from(el().querySelectorAll<HTMLElement>('section.dia'));
  const franjas = (dia: HTMLElement): HTMLElement[] =>
    Array.from(dia.querySelectorAll<HTMLElement>('.franja'));
  const texto = (e: HTMLElement): string => e.textContent!.replace(/\s+/g, ' ').trim();
  const accion = (franja: HTMLElement, nombre: string): HTMLButtonElement | null =>
    franja.querySelector(`button[data-accion="${nombre}"]`);

  beforeEach(() => {
    vi.resetAllMocks();
    tarifas.listar.mockReturnValue(of(TARIFAS));
    dialogo.open.mockReturnValue({ afterClosed: () => of(undefined) });
  });

  it('pide las tarifas del club indicado', async () => {
    await crear();

    expect(tarifas.listar).toHaveBeenCalledWith(5);
  });

  it('agrupa por día de la semana con nombres en español, de lunes a domingo', async () => {
    await crear();

    const titulos = dias().map((d) => d.querySelector('h3')!.textContent!.trim());
    expect(titulos).toEqual(['Lunes', 'Miércoles', 'Domingo']);
  });

  it('no pinta los días sin franjas', async () => {
    await crear();

    expect(dias()).toHaveLength(3);
    expect(el().textContent).not.toContain('Martes');
  });

  it('ordena las franjas de cada día por hora de inicio', async () => {
    await crear();

    const lunes = franjas(dias()[0]);
    expect(lunes).toHaveLength(2);
    expect(texto(lunes[0])).toContain('09:00 – 14:00');
    expect(texto(lunes[1])).toContain('14:00 – 22:00');
  });

  it('muestra el precio en euros por hora (es-ES)', async () => {
    await crear();

    expect(texto(franjas(dias()[0])[0])).toMatch(/12,50\s€\/h/);
    expect(texto(franjas(dias()[0])[1])).toMatch(/18,00\s€\/h/);
  });

  it('muestra el tipo como chip en español', async () => {
    await crear();

    expect(franjas(dias()[0])[0].querySelector('.tipo')!.textContent!.trim()).toBe('Normal');
    expect(franjas(dias()[0])[1].querySelector('.tipo')!.textContent!.trim()).toBe('Hora punta');
    expect(franjas(dias()[1])[0].querySelector('.tipo')!.textContent!.trim()).toBe(
      'Luz artificial',
    );
  });

  it('los botones de icono llevan etiqueta accesible con día y horas', async () => {
    await crear();

    const franja = franjas(dias()[0])[0];
    expect(accion(franja, 'editar')!.getAttribute('aria-label')).toBe(
      'Editar la tarifa del lunes de 09:00 a 14:00',
    );
    expect(accion(franja, 'eliminar')!.getAttribute('aria-label')).toBe(
      'Eliminar la tarifa del lunes de 09:00 a 14:00',
    );
  });

  it('muestra un estado vacío que avisa de que sin tarifas no se puede reservar', async () => {
    tarifas.listar.mockReturnValue(of([]));
    await crear();

    expect(el().textContent).toContain('Todavía no hay tarifas');
    expect(el().textContent).toContain('no se pueden reservar');
  });

  it('muestra el error si falla la carga', async () => {
    tarifas.listar.mockReturnValue(throwError(() => error(500, 'boom')));
    await crear();

    expect(el().querySelector('[role="alert"]')).not.toBeNull();
  });

  it('muestra el spinner mientras carga', async () => {
    tarifas.listar.mockReturnValue(new Subject<Tarifa[]>());
    await crear();

    expect(el().querySelector('mat-spinner')).not.toBeNull();
  });

  describe('nueva tarifa', () => {
    it('abre el diálogo de alta con el club', async () => {
      await crear();

      el().querySelector<HTMLButtonElement>('button[data-accion="nueva"]')!.click();

      const [componente, opciones] = dialogo.open.mock.calls[0];
      expect(componente).toBe(TarifaDialog);
      expect(opciones.data).toEqual({ clubId: 5 });
    });

    it('si se guarda, avisa y recarga', async () => {
      dialogo.open.mockReturnValue({ afterClosed: () => of(tarifa(9)) });
      await crear();
      tarifas.listar.mockClear();

      el().querySelector<HTMLButtonElement>('button[data-accion="nueva"]')!.click();
      await fixture.whenStable();

      expect(snack.open.mock.calls[0][0]).toContain('Tarifa creada');
      expect(tarifas.listar).toHaveBeenCalledTimes(1);
    });

    it('si se descarta no avisa ni recarga', async () => {
      await crear();
      tarifas.listar.mockClear();

      el().querySelector<HTMLButtonElement>('button[data-accion="nueva"]')!.click();
      await fixture.whenStable();

      expect(snack.open).not.toHaveBeenCalled();
      expect(tarifas.listar).not.toHaveBeenCalled();
    });
  });

  describe('editar', () => {
    it('abre el diálogo con la franja y, al guardar, avisa y recarga', async () => {
      dialogo.open.mockReturnValue({ afterClosed: () => of(tarifa(3, { precioHora: 15 })) });
      await crear();
      tarifas.listar.mockClear();

      accion(franjas(dias()[0])[0], 'editar')!.click();
      await fixture.whenStable();

      const [componente, opciones] = dialogo.open.mock.calls[0];
      expect(componente).toBe(TarifaDialog);
      expect(opciones.data).toEqual({ clubId: 5, tarifa: tarifa(3) });
      expect(snack.open.mock.calls[0][0]).toContain('Tarifa actualizada');
      expect(tarifas.listar).toHaveBeenCalledTimes(1);
    });
  });

  describe('eliminar', () => {
    it('pide confirmación con día, horas y precio', async () => {
      dialogo.open.mockReturnValue({ afterClosed: () => of(false) });
      await crear();

      accion(franjas(dias()[0])[0], 'eliminar')!.click();
      await fixture.whenStable();

      const datos = dialogo.open.mock.calls[0][1].data;
      expect(datos.titulo).toContain('Eliminar');
      expect(datos.lineas).toContainEqual({ etiqueta: 'Día', valor: 'Lunes' });
      expect(datos.lineas).toContainEqual({ etiqueta: 'Horario', valor: '09:00 – 14:00' });
      expect(tarifas.eliminar).not.toHaveBeenCalled();
    });

    it('tras confirmar hace DELETE, avisa y recarga', async () => {
      dialogo.open.mockReturnValue({ afterClosed: () => of(true) });
      tarifas.eliminar.mockReturnValue(of(undefined));
      await crear();
      tarifas.listar.mockClear();

      accion(franjas(dias()[0])[0], 'eliminar')!.click();
      await fixture.whenStable();

      expect(tarifas.eliminar).toHaveBeenCalledWith(5, 3);
      expect(snack.open.mock.calls[0][0]).toContain('Tarifa eliminada');
      expect(tarifas.listar).toHaveBeenCalledTimes(1);
    });

    it('si falla muestra el mensaje del backend', async () => {
      dialogo.open.mockReturnValue({ afterClosed: () => of(true) });
      tarifas.eliminar.mockReturnValue(throwError(() => error(404, 'Tarifa no encontrada')));
      await crear();

      accion(franjas(dias()[0])[0], 'eliminar')!.click();
      await fixture.whenStable();

      expect(snack.open.mock.calls[0][0]).toBe('Tarifa no encontrada');
    });
  });
});
