import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject, of, throwError } from 'rxjs';
import { ClubService } from '../../../core/api/club.service';
import { Club } from '../../../shared/models/club.model';
import { escribir } from '../../../testing/test-utils';
import { DatosClub } from './datos-club';

const CLUB: Club = {
  id: 5,
  nombre: 'Club Víbora Demo',
  direccion: 'Calle Mayor 1',
  ciudad: 'Madrid',
  horaApertura: '09:00:00',
  horaCierre: '22:00:00',
};

describe('DatosClub', () => {
  let fixture: ComponentFixture<DatosClub>;
  const clubes = { obtener: vi.fn(), actualizar: vi.fn() };
  const snack = { open: vi.fn() };

  async function crear(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [DatosClub],
      providers: [
        { provide: ClubService, useValue: clubes },
        { provide: MatSnackBar, useValue: snack },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(DatosClub);
    fixture.componentRef.setInput('clubId', 5);
    await fixture.whenStable();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const campo = (nombre: string): HTMLInputElement =>
    el().querySelector<HTMLInputElement>(`input[name="${nombre}"]`)!;
  const boton = (): HTMLButtonElement =>
    el().querySelector<HTMLButtonElement>('button[type="submit"]')!;

  beforeEach(() => {
    vi.resetAllMocks();
    clubes.obtener.mockReturnValue(of(CLUB));
  });

  it('carga el club indicado y rellena el formulario', async () => {
    await crear();

    expect(clubes.obtener).toHaveBeenCalledWith(5);
    expect(campo('nombre').value).toBe('Club Víbora Demo');
    expect(campo('horaApertura').value).toBe('09:00');
    expect(boton().textContent).toContain('Guardar cambios');
  });

  it('muestra un spinner mientras carga', async () => {
    clubes.obtener.mockReturnValue(new Subject<Club>());
    await crear();

    expect(el().querySelector('mat-spinner')).not.toBeNull();
    expect(campo('nombre')).toBeNull();
  });

  it('si falla la carga muestra el error y permite reintentar', async () => {
    clubes.obtener.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
    await crear();

    expect(el().querySelector('[role="alert"]')?.textContent).toContain('No se pudieron cargar');

    el().querySelector<HTMLButtonElement>('button[data-accion="reintentar"]')!.click();
    await fixture.whenStable();
    expect(clubes.obtener).toHaveBeenCalledTimes(2);
    expect(campo('nombre').value).toBe('Club Víbora Demo');
  });

  it('guarda los cambios con PUT, avisa y deja el formulario con lo guardado', async () => {
    clubes.actualizar.mockReturnValue(of({ ...CLUB, nombre: 'Club Renombrado' }));
    await crear();

    escribir(campo('nombre'), 'Club Renombrado');
    fixture.detectChanges();
    boton().click();
    await fixture.whenStable();

    expect(clubes.actualizar).toHaveBeenCalledWith(5, {
      nombre: 'Club Renombrado',
      direccion: 'Calle Mayor 1',
      ciudad: 'Madrid',
      horaApertura: '09:00',
      horaCierre: '22:00',
    });
    expect(snack.open.mock.calls[0][0]).toContain('Datos del club guardados');
    expect(campo('nombre').value).toBe('Club Renombrado');
  });

  it('no envía si el cierre no es posterior a la apertura', async () => {
    await crear();

    escribir(campo('horaCierre'), '08:00');
    fixture.detectChanges();

    expect(boton().disabled).toBe(true);
    expect(el().textContent).toContain('La hora de cierre debe ser posterior a la de apertura');
    expect(clubes.actualizar).not.toHaveBeenCalled();
  });

  it('si el backend rechaza el cambio muestra su mensaje sin avisar de éxito', async () => {
    clubes.actualizar.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 403, error: { error: 'Acceso denegado' } })),
    );
    await crear();

    boton().click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(el().querySelector('[role="alert"]')?.textContent).toContain('Acceso denegado');
    expect(snack.open).not.toHaveBeenCalled();
  });
});
