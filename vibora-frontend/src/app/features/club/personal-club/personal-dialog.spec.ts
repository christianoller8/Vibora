import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Subject, of, throwError } from 'rxjs';
import { MiembroPersonal } from '../../../shared/models/incidencia.model';
import { escribir } from '../../../testing/test-utils';
import { PersonalService } from '../personal.service';
import { PersonalDialog, PersonalDialogData } from './personal-dialog';

const NUEVO: MiembroPersonal = {
  usuarioId: 20,
  nombre: 'Pepe',
  apellidos: 'Gil',
  email: 'pepe@club.es',
  rol: 'MANTENIMIENTO',
};

const http = (status: number, error?: unknown): HttpErrorResponse =>
  new HttpErrorResponse({ status, error });

describe('PersonalDialog', () => {
  let fixture: ComponentFixture<PersonalDialog>;
  const personal = { agregar: vi.fn() };
  const dialogRef = { close: vi.fn() };
  const DATOS: PersonalDialogData = { clubId: 5 };

  async function crear(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [PersonalDialog],
      providers: [
        { provide: PersonalService, useValue: personal },
        { provide: MAT_DIALOG_DATA, useValue: DATOS },
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(PersonalDialog);
    await fixture.whenStable();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const email = (): HTMLInputElement =>
    el().querySelector<HTMLInputElement>('input[name="email"]')!;
  const rol = (): HTMLSelectElement => el().querySelector<HTMLSelectElement>('select[name="rol"]')!;
  const enviar = (): HTMLButtonElement =>
    el().querySelector<HTMLButtonElement>('button[type="submit"]')!;
  const alerta = (): string | undefined =>
    el().querySelector('[role="alert"]')?.textContent?.trim();

  async function rellenar(valorEmail: string, valorRol?: string): Promise<void> {
    escribir(email(), valorEmail);
    if (valorRol !== undefined) {
      escribir(rol(), valorRol);
    }
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(() => {
    vi.resetAllMocks();
    personal.agregar.mockReturnValue(of(NUEVO));
  });

  it('tiene título, campo de email y un selector con solo Monitor y Mantenimiento', async () => {
    await crear();

    expect(el().querySelector('h2')?.textContent).toContain('Añadir personal');
    expect(email().type).toBe('email');
    expect(email().getAttribute('autocomplete')).toBe('off');
    const opciones = Array.from(rol().options).map((o) => [o.value, o.textContent!.trim()]);
    expect(opciones).toEqual([
      ['MONITOR', 'Monitor'],
      ['MANTENIMIENTO', 'Mantenimiento'],
    ]);
  });

  it('no se puede enviar con el email vacío ni con un email mal formado', async () => {
    await crear();
    expect(enviar().disabled).toBe(true);

    await rellenar('esto-no-es-un-email');

    expect(enviar().disabled).toBe(true);
    expect(el().querySelector('mat-error')?.textContent).toContain('email válido');
  });

  it('rechaza un email de más de 255 caracteres (límite del backend)', async () => {
    await crear();

    await rellenar(`${'a'.repeat(250)}@club.es`);

    expect(enviar().disabled).toBe(true);
  });

  it('añade con el email recortado y el rol elegido, y cierra con la persona añadida', async () => {
    await crear();

    await rellenar('  pepe@club.es ', 'MANTENIMIENTO');
    enviar().click();
    await fixture.whenStable();

    expect(personal.agregar).toHaveBeenCalledWith(5, {
      email: 'pepe@club.es',
      rol: 'MANTENIMIENTO',
    });
    expect(dialogRef.close).toHaveBeenCalledWith(NUEVO);
  });

  it('el rol por defecto es Monitor', async () => {
    await crear();

    await rellenar('lola@club.es');
    enviar().click();
    await fixture.whenStable();

    expect(personal.agregar).toHaveBeenCalledWith(5, { email: 'lola@club.es', rol: 'MONITOR' });
  });

  it('un 404 se muestra dentro del diálogo con el mensaje del backend, sin cerrarlo', async () => {
    personal.agregar.mockReturnValue(
      throwError(() => http(404, { error: 'No existe ningún usuario con ese email' })),
    );
    await crear();

    await rellenar('nadie@club.es');
    enviar().click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(alerta()).toBe('No existe ningún usuario con ese email');
    expect(dialogRef.close).not.toHaveBeenCalled();
    expect(enviar().disabled).toBe(false);
  });

  it('un 404 sin mensaje usa un texto propio que dice que no existe', async () => {
    personal.agregar.mockReturnValue(throwError(() => http(404)));
    await crear();

    await rellenar('nadie@club.es');
    enviar().click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(alerta()).toContain('No existe');
  });

  it('un 409 dice que esa persona ya pertenece a un club', async () => {
    personal.agregar.mockReturnValue(
      throwError(() => http(409, { error: 'Ese usuario ya pertenece a un club' })),
    );
    await crear();

    await rellenar('otro@club.es');
    enviar().click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(alerta()).toBe('Ese usuario ya pertenece a un club');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('un 409 sin mensaje usa un texto propio', async () => {
    personal.agregar.mockReturnValue(throwError(() => http(409)));
    await crear();

    await rellenar('otro@club.es');
    enviar().click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(alerta()).toContain('ya pertenece a un club');
  });

  it('un fallo de red muestra el aviso genérico de conexión', async () => {
    personal.agregar.mockReturnValue(throwError(() => http(0)));
    await crear();

    await rellenar('pepe@club.es');
    enviar().click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(alerta()).toContain('No se pudo conectar');
  });

  it('al corregir el email tras un error, el aviso anterior desaparece al reenviar', async () => {
    personal.agregar
      .mockReturnValueOnce(
        throwError(() => http(404, { error: 'No existe ningún usuario con ese email' })),
      )
      .mockReturnValueOnce(of(NUEVO));
    await crear();
    await rellenar('nadie@club.es');
    enviar().click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(alerta()).toBeDefined();

    await rellenar('pepe@club.es');
    enviar().click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(alerta()).toBeUndefined();
    expect(dialogRef.close).toHaveBeenCalledWith(NUEVO);
  });

  it('mientras envía bloquea el botón y no deja enviar dos veces', async () => {
    personal.agregar.mockReturnValue(new Subject<MiembroPersonal>());
    await crear();
    await rellenar('pepe@club.es');

    enviar().click();
    fixture.detectChanges();
    expect(enviar().disabled).toBe(true);
    expect(enviar().textContent).toContain('Añadiendo');
    enviar().click();

    expect(personal.agregar).toHaveBeenCalledTimes(1);
  });

  it('"Cancelar" cierra sin resultado', async () => {
    await crear();

    el().querySelector<HTMLButtonElement>('button[data-accion="cancelar"]')!.click();

    expect(dialogRef.close).toHaveBeenCalledTimes(1);
    expect(dialogRef.close.mock.calls[0][0]).toBeUndefined();
  });
});
