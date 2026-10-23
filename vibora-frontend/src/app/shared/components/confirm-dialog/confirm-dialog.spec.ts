import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { ConfirmDialog, ConfirmDialogData } from './confirm-dialog';

describe('ConfirmDialog', () => {
  let fixture: ComponentFixture<ConfirmDialog>;
  const cerrar = vi.fn();

  const datos: ConfirmDialogData = {
    titulo: 'Confirmar reserva',
    lineas: [
      { etiqueta: 'Club', valor: 'Club Víbora' },
      { etiqueta: 'Precio', valor: '12,00 €' },
    ],
    textoConfirmar: 'Reservar',
  };

  beforeEach(async () => {
    cerrar.mockClear();
    await TestBed.configureTestingModule({
      imports: [ConfirmDialog],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: datos },
        { provide: MatDialogRef, useValue: { close: cerrar } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ConfirmDialog);
    await fixture.whenStable();
  });

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;

  it('muestra el título y cada línea etiqueta/valor', () => {
    expect(el().textContent).toContain('Confirmar reserva');
    expect(el().textContent).toContain('Club');
    expect(el().textContent).toContain('Club Víbora');
    expect(el().textContent).toContain('12,00 €');
  });

  it('cierra con true al confirmar', () => {
    el().querySelector<HTMLButtonElement>('button[data-accion="confirmar"]')!.click();
    expect(cerrar).toHaveBeenCalledWith(true);
  });

  it('cierra con false al cancelar', () => {
    el().querySelector<HTMLButtonElement>('button[data-accion="cancelar"]')!.click();
    expect(cerrar).toHaveBeenCalledWith(false);
  });
});
