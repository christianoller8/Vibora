import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Subject, of, throwError } from 'rxjs';
import { IncidenciaDetalle, MiembroPersonal } from '../../../shared/models/incidencia.model';
import { escribir } from '../../../testing/test-utils';
import { PersonalService } from '../../club/personal.service';
import { IncidenciaService } from '../incidencia.service';
import { AsignarDialog, AsignarDialogData } from './asignar-dialog';

const PERSONAL: MiembroPersonal[] = [
  { usuarioId: 20, nombre: 'Pepe', apellidos: 'Gil', email: 'pepe@club.es', rol: 'MANTENIMIENTO' },
  { usuarioId: 21, nombre: 'Lola', apellidos: 'Ruiz', email: 'lola@club.es', rol: 'MONITOR' },
  { usuarioId: 22, nombre: null, apellidos: 'Sanz', email: 'sanz@club.es', rol: 'MANTENIMIENTO' },
  { usuarioId: 9, nombre: 'Marta', apellidos: 'Ruiz', email: 'admin@club.es', rol: 'ADMIN_CLUB' },
];

const DATOS: AsignarDialogData = {
  incidenciaId: 7,
  clubId: 5,
  prioridad: 'MEDIA',
  asignadaA: null,
};
const RESULTADO = { id: 7, estado: 'ASIGNADA' } as IncidenciaDetalle;

describe('AsignarDialog', () => {
  let fixture: ComponentFixture<AsignarDialog>;
  const personal = { listar: vi.fn() };
  const incidencias = { asignar: vi.fn() };
  const dialogRef = { close: vi.fn() };

  async function crear(data: AsignarDialogData = DATOS): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [AsignarDialog],
      providers: [
        { provide: PersonalService, useValue: personal },
        { provide: IncidenciaService, useValue: incidencias },
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AsignarDialog);
    await fixture.whenStable();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const persona = (): HTMLSelectElement =>
    el().querySelector<HTMLSelectElement>('select[name="usuarioId"]')!;
  const prioridad = (): HTMLSelectElement =>
    el().querySelector<HTMLSelectElement>('select[name="prioridad"]')!;
  const enviar = (): HTMLButtonElement =>
    el().querySelector<HTMLButtonElement>('button[type="submit"]')!;
  const alerta = (): string | undefined =>
    el().querySelector('[role="alert"]')?.textContent?.trim();

  /** Elige a la persona por el valor de su `<option>` (su id de usuario). */
  async function elegirPersona(usuarioId: number): Promise<void> {
    escribir(persona(), String(usuarioId));
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(() => {
    vi.resetAllMocks();
    personal.listar.mockReturnValue(of(PERSONAL));
  });

  it('pide el personal del club de la incidencia y ofrece SOLO a mantenimiento', async () => {
    await crear();

    expect(personal.listar).toHaveBeenCalledWith(5);
    const opciones = Array.from(persona().options)
      .filter((o) => o.value !== '')
      .map((o) => o.textContent!.trim());
    expect(opciones).toHaveLength(2);
    expect(opciones[0]).toContain('Pepe Gil');
    expect(opciones[1]).toContain('Sanz');
  });

  it('la prioridad ofrece las cuatro en español y arranca en la actual', async () => {
    await crear({ ...DATOS, prioridad: 'ALTA' });

    const textos = Array.from(prioridad().options).map((o) => o.textContent!.trim());
    expect(textos).toEqual(['Baja', 'Media', 'Alta', 'Urgente']);
    expect(prioridad().value).toBe('ALTA');
  });

  it('no se puede enviar sin elegir a una persona', async () => {
    await crear();

    expect(enviar().disabled).toBe(true);
  });

  it('asigna con la persona y la prioridad elegidas y cierra con la incidencia actualizada', async () => {
    incidencias.asignar.mockReturnValue(of(RESULTADO));
    await crear();

    await elegirPersona(20);
    escribir(prioridad(), 'ALTA');
    fixture.detectChanges();
    enviar().click();
    await fixture.whenStable();

    expect(incidencias.asignar).toHaveBeenCalledWith(7, { usuarioId: 20, prioridad: 'ALTA' });
    expect(dialogRef.close).toHaveBeenCalledWith({ tipo: 'ok', incidencia: RESULTADO });
  });

  it('si no se toca la prioridad no se envía (solo cambia quien se encarga)', async () => {
    incidencias.asignar.mockReturnValue(of(RESULTADO));
    await crear();

    await elegirPersona(22);
    enviar().click();
    await fixture.whenStable();

    expect(incidencias.asignar).toHaveBeenCalledWith(7, { usuarioId: 22 });
  });

  it('una reasignación precarga a la persona actual si sigue siendo de mantenimiento', async () => {
    await crear({ ...DATOS, asignadaA: { id: 20, nombre: 'Pepe Gil' } });

    expect(persona().selectedOptions[0].textContent).toContain('Pepe Gil');
  });

  it('mientras envía bloquea el botón y no deja enviar dos veces', async () => {
    const respuesta = new Subject<IncidenciaDetalle>();
    incidencias.asignar.mockReturnValue(respuesta);
    await crear();
    await elegirPersona(20);

    enviar().click();
    fixture.detectChanges();
    expect(enviar().disabled).toBe(true);
    enviar().click();

    expect(incidencias.asignar).toHaveBeenCalledTimes(1);
  });

  it('un error del backend se muestra dentro del diálogo, que no se cierra', async () => {
    incidencias.asignar.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { error: 'No se puede asignar una incidencia en estado Cerrada' },
          }),
      ),
    );
    await crear();
    await elegirPersona(20);

    enviar().click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(alerta()).toBe('No se puede asignar una incidencia en estado Cerrada');
    expect(dialogRef.close).not.toHaveBeenCalled();
    expect(enviar().disabled).toBe(false);
  });

  it('un 409 de bloqueo optimista cierra el diálogo avisando del conflicto', async () => {
    const mensaje = 'La incidencia fue modificada por otra persona, recarga';
    incidencias.asignar.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { error: mensaje } })),
    );
    await crear();
    await elegirPersona(20);

    enviar().click();
    await fixture.whenStable();

    expect(dialogRef.close).toHaveBeenCalledWith({ tipo: 'conflicto', mensaje });
  });

  it('si no hay personal de mantenimiento lo dice y no deja enviar', async () => {
    personal.listar.mockReturnValue(of([PERSONAL[1], PERSONAL[3]]));
    await crear();

    expect(el().textContent).toContain('No hay personal de mantenimiento en este club');
    expect(enviar().disabled).toBe(true);
  });

  it('si falla la carga del personal lo muestra', async () => {
    personal.listar.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    await crear();

    expect(alerta()).toContain('No se pudo cargar el personal');
  });

  it('"Cancelar" cierra sin resultado', async () => {
    await crear();

    el().querySelector<HTMLButtonElement>('button[data-accion="cancelar"]')!.click();

    expect(dialogRef.close).toHaveBeenCalledTimes(1);
    expect(dialogRef.close.mock.calls[0][0]).toBeUndefined();
  });
});
