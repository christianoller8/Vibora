import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Subject, of, throwError } from 'rxjs';
import { BloqueoCreado } from '../../../shared/models/bloqueo.model';
import { EstadoIncidencia, IncidenciaResumen } from '../../../shared/models/incidencia.model';
import { Pista } from '../../../shared/models/pista.model';
import { escribir } from '../../../testing/test-utils';
import { IncidenciaService } from '../../incidencias/incidencia.service';
import { BloqueoService } from '../bloqueo.service';
import { BloqueoDialog, BloqueoDialogData } from './bloqueo-dialog';

const pista = (id: number, nombre: string): Pista => ({
  id,
  nombre,
  tipo: 'INTERIOR',
  activa: true,
  clubId: 5,
});

function incidencia(
  id: number,
  pistaId: number | null,
  estado: EstadoIncidencia,
  titulo = `Incidencia ${id}`,
): IncidenciaResumen {
  return {
    id,
    clubId: 5,
    pistaId,
    pistaNombre: pistaId === null ? null : `Pista ${pistaId}`,
    titulo,
    categoria: 'ILUMINACION',
    prioridad: 'MEDIA',
    estado,
    reportadaPor: { id: 1, nombre: 'Ana' },
    asignadaA: null,
    creadaEn: '2026-07-15T16:00:00Z',
    actualizadaEn: '2026-07-15T16:00:00Z',
  };
}

const INCIDENCIAS: IncidenciaResumen[] = [
  incidencia(1, 10, 'ABIERTA', 'Foco fundido'),
  incidencia(2, 11, 'ASIGNADA', 'Red rota'),
  incidencia(3, null, 'EN_CURSO', 'Goteras generales'),
  incidencia(4, 10, 'CERRADA', 'Ya cerrada'),
  incidencia(5, 10, 'RECHAZADA', 'Ya rechazada'),
  incidencia(6, 10, 'RESUELTA', 'Resuelta pendiente de cierre'),
];

const CREADO: BloqueoCreado = {
  bloqueo: {
    id: 3,
    pistaId: 10,
    pistaNombre: 'Pista 1',
    incidenciaId: null,
    motivo: 'Foco',
    desde: '2026-07-15T16:00:00Z',
    hasta: null,
    creadoPor: { id: 9, nombre: 'Marta Ruiz' },
    creadoEn: '2026-07-15T16:00:00Z',
    levantadoEn: null,
    levantadoPor: null,
    activo: true,
  },
  afectadas: [],
};

const http = (status: number, error?: unknown): HttpErrorResponse =>
  new HttpErrorResponse({ status, error });

describe('BloqueoDialog', () => {
  let fixture: ComponentFixture<BloqueoDialog>;
  const bloqueos = { crear: vi.fn() };
  const incidencias = { listar: vi.fn() };
  const dialogRef = { close: vi.fn() };
  const DATOS: BloqueoDialogData = {
    clubId: 5,
    pistas: [pista(10, 'Pista 1'), pista(11, 'Pista 2')],
    pistaId: 10,
  };

  async function crear(datos: BloqueoDialogData = DATOS): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [BloqueoDialog],
      providers: [
        { provide: BloqueoService, useValue: bloqueos },
        { provide: IncidenciaService, useValue: incidencias },
        { provide: MAT_DIALOG_DATA, useValue: datos },
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(BloqueoDialog);
    await fixture.whenStable();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const q = <T extends Element>(s: string): T | null => el().querySelector<T>(s);
  const campo = <T extends HTMLElement>(nombre: string): T => q<T>(`[name="${nombre}"]`) as T;
  const enviar = (): HTMLButtonElement => q<HTMLButtonElement>('button[type="submit"]')!;
  const alerta = (): string | undefined => q('[role="alert"]')?.textContent?.trim();
  const opciones = (select: HTMLSelectElement): string[] =>
    Array.from(select.options).map((o) => o.textContent!.trim());

  async function escribirCampo(nombre: string, valor: string): Promise<void> {
    escribir(campo<HTMLInputElement>(nombre), valor);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function enviarForm(): Promise<void> {
    enviar().click();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(() => {
    vi.resetAllMocks();
    incidencias.listar.mockReturnValue(of(INCIDENCIAS));
    bloqueos.crear.mockReturnValue(of(CREADO));
  });

  describe('formulario', () => {
    it('ofrece las pistas con la elegida preseleccionada y campos de fecha y hora de fin opcionales', async () => {
      await crear({ ...DATOS, pistaId: 11 });

      expect(q('h2')?.textContent).toContain('Nuevo bloqueo');
      expect(opciones(campo<HTMLSelectElement>('pistaId'))).toEqual(['Pista 1', 'Pista 2']);
      expect(campo<HTMLSelectElement>('pistaId').value).toBe('11');
      expect(campo<HTMLInputElement>('desde').type).toBe('datetime-local');
      expect(campo<HTMLInputElement>('hasta').type).toBe('datetime-local');
      expect(campo<HTMLInputElement>('desde').required).toBe(false);
      expect(campo<HTMLInputElement>('hasta').required).toBe(false);
      expect(el().textContent).toContain('Madrid');
      expect(el().textContent).toContain('ahora');
    });

    it('no se puede enviar sin motivo ni con un motivo de solo espacios', async () => {
      await crear();
      expect(enviar().disabled).toBe(true);

      await escribirCampo('motivo', '     ');

      expect(enviar().disabled).toBe(true);
    });

    it('el motivo admite 300 caracteres y no 301', async () => {
      await crear();

      await escribirCampo('motivo', 'a'.repeat(300));
      expect(enviar().disabled).toBe(false);

      await escribirCampo('motivo', 'a'.repeat(301));
      expect(enviar().disabled).toBe(true);
      expect(el().querySelector('mat-error')?.textContent).toContain('300');
    });
  });

  describe('envío', () => {
    it('con solo el motivo envía únicamente el motivo (desde = ahora y sin fin lo decide el servidor)', async () => {
      await crear();

      await escribirCampo('motivo', '  Foco fundido ');
      await enviarForm();

      expect(bloqueos.crear).toHaveBeenCalledWith(5, 10, { motivo: 'Foco fundido' });
      expect(dialogRef.close).toHaveBeenCalledWith(CREADO);
    });

    it('convierte desde y hasta de hora de Madrid a instantes UTC', async () => {
      await crear();

      await escribirCampo('motivo', 'Reparación');
      await escribirCampo('desde', '2099-07-15T18:00');
      await escribirCampo('hasta', '2099-07-16T10:30');
      await enviarForm();

      expect(bloqueos.crear).toHaveBeenCalledWith(5, 10, {
        motivo: 'Reparación',
        desde: '2099-07-15T16:00:00.000Z',
        hasta: '2099-07-16T08:30:00.000Z',
      });
    });

    it('envía la pista elegida en el selector', async () => {
      await crear();

      await escribirCampo('pistaId', '11');
      await escribirCampo('motivo', 'Red rota');
      await enviarForm();

      expect(bloqueos.crear).toHaveBeenCalledWith(5, 11, { motivo: 'Red rota' });
    });

    it('un error 409 o 400 del backend se muestra dentro del diálogo, que no se cierra', async () => {
      bloqueos.crear.mockReturnValue(
        throwError(() => http(409, { error: 'La incidencia ya tiene un bloqueo activo' })),
      );
      await crear();
      await escribirCampo('motivo', 'Foco');

      await enviarForm();

      expect(alerta()).toBe('La incidencia ya tiene un bloqueo activo');
      expect(dialogRef.close).not.toHaveBeenCalled();
      expect(enviar().disabled).toBe(false);
    });

    it('mientras envía bloquea el botón y no deja enviar dos veces', async () => {
      bloqueos.crear.mockReturnValue(new Subject<BloqueoCreado>());
      await crear();
      await escribirCampo('motivo', 'Foco');

      enviar().click();
      fixture.detectChanges();
      expect(enviar().disabled).toBe(true);
      enviar().click();

      expect(bloqueos.crear).toHaveBeenCalledTimes(1);
    });

    it('"Cancelar" cierra sin resultado', async () => {
      await crear();

      q<HTMLButtonElement>('button[data-accion="cancelar"]')!.click();

      expect(dialogRef.close).toHaveBeenCalledTimes(1);
      expect(dialogRef.close.mock.calls[0][0]).toBeUndefined();
    });
  });

  describe('fechas', () => {
    it('hasta igual que desde no es válido y no se envía', async () => {
      await crear();
      await escribirCampo('motivo', 'Foco');

      await escribirCampo('desde', '2099-07-15T18:00');
      await escribirCampo('hasta', '2099-07-15T18:00');

      expect(enviar().disabled).toBe(true);
      expect(el().querySelector('mat-error')?.textContent).toContain('posterior al inicio');
      enviar().click();
      expect(bloqueos.crear).not.toHaveBeenCalled();
    });

    it('hasta anterior a desde no es válido', async () => {
      await crear();
      await escribirCampo('motivo', 'Foco');

      await escribirCampo('desde', '2099-07-15T18:00');
      await escribirCampo('hasta', '2099-07-15T17:59');

      expect(enviar().disabled).toBe(true);
    });

    it('un minuto después de desde sí es válido', async () => {
      await crear();
      await escribirCampo('motivo', 'Foco');

      await escribirCampo('desde', '2099-07-15T18:00');
      await escribirCampo('hasta', '2099-07-15T18:01');

      expect(enviar().disabled).toBe(false);
    });

    it('sin desde, un fin ya pasado no es válido (el inicio por defecto es ahora)', async () => {
      await crear();
      await escribirCampo('motivo', 'Foco');

      await escribirCampo('hasta', '2020-01-01T10:00');

      expect(enviar().disabled).toBe(true);
      expect(el().querySelector('mat-error')?.textContent).toContain('posterior al inicio');
    });

    it('sin desde, un fin futuro es válido', async () => {
      await crear();
      await escribirCampo('motivo', 'Foco');

      await escribirCampo('hasta', '2099-01-01T10:00');

      expect(enviar().disabled).toBe(false);
    });

    it('una hora que no existe en Madrid (salto de marzo) se rechaza con un mensaje', async () => {
      await crear();
      await escribirCampo('motivo', 'Foco');

      await escribirCampo('desde', '2027-03-28T02:30');

      expect(enviar().disabled).toBe(true);
      expect(el().querySelector('mat-error')?.textContent).toContain('no existe');
    });

    it('al vaciar los campos vuelven a ser opcionales', async () => {
      await crear();
      await escribirCampo('motivo', 'Foco');
      await escribirCampo('desde', '2099-07-15T18:00');
      await escribirCampo('hasta', '2099-07-15T17:00');
      expect(enviar().disabled).toBe(true);

      await escribirCampo('hasta', '');
      await escribirCampo('desde', '');
      await enviarForm();

      expect(bloqueos.crear).toHaveBeenCalledWith(5, 10, { motivo: 'Foco' });
    });
  });

  describe('incidencia asociada (opcional)', () => {
    it('pide las incidencias del club y ofrece solo las no terminales coherentes con la pista', async () => {
      await crear();

      expect(incidencias.listar).toHaveBeenCalledWith(5, { limit: 100 });
      expect(opciones(campo<HTMLSelectElement>('incidenciaId'))).toEqual([
        'Sin incidencia',
        'Foco fundido',
        'Goteras generales (sin pista concreta)',
        'Resuelta pendiente de cierre',
      ]);
    });

    it('al cambiar de pista cambia la lista: la de otra pista sale y entra la de la nueva', async () => {
      await crear();

      await escribirCampo('pistaId', '11');

      expect(opciones(campo<HTMLSelectElement>('incidenciaId'))).toEqual([
        'Sin incidencia',
        'Red rota',
        'Goteras generales (sin pista concreta)',
      ]);
    });

    it('si la incidencia elegida deja de ser coherente al cambiar de pista, se deselecciona', async () => {
      await crear();
      await escribirCampo('incidenciaId', '1');
      expect(campo<HTMLSelectElement>('incidenciaId').value).toBe('1');

      await escribirCampo('pistaId', '11');

      expect(campo<HTMLSelectElement>('incidenciaId').value).toBe('');
      await escribirCampo('motivo', 'Foco');
      await enviarForm();
      expect(bloqueos.crear).toHaveBeenCalledWith(5, 11, { motivo: 'Foco' });
    });

    it('si la incidencia sigue siendo coherente al cambiar de pista, se mantiene (sin pista concreta)', async () => {
      await crear();
      await escribirCampo('incidenciaId', '3');

      await escribirCampo('pistaId', '11');

      expect(campo<HTMLSelectElement>('incidenciaId').value).toBe('3');
    });

    it('elegir una incidencia la envía con el bloqueo', async () => {
      await crear();

      await escribirCampo('motivo', 'Foco fundido');
      await escribirCampo('incidenciaId', '1');
      await enviarForm();

      expect(bloqueos.crear).toHaveBeenCalledWith(5, 10, {
        motivo: 'Foco fundido',
        incidenciaId: 1,
      });
    });

    it('si no se pueden cargar las incidencias avisa, pero se puede bloquear igualmente', async () => {
      incidencias.listar.mockReturnValue(throwError(() => http(500)));
      await crear();

      expect(el().textContent).toContain('No se pudieron cargar las incidencias');
      await escribirCampo('motivo', 'Foco');
      await enviarForm();

      expect(bloqueos.crear).toHaveBeenCalledWith(5, 10, { motivo: 'Foco' });
    });

    it('sin incidencias elegibles el selector sigue con "Sin incidencia"', async () => {
      incidencias.listar.mockReturnValue(of([]));
      await crear();

      expect(opciones(campo<HTMLSelectElement>('incidenciaId'))).toEqual(['Sin incidencia']);
    });
  });
});
