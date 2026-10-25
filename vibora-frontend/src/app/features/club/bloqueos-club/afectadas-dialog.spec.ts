import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { NEVER, Subject, of, throwError } from 'rxjs';
import { ConfirmDialog } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { CancelacionAfectadas, ReservaAfectada } from '../../../shared/models/bloqueo.model';
import { BloqueoService } from '../bloqueo.service';
import { AfectadasDialog, AfectadasDialogData } from './afectadas-dialog';

const R1: ReservaAfectada = {
  id: 1,
  inicio: '2026-07-15T16:00:00Z',
  fin: '2026-07-15T17:30:00Z',
  organizador: { id: 1, nombre: 'Ana García López', email: 'ana@x.es' },
  jugadores: [
    { id: 1, nombre: 'Ana García López' },
    { id: 2, nombre: 'Luis Pérez' },
    { id: 3, nombre: 'Eva Mora' },
  ],
};
const R2: ReservaAfectada = {
  id: 2,
  inicio: '2026-07-16T07:00:00Z',
  fin: '2026-07-16T08:30:00Z',
  organizador: { id: 2, nombre: 'Luis Pérez', email: 'luis@x.es' },
  jugadores: [{ id: 2, nombre: 'Luis Pérez' }],
};

const DATOS: AfectadasDialogData = {
  clubId: 5,
  pistaId: 10,
  pistaNombre: 'Pista 1',
  bloqueoId: 3,
};

const http = (status: number, error?: unknown): HttpErrorResponse =>
  new HttpErrorResponse({ status, error });

describe('AfectadasDialog', () => {
  let fixture: ComponentFixture<AfectadasDialog>;
  const bloqueos = { afectadas: vi.fn(), cancelarAfectadas: vi.fn() };
  const dialogo = { open: vi.fn() };
  const dialogRef = { close: vi.fn() };

  async function crear(datos: AfectadasDialogData = DATOS): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [AfectadasDialog],
      providers: [
        { provide: BloqueoService, useValue: bloqueos },
        { provide: MatDialog, useValue: dialogo },
        { provide: MAT_DIALOG_DATA, useValue: datos },
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AfectadasDialog);
    await fixture.whenStable();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const q = <T extends Element>(s: string): T | null => el().querySelector<T>(s);
  const texto = (e: Element | null): string => e?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  const filas = (): HTMLElement[] => Array.from(el().querySelectorAll<HTMLElement>('tr.afectada'));
  const cancelar = (): HTMLButtonElement =>
    q<HTMLButtonElement>('button[data-accion="cancelar-afectadas"]')!;
  const cierraCon = (resultado: unknown): void => {
    dialogo.open.mockReturnValue({ afterClosed: () => of(resultado) });
  };

  async function pulsarCancelar(): Promise<void> {
    cancelar().click();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(() => {
    vi.resetAllMocks();
    bloqueos.afectadas.mockReturnValue(of([R1, R2]));
    cierraCon(true);
  });

  describe('contenido', () => {
    it('con las afectadas ya conocidas (recién creado el bloqueo) no pide nada al servidor', async () => {
      await crear({ ...DATOS, afectadas: [R1, R2] });

      expect(bloqueos.afectadas).not.toHaveBeenCalled();
      expect(filas()).toHaveLength(2);
    });

    it('si no las traen, las pide por club, pista y bloqueo y muestra un spinner mientras tanto', async () => {
      bloqueos.afectadas.mockReturnValue(NEVER);

      await crear();

      expect(bloqueos.afectadas).toHaveBeenCalledWith(5, 10, 3);
      expect(q('mat-spinner')?.getAttribute('aria-label')).toBe('Cargando las reservas afectadas');
    });

    it('cada fila lleva día y hora de Madrid, pista, organizador con email, jugadores y estado', async () => {
      await crear();

      expect(texto(q('h2'))).toBe('Reservas afectadas');
      expect(q('table')?.getAttribute('aria-label')).toBe('Reservas afectadas por el bloqueo');
      const primera = filas()[0];
      expect(texto(primera.querySelector('.fecha'))).toContain('miércoles, 15 de julio de 2026');
      expect(texto(primera.querySelector('.hora'))).toBe('18:00 – 19:30');
      expect(texto(primera)).toContain('Pista 1');
      expect(texto(primera.querySelector('.organizador .nombre'))).toBe('Ana García López');
      expect(texto(primera.querySelector('.organizador .email'))).toBe('ana@x.es');
      expect(texto(primera.querySelector('.jugadores'))).toBe('3 de 4');
      expect(texto(primera.querySelector('mat-chip'))).toBe('Confirmada');
      expect(texto(filas()[1].querySelector('.jugadores'))).toBe('1 de 4');
      expect(texto(filas()[1].querySelector('.hora'))).toBe('09:00 – 10:30');
    });

    it('sin afectadas lo dice y no deja cancelar', async () => {
      bloqueos.afectadas.mockReturnValue(of([]));

      await crear();

      expect(texto(el())).toContain('No hay reservas afectadas por este bloqueo');
      expect(q('table')).toBeNull();
      expect(cancelar().disabled).toBe(true);
    });

    it('si falla la carga muestra el motivo y permite reintentar', async () => {
      bloqueos.afectadas.mockReturnValueOnce(throwError(() => http(500)));
      await crear();

      expect(q('[role="alert"]')?.textContent).toContain('No se pudieron cargar');
      expect(cancelar().disabled).toBe(true);
      q<HTMLButtonElement>('button[data-accion="reintentar"]')!.click();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(filas()).toHaveLength(2);
    });
  });

  describe('cancelar afectadas', () => {
    it('pide confirmación diciendo cuántas reservas se cancelan, que se anulan los cargos y se avisa', async () => {
      cierraCon(false);
      await crear();

      await pulsarCancelar();

      const [componente, config] = dialogo.open.mock.calls[0];
      expect(componente).toBe(ConfirmDialog);
      expect(config.data.mensaje).toContain('2 reservas');
      expect(config.data.mensaje).toContain('cargos');
      expect(config.data.mensaje).toContain('jugadores');
      expect(config.data.textoConfirmar).toBe('Cancelar 2 reservas');
      expect(bloqueos.cancelarAfectadas).not.toHaveBeenCalled();
    });

    it('en singular dice "1 reserva"', async () => {
      cierraCon(false);
      await crear({ ...DATOS, afectadas: [R1] });

      await pulsarCancelar();

      expect(dialogo.open.mock.calls[0][1].data.mensaje).toContain('1 reserva ');
      expect(dialogo.open.mock.calls[0][1].data.textoConfirmar).toBe('Cancelar 1 reserva');
    });

    it('al confirmar cancela, muestra cuántas se cancelaron y vacía la lista', async () => {
      bloqueos.cancelarAfectadas.mockReturnValue(of({ canceladas: 2, ids: [1, 2] }));
      await crear();

      await pulsarCancelar();

      expect(bloqueos.cancelarAfectadas).toHaveBeenCalledWith(5, 10, 3);
      expect(texto(q('[role="status"]'))).toContain('Se cancelaron 2 reservas');
      expect(filas()).toHaveLength(0);
      expect(cancelar().disabled).toBe(true);
    });

    it('si solo se cancela una de dos (otra ya no estaba) deja la otra en la lista', async () => {
      bloqueos.cancelarAfectadas.mockReturnValue(of({ canceladas: 1, ids: [1] }));
      await crear();

      await pulsarCancelar();

      expect(texto(q('[role="status"]'))).toContain('Se canceló 1 reserva');
      expect(filas()).toHaveLength(1);
      expect(texto(filas()[0])).toContain('luis@x.es');
    });

    it('un resultado de 0 (repetir la acción) dice que no había reservas afectadas', async () => {
      bloqueos.cancelarAfectadas.mockReturnValue(of({ canceladas: 0, ids: [] }));
      await crear();

      await pulsarCancelar();

      expect(texto(q('[role="status"]'))).toBe('No había reservas afectadas.');
      expect(filas()).toHaveLength(0);
    });

    it('si se rechaza la confirmación no se cancela nada', async () => {
      cierraCon(false);
      await crear();

      await pulsarCancelar();

      expect(bloqueos.cancelarAfectadas).not.toHaveBeenCalled();
      expect(filas()).toHaveLength(2);
    });

    it('un 409 (bloqueo ya no activo) se muestra dentro del diálogo y la lista queda como estaba', async () => {
      bloqueos.cancelarAfectadas.mockReturnValue(
        throwError(() => http(409, { error: 'El bloqueo ya no está activo' })),
      );
      await crear();

      await pulsarCancelar();

      expect(q('[role="alert"]')?.textContent).toContain('El bloqueo ya no está activo');
      expect(filas()).toHaveLength(2);
      expect(dialogRef.close).not.toHaveBeenCalled();
    });

    it('mientras cancela bloquea el botón y no lo lanza dos veces', async () => {
      const respuesta = new Subject<CancelacionAfectadas>();
      bloqueos.cancelarAfectadas.mockReturnValue(respuesta);
      await crear();

      await pulsarCancelar();
      expect(cancelar().disabled).toBe(true);
      cancelar().click();

      expect(bloqueos.cancelarAfectadas).toHaveBeenCalledTimes(1);
    });
  });

  describe('cerrar', () => {
    it('"Cerrar" sin haber cancelado nada cierra sin resultado', async () => {
      await crear();

      q<HTMLButtonElement>('button[data-accion="cerrar"]')!.click();

      expect(dialogRef.close.mock.calls[0][0]).toBeUndefined();
    });

    it('tras cancelar, cierra devolviendo cuántas reservas se cancelaron', async () => {
      bloqueos.cancelarAfectadas.mockReturnValue(of({ canceladas: 2, ids: [1, 2] }));
      await crear();
      await pulsarCancelar();

      q<HTMLButtonElement>('button[data-accion="cerrar"]')!.click();

      expect(dialogRef.close).toHaveBeenCalledWith(2);
    });
  });
});
