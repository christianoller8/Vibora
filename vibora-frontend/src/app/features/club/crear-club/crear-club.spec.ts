import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { ClubService } from '../../../core/api/club.service';
import { AuthService } from '../../../core/auth/auth.service';
import { Club } from '../../../shared/models/club.model';
import { crearAuthFalso, escribir } from '../../../testing/test-utils';
import { ClubCreadoDialog } from './club-creado-dialog';
import { CrearClub } from './crear-club';

const CLUB_CREADO: Club = {
  id: 8,
  nombre: 'Club Nuevo',
  direccion: null,
  ciudad: 'Madrid',
  horaApertura: '09:00:00',
  horaCierre: '22:00:00',
};

describe('CrearClub', () => {
  let fixture: ComponentFixture<CrearClub>;
  let auth: ReturnType<typeof crearAuthFalso>;
  let cerrarDialogo: Subject<void>;
  let navegar: ReturnType<typeof vi.fn>;
  const clubes = { crear: vi.fn() };
  const dialogo = { open: vi.fn() };

  async function crear(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [CrearClub],
      providers: [
        { provide: ClubService, useValue: clubes },
        { provide: AuthService, useValue: auth },
        { provide: MatDialog, useValue: dialogo },
      ],
    }).compileComponents();
    navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(CrearClub);
    await fixture.whenStable();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const campo = (nombre: string): HTMLInputElement =>
    el().querySelector<HTMLInputElement>(`input[name="${nombre}"]`)!;
  const boton = (): HTMLButtonElement =>
    el().querySelector<HTMLButtonElement>('button[type="submit"]')!;

  async function enviarFormularioValido(): Promise<void> {
    escribir(campo('nombre'), 'Club Nuevo');
    escribir(campo('ciudad'), 'Madrid');
    escribir(campo('horaApertura'), '09:00');
    escribir(campo('horaCierre'), '22:00');
    fixture.detectChanges();
    boton().click();
    await fixture.whenStable();
  }

  beforeEach(() => {
    vi.resetAllMocks();
    auth = crearAuthFalso();
    cerrarDialogo = new Subject<void>();
    clubes.crear.mockReturnValue(of(CLUB_CREADO));
    dialogo.open.mockReturnValue({ afterClosed: () => cerrarDialogo.asObservable() });
  });

  it('muestra el título y el formulario de alta', async () => {
    await crear();

    expect(el().querySelector('h1')?.textContent).toContain('Crear mi club');
    expect(campo('nombre')).not.toBeNull();
    expect(boton().textContent).toContain('Crear club');
  });

  it('no envía nada si el nombre está vacío', async () => {
    await crear();
    escribir(campo('nombre'), '');
    fixture.detectChanges();

    expect(boton().disabled).toBe(true);
    expect(clubes.crear).not.toHaveBeenCalled();
  });

  it('no envía si el cierre no es posterior a la apertura', async () => {
    await crear();
    escribir(campo('nombre'), 'Club Nuevo');
    escribir(campo('horaApertura'), '10:00');
    escribir(campo('horaCierre'), '10:00');
    fixture.detectChanges();

    expect(boton().disabled).toBe(true);
    expect(el().textContent).toContain('La hora de cierre debe ser posterior a la de apertura');
    expect(clubes.crear).not.toHaveBeenCalled();
  });

  it('envía los datos al backend', async () => {
    await crear();

    await enviarFormularioValido();

    expect(clubes.crear).toHaveBeenCalledWith({
      nombre: 'Club Nuevo',
      direccion: null,
      ciudad: 'Madrid',
      horaApertura: '09:00',
      horaCierre: '22:00',
    });
  });

  it('al crearlo explica en un diálogo que debe iniciar sesión de nuevo (sin cerrar la sesión aún)', async () => {
    await crear();

    await enviarFormularioValido();

    expect(dialogo.open).toHaveBeenCalledTimes(1);
    const [componente, opciones] = dialogo.open.mock.calls[0];
    expect(componente).toBe(ClubCreadoDialog);
    expect(opciones.disableClose).toBe(true);
    expect(opciones.data).toEqual({ nombre: 'Club Nuevo' });
    expect(auth.logout).not.toHaveBeenCalled(); // el usuario aún está leyendo el aviso
    expect(navegar).not.toHaveBeenCalled();
  });

  it('al cerrar el diálogo cierra la sesión (token obsoleto) y va a /login', async () => {
    await crear();
    await enviarFormularioValido();

    cerrarDialogo.next();
    cerrarDialogo.complete();

    expect(auth.logout).toHaveBeenCalledTimes(1);
    expect(navegar).toHaveBeenCalledWith('/login');
  });

  it('un 409 (ya tiene club) muestra el mensaje del backend y no cierra la sesión', async () => {
    clubes.crear.mockReturnValue(
      throwError(
        () => new HttpErrorResponse({ status: 409, error: { error: 'Ya perteneces a un club' } }),
      ),
    );
    await crear();

    await enviarFormularioValido();
    fixture.detectChanges();

    expect(el().querySelector('[role="alert"]')?.textContent).toContain('Ya perteneces a un club');
    expect(dialogo.open).not.toHaveBeenCalled();
    expect(auth.logout).not.toHaveBeenCalled();
    expect(boton().disabled).toBe(false); // se puede corregir y reintentar
  });

  it('un error de red muestra un mensaje genérico', async () => {
    clubes.crear.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    await crear();

    await enviarFormularioValido();
    fixture.detectChanges();

    expect(el().querySelector('[role="alert"]')?.textContent).toContain('No se pudo crear el club');
  });
});
