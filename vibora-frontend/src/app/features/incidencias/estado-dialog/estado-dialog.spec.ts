import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Subject, of, throwError } from 'rxjs';
import { IncidenciaDetalle } from '../../../shared/models/incidencia.model';
import { escribir } from '../../../testing/test-utils';
import { AccionIncidencia } from '../incidencia-estados';
import { IncidenciaService } from '../incidencia.service';
import { EstadoDialog, EstadoDialogData } from './estado-dialog';

const RESULTADO = { id: 7, estado: 'RESUELTA' } as IncidenciaDetalle;
type AccionDeEstado = Exclude<AccionIncidencia, 'ASIGNAR'>;

describe('EstadoDialog', () => {
  let fixture: ComponentFixture<EstadoDialog>;
  const incidencias = { cambiarEstado: vi.fn() };
  const dialogRef = { close: vi.fn() };

  async function crear(accion: AccionDeEstado): Promise<void> {
    const data: EstadoDialogData = { incidenciaId: 7, accion };
    await TestBed.configureTestingModule({
      imports: [EstadoDialog],
      providers: [
        { provide: IncidenciaService, useValue: incidencias },
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(EstadoDialog);
    await fixture.whenStable();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const comentario = (): HTMLTextAreaElement =>
    el().querySelector<HTMLTextAreaElement>('textarea[name="comentario"]')!;
  const enviar = (): HTMLButtonElement =>
    el().querySelector<HTMLButtonElement>('button[type="submit"]')!;
  const alerta = (): string | undefined =>
    el().querySelector('[role="alert"]')?.textContent?.trim();

  beforeEach(() => {
    vi.resetAllMocks();
    incidencias.cambiarEstado.mockReturnValue(of(RESULTADO));
  });

  it.each<[AccionDeEstado, string, string, string]>([
    ['INICIAR', 'EN_CURSO', 'Marcar en curso', 'Marcar en curso'],
    ['RESOLVER', 'RESUELTA', 'Resolver incidencia', 'Resolver'],
    ['CERRAR', 'CERRADA', 'Cerrar incidencia', 'Cerrar'],
    ['REABRIR', 'EN_CURSO', 'Reabrir incidencia', 'Reabrir'],
    ['RECHAZAR', 'RECHAZADA', 'Rechazar incidencia', 'Rechazar'],
  ])(
    '%s envía el estado %s con título "%s" y botón "%s"',
    async (accion, estado, titulo, boton) => {
      await crear(accion);
      if (accion === 'RECHAZAR') {
        escribir(comentario(), 'Duplicada');
        fixture.detectChanges();
      }

      expect(el().querySelector('h2')?.textContent).toContain(titulo);
      expect(enviar().textContent).toContain(boton);
      enviar().click();
      await fixture.whenStable();

      expect(incidencias.cambiarEstado).toHaveBeenCalledWith(
        7,
        accion === 'RECHAZAR' ? { estado, comentario: 'Duplicada' } : { estado },
      );
      expect(dialogRef.close).toHaveBeenCalledWith({ tipo: 'ok', incidencia: RESULTADO });
    },
  );

  it('el comentario es opcional salvo al rechazar: sin él se puede enviar y no viaja', async () => {
    await crear('RESOLVER');

    expect(enviar().disabled).toBe(false);
    enviar().click();
    await fixture.whenStable();

    expect(incidencias.cambiarEstado).toHaveBeenCalledWith(7, { estado: 'RESUELTA' });
  });

  it('un comentario opcional se envía recortado', async () => {
    await crear('RESOLVER');

    escribir(comentario(), '  Cambiado el foco  ');
    fixture.detectChanges();
    enviar().click();
    await fixture.whenStable();

    expect(incidencias.cambiarEstado).toHaveBeenCalledWith(7, {
      estado: 'RESUELTA',
      comentario: 'Cambiado el foco',
    });
  });

  it('rechazar EXIGE un motivo: vacío o solo espacios no se puede enviar', async () => {
    await crear('RECHAZAR');
    expect(enviar().disabled).toBe(true);
    expect(el().querySelector('mat-label')?.textContent).toContain('Motivo del rechazo');

    escribir(comentario(), '     ');
    fixture.detectChanges();

    expect(enviar().disabled).toBe(true);
    expect(incidencias.cambiarEstado).not.toHaveBeenCalled();
  });

  it('el comentario admite como máximo 2000 caracteres', async () => {
    await crear('RESOLVER');

    expect(comentario().getAttribute('maxlength')).toBe('2000');
    escribir(comentario(), 'a'.repeat(2001));
    fixture.detectChanges();

    expect(enviar().disabled).toBe(true);
  });

  it('mientras envía no se puede enviar de nuevo', async () => {
    incidencias.cambiarEstado.mockReturnValue(new Subject<IncidenciaDetalle>());
    await crear('CERRAR');

    enviar().click();
    fixture.detectChanges();
    expect(enviar().disabled).toBe(true);
    enviar().click();

    expect(incidencias.cambiarEstado).toHaveBeenCalledTimes(1);
  });

  it('un 409 de regla de negocio se muestra tal cual dentro del diálogo, que sigue abierto', async () => {
    incidencias.cambiarEstado.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { error: 'No se puede pasar de Abierta a Cerrada' },
          }),
      ),
    );
    await crear('CERRAR');

    enviar().click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(alerta()).toBe('No se puede pasar de Abierta a Cerrada');
    expect(dialogRef.close).not.toHaveBeenCalled();
    expect(enviar().disabled).toBe(false);
  });

  it('un 403 sin mensaje dice que no hay permiso', async () => {
    incidencias.cambiarEstado.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 403 })),
    );
    await crear('RESOLVER');

    enviar().click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(alerta()).toBe('No tienes permiso para hacer esto.');
  });

  it('un 409 de bloqueo optimista cierra el diálogo avisando del conflicto', async () => {
    const mensaje = 'La incidencia fue modificada por otra persona, recarga';
    incidencias.cambiarEstado.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { error: mensaje } })),
    );
    await crear('RESOLVER');

    enviar().click();
    await fixture.whenStable();

    expect(dialogRef.close).toHaveBeenCalledWith({ tipo: 'conflicto', mensaje });
  });

  it('"Cancelar" cierra sin resultado ni petición', async () => {
    await crear('RESOLVER');

    el().querySelector<HTMLButtonElement>('button[data-accion="cancelar"]')!.click();

    expect(incidencias.cambiarEstado).not.toHaveBeenCalled();
    expect(dialogRef.close.mock.calls[0][0]).toBeUndefined();
  });
});
