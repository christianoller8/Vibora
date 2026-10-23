import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { Pista } from '../../../shared/models/pista.model';
import { escribir } from '../../../testing/test-utils';
import { PistaService } from '../pista.service';
import { PistaDialog } from './pista-dialog';

const EXISTENTE: Pista = { id: 4, nombre: 'Central', tipo: 'CUBIERTA', activa: false, clubId: 5 };

describe('PistaDialog', () => {
  let fixture: ComponentFixture<PistaDialog>;
  const pistas = { crear: vi.fn(), actualizar: vi.fn() };
  const dialogRef = { close: vi.fn() };

  async function crear(data: { clubId: number; pista?: Pista }): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [PistaDialog],
      providers: [
        { provide: PistaService, useValue: pistas },
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(PistaDialog);
    await fixture.whenStable();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const nombre = (): HTMLInputElement =>
    el().querySelector<HTMLInputElement>('input[name="nombre"]')!;
  const tipo = (): HTMLSelectElement =>
    el().querySelector<HTMLSelectElement>('select[name="tipo"]')!;
  const guardar = (): HTMLButtonElement =>
    el().querySelector<HTMLButtonElement>('button[type="submit"]')!;

  beforeEach(() => vi.resetAllMocks());

  describe('alta', () => {
    it('ofrece los tres tipos en español y pide el nombre', async () => {
      await crear({ clubId: 5 });

      expect(el().querySelector('h2')?.textContent).toContain('Nueva pista');
      const opciones = Array.from(tipo().querySelectorAll('option')).map((o) =>
        o.textContent!.trim(),
      );
      expect(opciones).toEqual(['Interior', 'Exterior', 'Cubierta']);
      expect(guardar().disabled).toBe(true); // sin nombre
    });

    it('crea la pista con POST y cierra devolviéndola', async () => {
      const creada: Pista = { id: 9, nombre: 'Pista 9', tipo: 'EXTERIOR', activa: true, clubId: 5 };
      pistas.crear.mockReturnValue(of(creada));
      await crear({ clubId: 5 });

      escribir(nombre(), '  Pista 9  ');
      escribir(tipo(), 'EXTERIOR');
      fixture.detectChanges();
      guardar().click();
      await fixture.whenStable();

      expect(pistas.crear).toHaveBeenCalledWith(5, { nombre: 'Pista 9', tipo: 'EXTERIOR' });
      expect(dialogRef.close).toHaveBeenCalledWith(creada);
    });

    it('un nombre solo con espacios no se puede enviar', async () => {
      await crear({ clubId: 5 });

      escribir(nombre(), '    ');
      fixture.detectChanges();

      expect(guardar().disabled).toBe(true);
    });

    it('un 409 de nombre duplicado se muestra dentro del diálogo y no lo cierra', async () => {
      pistas.crear.mockReturnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 409,
              error: { error: 'Ya existe una pista con ese nombre en el club' },
            }),
        ),
      );
      await crear({ clubId: 5 });

      escribir(nombre(), 'Pista 1');
      fixture.detectChanges();
      guardar().click();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(el().querySelector('[role="alert"]')?.textContent).toContain(
        'Ya existe una pista con ese nombre en el club',
      );
      expect(dialogRef.close).not.toHaveBeenCalled();
      expect(guardar().disabled).toBe(false);
    });
  });

  describe('edición', () => {
    it('precarga nombre y tipo', async () => {
      await crear({ clubId: 5, pista: EXISTENTE });

      expect(el().querySelector('h2')?.textContent).toContain('Editar pista');
      expect(nombre().value).toBe('Central');
      expect(tipo().value).toBe('CUBIERTA');
    });

    it('actualiza con PUT sin tocar "activa" y cierra devolviendo la pista', async () => {
      const editada: Pista = { ...EXISTENTE, nombre: 'Central 2' };
      pistas.actualizar.mockReturnValue(of(editada));
      await crear({ clubId: 5, pista: EXISTENTE });

      escribir(nombre(), 'Central 2');
      fixture.detectChanges();
      guardar().click();
      await fixture.whenStable();

      expect(pistas.actualizar).toHaveBeenCalledWith(5, 4, {
        nombre: 'Central 2',
        tipo: 'CUBIERTA',
      });
      expect(dialogRef.close).toHaveBeenCalledWith(editada);
    });
  });

  it('"Cancelar" cierra sin resultado', async () => {
    await crear({ clubId: 5 });

    el().querySelector<HTMLButtonElement>('button[data-accion="cancelar"]')!.click();

    expect(dialogRef.close).toHaveBeenCalledTimes(1);
    expect(dialogRef.close.mock.calls[0][0]).toBeUndefined();
  });
});
