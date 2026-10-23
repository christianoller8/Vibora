import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { USUARIO_JUGADOR, crearAuthFalso } from '../../../testing/test-utils';
import { Registro } from './registro';

describe('Registro', () => {
  let fixture: ComponentFixture<Registro>;
  let auth: ReturnType<typeof crearAuthFalso>;
  let navegar: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    auth = crearAuthFalso(null);
    await TestBed.configureTestingModule({
      imports: [Registro],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    }).compileComponents();
    navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(Registro);
    await fixture.whenStable();
  });

  afterEach(() => vi.restoreAllMocks());

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const boton = (): HTMLButtonElement => el().querySelector('button[type="submit"]')!;

  async function escribir(nombreCampo: string, valor: string, tocar = false): Promise<void> {
    const campo = el().querySelector<HTMLInputElement>(`input[name="${nombreCampo}"]`)!;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
    if (tocar) {
      campo.dispatchEvent(new Event('blur'));
    }
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function rellenarValido(): Promise<void> {
    await escribir('email', 'ana@vibora.es');
    await escribir('password', 'secreto123');
    await escribir('apellidos', 'García López');
  }

  async function enviar(): Promise<void> {
    el().querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    await fixture.whenStable();
  }

  it('empieza con el botón deshabilitado', () => {
    expect(boton().disabled).toBe(true);
  });

  it('exige una contraseña de al menos 8 caracteres', async () => {
    await escribir('password', 'corta', true);
    expect(el().textContent).toContain('La contraseña debe tener al menos 8 caracteres');
  });

  it('rechaza una contraseña de más de 72 bytes UTF-8 aunque tenga menos de 72 caracteres', async () => {
    await rellenarValido();
    await escribir('password', 'ñ'.repeat(40), true); // 40 caracteres, 80 bytes

    expect(el().textContent).toContain('La contraseña no puede superar los 72 bytes');
    expect(el().textContent).not.toContain('al menos 8 caracteres');
    expect(boton().disabled).toBe(true);
  });

  it('acepta una contraseña de exactamente 72 bytes (36 "ñ" o 72 ASCII)', async () => {
    await rellenarValido();

    await escribir('password', 'ñ'.repeat(36), true);
    expect(boton().disabled).toBe(false);

    await escribir('password', 'a'.repeat(72), true);
    expect(boton().disabled).toBe(false);

    await escribir('password', 'a'.repeat(73), true);
    expect(boton().disabled).toBe(true);
  });

  it('mide los bytes de la forma normalizada NFC, como hace el backend', async () => {
    await rellenarValido();
    await escribir('password', 'ñ'.repeat(36).normalize('NFD'), true); // 108 bytes en NFD, 72 en NFC

    expect(boton().disabled).toBe(false);
  });

  it('exige los apellidos', async () => {
    await escribir('apellidos', '', true);
    expect(el().textContent).toContain('Los apellidos son obligatorios');
  });

  it('el nombre es opcional', async () => {
    await rellenarValido();
    expect(boton().disabled).toBe(false);
  });

  it('registra, inicia sesión con las mismas credenciales y va a /agenda', async () => {
    auth.registro.mockReturnValue(of(USUARIO_JUGADOR));
    auth.login.mockReturnValue(of(USUARIO_JUGADOR));
    await escribir('nombre', 'Ana');
    await rellenarValido();

    await enviar();

    expect(auth.registro).toHaveBeenCalledWith({
      email: 'ana@vibora.es',
      password: 'secreto123',
      nombre: 'Ana',
      apellidos: 'García López',
    });
    expect(auth.login).toHaveBeenCalledWith('ana@vibora.es', 'secreto123');
    expect(navegar).toHaveBeenCalledWith('/agenda');
  });

  it('envía el nombre como null cuando se deja vacío', async () => {
    auth.registro.mockReturnValue(of(USUARIO_JUGADOR));
    auth.login.mockReturnValue(of(USUARIO_JUGADOR));
    await rellenarValido();

    await enviar();

    expect(auth.registro).toHaveBeenCalledWith(expect.objectContaining({ nombre: null }));
  });

  it('muestra el 409 de email duplicado y no intenta el login', async () => {
    auth.registro.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({ status: 409, error: { error: 'El email ya está registrado' } }),
      ),
    );
    await rellenarValido();

    await enviar();

    expect(el().querySelector('[role="alert"]')?.textContent).toContain(
      'El email ya está registrado',
    );
    expect(auth.login).not.toHaveBeenCalled();
    expect(navegar).not.toHaveBeenCalled();
    expect(boton().disabled).toBe(false);
  });

  it('muestra un error si el registro va bien pero el login automático falla', async () => {
    auth.registro.mockReturnValue(of(USUARIO_JUGADOR));
    auth.login.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500, error: 'boom' })),
    );
    await rellenarValido();

    await enviar();

    expect(el().querySelector('[role="alert"]')).not.toBeNull();
    expect(navegar).not.toHaveBeenCalled();
  });
});
