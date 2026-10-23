import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject, of, throwError } from 'rxjs';
import { ClubService } from '../../../core/api/club.service';
import { Pista } from '../../../shared/models/pista.model';
import { PistaService } from '../pista.service';
import { PistaDialog } from './pista-dialog';
import { PistasClub } from './pistas-club';

function pista(id: number, cambios: Partial<Pista> = {}): Pista {
  return { id, nombre: `Pista ${id}`, tipo: 'INTERIOR', activa: true, clubId: 5, ...cambios };
}

const error = (status: number, mensaje: string): HttpErrorResponse =>
  new HttpErrorResponse({ status, error: { error: mensaje } });

describe('PistasClub', () => {
  let fixture: ComponentFixture<PistasClub>;
  const clubes = { pistas: vi.fn() };
  const pistas = { actualizar: vi.fn(), darDeBaja: vi.fn() };
  const dialogo = { open: vi.fn() };
  const snack = { open: vi.fn() };

  async function crear(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [PistasClub],
      providers: [
        { provide: ClubService, useValue: clubes },
        { provide: PistaService, useValue: pistas },
        { provide: MatDialog, useValue: dialogo },
        { provide: MatSnackBar, useValue: snack },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(PistasClub);
    fixture.componentRef.setInput('clubId', 5);
    await fixture.whenStable();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const filas = (): HTMLElement[] => Array.from(el().querySelectorAll<HTMLElement>('tr.pista'));
  const celdas = (fila: HTMLElement): string[] =>
    Array.from(fila.querySelectorAll('td')).map((c) => c.textContent!.replace(/\s+/g, ' ').trim());
  const accion = (fila: HTMLElement, nombre: string): HTMLButtonElement | null =>
    fila.querySelector(`button[data-accion="${nombre}"]`);
  const interruptor = (fila: HTMLElement): HTMLButtonElement =>
    fila.querySelector<HTMLButtonElement>('mat-slide-toggle button[role="switch"]')!;

  beforeEach(() => {
    vi.resetAllMocks();
    clubes.pistas.mockReturnValue(
      of([pista(1), pista(2, { tipo: 'EXTERIOR', activa: false }), pista(3, { tipo: 'CUBIERTA' })]),
    );
    dialogo.open.mockReturnValue({ afterClosed: () => of(undefined) });
  });

  it('pide las pistas del club indicado', async () => {
    await crear();

    expect(clubes.pistas).toHaveBeenCalledWith(5);
  });

  it('lista nombre y tipo en español de cada pista, también las inactivas', async () => {
    await crear();

    expect(filas()).toHaveLength(3);
    expect(celdas(filas()[0])[0]).toBe('Pista 1');
    expect(celdas(filas()[0])[1]).toBe('Interior');
    expect(celdas(filas()[1])[1]).toBe('Exterior');
    expect(celdas(filas()[2])[1]).toBe('Cubierta');
  });

  it('el interruptor refleja si la pista está activa', async () => {
    await crear();

    expect(interruptor(filas()[0]).getAttribute('aria-checked')).toBe('true');
    expect(interruptor(filas()[1]).getAttribute('aria-checked')).toBe('false');
  });

  it('los botones de icono llevan etiquetas accesibles con el nombre de la pista', async () => {
    await crear();

    expect(accion(filas()[0], 'editar')!.getAttribute('aria-label')).toBe('Editar Pista 1');
    expect(accion(filas()[0], 'baja')!.getAttribute('aria-label')).toBe('Dar de baja Pista 1');
    expect(interruptor(filas()[0]).getAttribute('aria-label')).toBe('Pista 1 activa');
  });

  it('muestra un estado vacío cuando no hay pistas', async () => {
    clubes.pistas.mockReturnValue(of([]));
    await crear();

    expect(el().textContent).toContain('Todavía no hay pistas');
  });

  it('muestra el error si falla la carga', async () => {
    clubes.pistas.mockReturnValue(throwError(() => error(500, 'boom')));
    await crear();

    expect(el().querySelector('[role="alert"]')).not.toBeNull();
  });

  it('muestra el spinner mientras carga', async () => {
    clubes.pistas.mockReturnValue(new Subject<Pista[]>());
    await crear();

    expect(el().querySelector('mat-spinner')).not.toBeNull();
  });

  describe('activar / desactivar', () => {
    it('al apagar el interruptor hace PUT con activa=false conservando nombre y tipo', async () => {
      pistas.actualizar.mockReturnValue(of(pista(1, { activa: false })));
      await crear();

      interruptor(filas()[0]).click();
      await fixture.whenStable();

      expect(pistas.actualizar).toHaveBeenCalledWith(5, 1, {
        nombre: 'Pista 1',
        tipo: 'INTERIOR',
        activa: false,
      });
    });

    it('al encender una inactiva hace PUT con activa=true', async () => {
      pistas.actualizar.mockReturnValue(of(pista(2, { tipo: 'EXTERIOR', activa: true })));
      await crear();

      interruptor(filas()[1]).click();
      await fixture.whenStable();

      expect(pistas.actualizar).toHaveBeenCalledWith(5, 2, {
        nombre: 'Pista 2',
        tipo: 'EXTERIOR',
        activa: true,
      });
      expect(interruptor(filas()[1]).getAttribute('aria-checked')).toBe('true');
    });

    it('si el backend lo rechaza deja el interruptor como estaba y muestra el motivo', async () => {
      pistas.actualizar.mockReturnValue(throwError(() => error(403, 'Acceso denegado')));
      await crear();

      interruptor(filas()[0]).click();
      await fixture.whenStable();

      expect(interruptor(filas()[0]).getAttribute('aria-checked')).toBe('true');
      expect(snack.open.mock.calls[0][0]).toBe('Acceso denegado');
    });
  });

  describe('nueva pista', () => {
    it('abre el diálogo de alta con el club', async () => {
      await crear();

      el().querySelector<HTMLButtonElement>('button[data-accion="nueva"]')!.click();

      const [componente, opciones] = dialogo.open.mock.calls[0];
      expect(componente).toBe(PistaDialog);
      expect(opciones.data).toEqual({ clubId: 5 });
    });

    it('si se guarda, avisa y recarga la lista', async () => {
      dialogo.open.mockReturnValue({ afterClosed: () => of(pista(9)) });
      await crear();
      clubes.pistas.mockClear();

      el().querySelector<HTMLButtonElement>('button[data-accion="nueva"]')!.click();
      await fixture.whenStable();

      expect(snack.open.mock.calls[0][0]).toContain('Pista creada');
      expect(clubes.pistas).toHaveBeenCalledTimes(1);
    });

    it('si se descarta el diálogo no recarga ni avisa', async () => {
      await crear();
      clubes.pistas.mockClear();

      el().querySelector<HTMLButtonElement>('button[data-accion="nueva"]')!.click();
      await fixture.whenStable();

      expect(snack.open).not.toHaveBeenCalled();
      expect(clubes.pistas).not.toHaveBeenCalled();
    });
  });

  describe('editar', () => {
    it('abre el diálogo con la pista y, al guardar, avisa y recarga', async () => {
      dialogo.open.mockReturnValue({ afterClosed: () => of(pista(1, { nombre: 'Central' })) });
      await crear();
      clubes.pistas.mockClear();

      accion(filas()[0], 'editar')!.click();
      await fixture.whenStable();

      const [componente, opciones] = dialogo.open.mock.calls[0];
      expect(componente).toBe(PistaDialog);
      expect(opciones.data).toEqual({ clubId: 5, pista: pista(1) });
      expect(snack.open.mock.calls[0][0]).toContain('Pista actualizada');
      expect(clubes.pistas).toHaveBeenCalledTimes(1);
    });
  });

  describe('dar de baja', () => {
    it('pide confirmación explicando que es una baja y que las reservas se mantienen', async () => {
      dialogo.open.mockReturnValue({ afterClosed: () => of(false) });
      await crear();

      accion(filas()[0], 'baja')!.click();
      await fixture.whenStable();

      const datos = dialogo.open.mock.calls[0][1].data;
      expect(datos.titulo).toContain('Dar de baja');
      expect(datos.mensaje).toContain('reservas');
      expect(datos.lineas).toContainEqual({ etiqueta: 'Pista', valor: 'Pista 1' });
      expect(pistas.darDeBaja).not.toHaveBeenCalled();
    });

    it('tras confirmar hace DELETE, avisa y recarga', async () => {
      dialogo.open.mockReturnValue({ afterClosed: () => of(true) });
      pistas.darDeBaja.mockReturnValue(of(undefined));
      await crear();
      clubes.pistas.mockClear();

      accion(filas()[0], 'baja')!.click();
      await fixture.whenStable();

      expect(pistas.darDeBaja).toHaveBeenCalledWith(5, 1);
      expect(snack.open.mock.calls[0][0]).toContain('de baja');
      expect(clubes.pistas).toHaveBeenCalledTimes(1);
    });

    it('si falla muestra el mensaje del backend', async () => {
      dialogo.open.mockReturnValue({ afterClosed: () => of(true) });
      pistas.darDeBaja.mockReturnValue(throwError(() => error(404, 'Pista no encontrada')));
      await crear();

      accion(filas()[0], 'baja')!.click();
      await fixture.whenStable();

      expect(snack.open.mock.calls[0][0]).toBe('Pista no encontrada');
    });

    it('una pista ya inactiva no ofrece darla de baja otra vez', async () => {
      await crear();

      expect(accion(filas()[1], 'baja')).toBeNull();
      expect(accion(filas()[1], 'editar')).not.toBeNull();
    });
  });
});
