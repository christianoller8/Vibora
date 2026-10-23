import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { Tarifa } from '../../../shared/models/tarifa.model';
import { escribir } from '../../../testing/test-utils';
import { TarifaService } from '../tarifa.service';
import { TarifaDialog } from './tarifa-dialog';

const EXISTENTE: Tarifa = {
  id: 3,
  clubId: 5,
  diaSemana: 'FRIDAY',
  horaInicio: '18:00:00',
  horaFin: '23:00:00',
  precioHora: 20,
  tipo: 'HORA_PUNTA',
};

describe('TarifaDialog', () => {
  let fixture: ComponentFixture<TarifaDialog>;
  const tarifas = { crear: vi.fn(), actualizar: vi.fn() };
  const dialogRef = { close: vi.fn() };

  async function crear(data: { clubId: number; tarifa?: Tarifa }): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [TarifaDialog],
      providers: [
        { provide: TarifaService, useValue: tarifas },
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(TarifaDialog);
    await fixture.whenStable();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const campo = (nombre: string): HTMLInputElement =>
    el().querySelector<HTMLInputElement>(`input[name="${nombre}"]`)!;
  const selector = (nombre: string): HTMLSelectElement =>
    el().querySelector<HTMLSelectElement>(`select[name="${nombre}"]`)!;
  const guardar = (): HTMLButtonElement =>
    el().querySelector<HTMLButtonElement>('button[type="submit"]')!;

  function rellenarValido(): void {
    escribir(selector('diaSemana'), 'TUESDAY');
    escribir(campo('horaInicio'), '09:00');
    escribir(campo('horaFin'), '14:00');
    escribir(campo('precioHora'), '12.5');
    escribir(selector('tipo'), 'NORMAL');
    fixture.detectChanges();
  }

  beforeEach(() => vi.resetAllMocks());

  describe('alta', () => {
    it('ofrece los días en español (valores del enum en inglés) y los tipos de tarifa', async () => {
      await crear({ clubId: 5 });

      expect(el().querySelector('h2')?.textContent).toContain('Nueva tarifa');
      const dias = Array.from(selector('diaSemana').querySelectorAll('option')).map((o) => [
        o.textContent!.trim(),
        o.value,
      ]);
      expect(dias[0]).toEqual(['Lunes', 'MONDAY']);
      expect(dias[6]).toEqual(['Domingo', 'SUNDAY']);
      expect(dias).toHaveLength(7);
      const tipos = Array.from(selector('tipo').querySelectorAll('option')).map((o) =>
        o.textContent!.trim(),
      );
      expect(tipos).toEqual(['Normal', 'Hora punta', 'Luz artificial']);
      expect(campo('horaInicio').type).toBe('time');
      expect(campo('precioHora').type).toBe('number');
    });

    it('no se puede enviar vacío', async () => {
      await crear({ clubId: 5 });

      expect(guardar().disabled).toBe(true);
    });

    it('crea la franja con POST: horas en HH:mm, precio numérico y día en inglés', async () => {
      const creada: Tarifa = { ...EXISTENTE, id: 9, diaSemana: 'TUESDAY' };
      tarifas.crear.mockReturnValue(of(creada));
      await crear({ clubId: 5 });

      rellenarValido();
      guardar().click();
      await fixture.whenStable();

      expect(tarifas.crear).toHaveBeenCalledWith(5, {
        diaSemana: 'TUESDAY',
        horaInicio: '09:00',
        horaFin: '14:00',
        precioHora: 12.5,
        tipo: 'NORMAL',
      });
      expect(dialogRef.close).toHaveBeenCalledWith(creada);
    });

    it('exige que el fin sea posterior al inicio y lo explica', async () => {
      await crear({ clubId: 5 });
      rellenarValido();

      escribir(campo('horaFin'), '09:00');
      fixture.detectChanges();

      expect(guardar().disabled).toBe(true);
      expect(el().textContent).toContain('La hora de fin debe ser posterior a la de inicio');
      expect(tarifas.crear).not.toHaveBeenCalled();
    });

    it('rechaza un precio negativo', async () => {
      await crear({ clubId: 5 });
      rellenarValido();

      escribir(campo('precioHora'), '-1');
      fixture.detectChanges();

      expect(guardar().disabled).toBe(true);
      expect(el().textContent).toContain('El precio no puede ser negativo');
    });

    it('acepta un precio 0', async () => {
      await crear({ clubId: 5 });
      rellenarValido();

      escribir(campo('precioHora'), '0');
      fixture.detectChanges();

      expect(guardar().disabled).toBe(false);
    });

    it('exige el precio', async () => {
      await crear({ clubId: 5 });
      rellenarValido();

      escribir(campo('precioHora'), '');
      fixture.detectChanges();

      expect(guardar().disabled).toBe(true);
    });

    it('un 409 de solape se muestra dentro del diálogo y no lo cierra', async () => {
      tarifas.crear.mockReturnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 409,
              error: { error: 'La franja se solapa con otra del mismo día' },
            }),
        ),
      );
      await crear({ clubId: 5 });
      rellenarValido();

      guardar().click();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(el().querySelector('[role="alert"]')?.textContent).toContain(
        'La franja se solapa con otra del mismo día',
      );
      expect(dialogRef.close).not.toHaveBeenCalled();
      expect(guardar().disabled).toBe(false);
    });
  });

  describe('edición', () => {
    it('precarga todos los campos recortando los segundos', async () => {
      await crear({ clubId: 5, tarifa: EXISTENTE });

      expect(el().querySelector('h2')?.textContent).toContain('Editar tarifa');
      expect(selector('diaSemana').value).toBe('FRIDAY');
      expect(campo('horaInicio').value).toBe('18:00');
      expect(campo('horaFin').value).toBe('23:00');
      expect(campo('precioHora').value).toBe('20');
      expect(selector('tipo').value).toBe('HORA_PUNTA');
    });

    it('actualiza con PUT y cierra devolviendo la franja', async () => {
      const editada: Tarifa = { ...EXISTENTE, precioHora: 25 };
      tarifas.actualizar.mockReturnValue(of(editada));
      await crear({ clubId: 5, tarifa: EXISTENTE });

      escribir(campo('precioHora'), '25');
      fixture.detectChanges();
      guardar().click();
      await fixture.whenStable();

      expect(tarifas.actualizar).toHaveBeenCalledWith(5, 3, {
        diaSemana: 'FRIDAY',
        horaInicio: '18:00',
        horaFin: '23:00',
        precioHora: 25,
        tipo: 'HORA_PUNTA',
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
