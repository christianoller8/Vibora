import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { ClubCreadoDialog } from './club-creado-dialog';

describe('ClubCreadoDialog', () => {
  let fixture: ComponentFixture<ClubCreadoDialog>;
  const dialogRef = { close: vi.fn() };

  beforeEach(async () => {
    vi.resetAllMocks();
    await TestBed.configureTestingModule({
      imports: [ClubCreadoDialog],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { nombre: 'Club Nuevo' } },
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ClubCreadoDialog);
    await fixture.whenStable();
  });

  const texto = (): string =>
    (fixture.nativeElement as HTMLElement).textContent!.replace(/\s+/g, ' ');

  it('dice que el club se creó, con su nombre', () => {
    expect(texto()).toContain('Club creado');
    expect(texto()).toContain('Club Nuevo');
  });

  it('explica que hay que iniciar sesión de nuevo para activar los permisos de administrador', () => {
    expect(texto()).toContain('iniciar sesión de nuevo');
    expect(texto()).toContain('permisos de administrador');
  });

  it('el botón cierra el diálogo', () => {
    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('button[data-accion="entendido"]')!
      .click();

    expect(dialogRef.close).toHaveBeenCalledTimes(1);
  });
});
