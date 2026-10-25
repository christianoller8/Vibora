import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { NEVER, of, throwError } from 'rxjs';
import { ConfirmDialog } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { MiembroPersonal } from '../../../shared/models/incidencia.model';
import { PersonalService } from '../personal.service';
import { PersonalClub } from './personal-club';
import { PersonalDialog } from './personal-dialog';

const ADMIN: MiembroPersonal = {
  usuarioId: 9,
  nombre: 'Marta',
  apellidos: 'Ruiz',
  email: 'admin@club.es',
  rol: 'ADMIN_CLUB',
};
const PEPE: MiembroPersonal = {
  usuarioId: 20,
  nombre: 'Pepe',
  apellidos: 'Gil',
  email: 'pepe@club.es',
  rol: 'MANTENIMIENTO',
};
const LOLA: MiembroPersonal = {
  usuarioId: 21,
  nombre: 'Lola',
  apellidos: 'Sanz',
  email: 'lola@club.es',
  rol: 'MONITOR',
};
const SIN_NOMBRE: MiembroPersonal = {
  usuarioId: 22,
  nombre: null,
  apellidos: 'Ortega',
  email: 'ortega@club.es',
  rol: 'MONITOR',
};

const http = (status: number, error?: unknown): HttpErrorResponse =>
  new HttpErrorResponse({ status, error });

describe('PersonalClub', () => {
  let fixture: ComponentFixture<PersonalClub>;
  const personal = { listar: vi.fn(), quitar: vi.fn() };
  const dialogo = { open: vi.fn() };
  const snack = { open: vi.fn() };

  async function crear(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [PersonalClub],
      providers: [
        { provide: PersonalService, useValue: personal },
        { provide: MatDialog, useValue: dialogo },
        { provide: MatSnackBar, useValue: snack },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(PersonalClub);
    fixture.componentRef.setInput('clubId', 5);
    await fixture.whenStable();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const q = <T extends Element>(s: string): T | null => el().querySelector<T>(s);
  const filas = (): HTMLElement[] => Array.from(el().querySelectorAll<HTMLElement>('tr.miembro'));
  const texto = (e: Element | null): string => e?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  const cierraCon = (resultado: unknown): void => {
    dialogo.open.mockReturnValue({ afterClosed: () => of(resultado) });
  };
  const botonQuitar = (fila: HTMLElement): HTMLButtonElement | null =>
    fila.querySelector<HTMLButtonElement>('button[data-accion="quitar"]');

  beforeEach(() => {
    vi.resetAllMocks();
    personal.listar.mockReturnValue(of([ADMIN, PEPE, LOLA, SIN_NOMBRE]));
    cierraCon(undefined);
  });

  describe('listado', () => {
    it('pide el personal del club y muestra un spinner mientras tanto', async () => {
      personal.listar.mockReturnValue(NEVER);

      await crear();

      expect(personal.listar).toHaveBeenCalledWith(5);
      expect(q('mat-spinner')?.getAttribute('aria-label')).toBe('Cargando el personal');
      expect(q('table')).toBeNull();
    });

    it('una fila por persona con nombre, email y rol en español; sin nombre usa solo apellidos', async () => {
      await crear();

      expect(filas()).toHaveLength(4);
      expect(texto(filas()[0])).toContain('Marta Ruiz');
      expect(texto(filas()[0])).toContain('admin@club.es');
      expect(texto(filas()[0].querySelector('mat-chip'))).toBe('Admin de club');
      expect(texto(filas()[1].querySelector('mat-chip'))).toBe('Mantenimiento');
      expect(texto(filas()[2].querySelector('mat-chip'))).toBe('Monitor');
      expect(texto(filas()[3].querySelector('.nombre'))).toBe('Ortega');
    });

    it('la tabla tiene nombre accesible y cabeceras', async () => {
      await crear();

      expect(q('table')?.getAttribute('aria-label')).toBe('Personal del club');
      const cabeceras = Array.from(el().querySelectorAll('th')).map((c) => texto(c));
      expect(cabeceras.slice(0, 3)).toEqual(['Nombre', 'Email', 'Rol']);
    });

    it('el administrador se muestra pero sin botón de quitar; el resto sí, con etiqueta accesible', async () => {
      await crear();

      expect(botonQuitar(filas()[0])).toBeNull();
      expect(botonQuitar(filas()[1])?.getAttribute('aria-label')).toBe(
        'Quitar a Pepe Gil del personal',
      );
      expect(botonQuitar(filas()[2])).not.toBeNull();
      expect(botonQuitar(filas()[3])?.getAttribute('aria-label')).toBe(
        'Quitar a Ortega del personal',
      );
    });

    it('sin personal (solo la lista vacía) lo dice', async () => {
      personal.listar.mockReturnValue(of([]));

      await crear();

      expect(texto(el())).toContain('No hay personal');
      expect(q('table')).toBeNull();
    });

    it('si la carga falla muestra el motivo y permite reintentar', async () => {
      personal.listar.mockReturnValueOnce(throwError(() => http(500)));
      await crear();

      expect(q('[role="alert"]')?.textContent).toContain('No se pudo cargar el personal');
      personal.listar.mockReturnValue(of([ADMIN, PEPE]));
      q<HTMLButtonElement>('button[data-accion="reintentar"]')!.click();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(filas()).toHaveLength(2);
      expect(q('[role="alert"]')).toBeNull();
    });
  });

  describe('añadir personal', () => {
    it('el botón abre el diálogo de alta con el club', async () => {
      await crear();

      q<HTMLButtonElement>('button[data-accion="anadir"]')!.click();

      const [componente, config] = dialogo.open.mock.calls[0];
      expect(componente).toBe(PersonalDialog);
      expect(config.data).toEqual({ clubId: 5 });
    });

    it('al añadir avisa de que la persona debe volver a iniciar sesión y recarga la lista', async () => {
      await crear();
      cierraCon({ ...PEPE, usuarioId: 30, nombre: 'Nuria', apellidos: 'Pons', rol: 'MONITOR' });
      personal.listar.mockClear();

      q<HTMLButtonElement>('button[data-accion="anadir"]')!.click();
      await fixture.whenStable();

      const [mensaje] = snack.open.mock.calls[0];
      expect(mensaje).toContain('Nuria Pons');
      expect(mensaje).toContain('Monitor');
      expect(mensaje).toContain('volver a iniciar sesión');
      expect(personal.listar).toHaveBeenCalledTimes(1);
    });

    it('si se cierra el diálogo sin añadir a nadie no avisa ni recarga', async () => {
      await crear();
      personal.listar.mockClear();

      q<HTMLButtonElement>('button[data-accion="anadir"]')!.click();
      await fixture.whenStable();

      expect(snack.open).not.toHaveBeenCalled();
      expect(personal.listar).not.toHaveBeenCalled();
    });
  });

  describe('quitar personal', () => {
    async function pulsarQuitar(indice: number): Promise<void> {
      botonQuitar(filas()[indice])!.click();
      await fixture.whenStable();
      fixture.detectChanges();
    }

    it('pide confirmación diciendo que las incidencias activas de esa persona quedarán sin asignar', async () => {
      await crear();

      await pulsarQuitar(1);

      const [componente, config] = dialogo.open.mock.calls[0];
      expect(componente).toBe(ConfirmDialog);
      expect(config.data.titulo).toContain('Quitar');
      expect(config.data.mensaje).toContain('incidencias');
      expect(config.data.mensaje).toContain('sin asignar');
      expect(config.data.lineas).toEqual(
        expect.arrayContaining([
          { etiqueta: 'Persona', valor: 'Pepe Gil' },
          { etiqueta: 'Rol', valor: 'Mantenimiento' },
        ]),
      );
      expect(personal.quitar).not.toHaveBeenCalled();
    });

    it('al confirmar quita, avisa y recarga la lista', async () => {
      personal.quitar.mockReturnValue(of(undefined));
      await crear();
      cierraCon(true);
      personal.listar.mockClear();
      personal.listar.mockReturnValue(of([ADMIN, LOLA]));

      await pulsarQuitar(1);

      expect(personal.quitar).toHaveBeenCalledWith(5, 20);
      expect(snack.open.mock.calls[0][0]).toContain('Pepe Gil');
      expect(personal.listar).toHaveBeenCalledTimes(1);
      expect(filas()).toHaveLength(2);
    });

    it('si se cancela la confirmación no se quita a nadie', async () => {
      await crear();
      cierraCon(false);

      await pulsarQuitar(1);

      expect(personal.quitar).not.toHaveBeenCalled();
    });

    it('un error (409 de administrador) se avisa con el mensaje del backend y se recarga', async () => {
      personal.quitar.mockReturnValue(
        throwError(() => http(409, { error: 'No se puede quitar a un administrador del club' })),
      );
      await crear();
      cierraCon(true);
      personal.listar.mockClear();

      await pulsarQuitar(1);

      expect(snack.open.mock.calls[0][0]).toBe('No se puede quitar a un administrador del club');
      expect(personal.listar).toHaveBeenCalledTimes(1);
    });

    it('un 404 (ya no es personal) también recarga para mostrar el estado real', async () => {
      personal.quitar.mockReturnValue(
        throwError(() => http(404, { error: 'Personal no encontrado' })),
      );
      await crear();
      cierraCon(true);
      personal.listar.mockClear();

      await pulsarQuitar(2);

      expect(snack.open.mock.calls[0][0]).toBe('Personal no encontrado');
      expect(personal.listar).toHaveBeenCalledTimes(1);
    });

    it('un fallo de red avisa sin recargar', async () => {
      personal.quitar.mockReturnValue(throwError(() => http(0)));
      await crear();
      cierraCon(true);
      personal.listar.mockClear();

      await pulsarQuitar(1);

      expect(snack.open.mock.calls[0][0]).toContain('No se pudo conectar');
      expect(personal.listar).not.toHaveBeenCalled();
    });

    it('con una baja en vuelo los botones de quitar se deshabilitan', async () => {
      personal.quitar.mockReturnValue(NEVER);
      await crear();
      cierraCon(true);

      await pulsarQuitar(1);

      expect(botonQuitar(filas()[2])!.disabled).toBe(true);
    });
  });
});
