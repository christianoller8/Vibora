import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { USUARIO_JUGADOR, crearAuthFalso } from '../../../testing/test-utils';
import { Login } from './login';

describe('Login', () => {
  let fixture: ComponentFixture<Login>;
  let auth: ReturnType<typeof crearAuthFalso>;
  let navegar: ReturnType<typeof vi.spyOn>;

  async function crear(queryParams: Record<string, string> = {}): Promise<void> {
    auth = crearAuthFalso(null);
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } },
        },
      ],
    }).compileComponents();
    navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(Login);
    await fixture.whenStable();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const boton = (): HTMLButtonElement => el().querySelector('button[type="submit"]')!;

  async function rellenar(email: string, password: string): Promise<void> {
    const campos = el().querySelectorAll<HTMLInputElement>('input');
    campos[0].value = email;
    campos[0].dispatchEvent(new Event('input'));
    campos[1].value = password;
    campos[1].dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function enviar(): Promise<void> {
    el().querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    await fixture.whenStable();
  }

  afterEach(() => vi.restoreAllMocks());

  it('tiene el botón de entrar deshabilitado mientras el formulario es inválido', async () => {
    await crear();
    expect(boton().disabled).toBe(true);
  });

  it('muestra un error de email inválido tras tocar el campo', async () => {
    await crear();
    const email = el().querySelector<HTMLInputElement>('input[type="email"]')!;
    email.value = 'no-es-email';
    email.dispatchEvent(new Event('input'));
    email.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
    await fixture.whenStable();

    expect(el().textContent).toContain('Introduce un correo electrónico válido');
  });

  it('habilita el botón con datos válidos', async () => {
    await crear();
    await rellenar('ana@vibora.es', 'secreto123');
    expect(boton().disabled).toBe(false);
  });

  it('no llama a login si el formulario es inválido', async () => {
    await crear();
    await enviar();
    expect(auth.login).not.toHaveBeenCalled();
  });

  it('inicia sesión y navega a /agenda por defecto', async () => {
    await crear();
    auth.login.mockReturnValue(of(USUARIO_JUGADOR));
    await rellenar('ana@vibora.es', 'secreto123');

    await enviar();

    expect(auth.login).toHaveBeenCalledWith('ana@vibora.es', 'secreto123');
    expect(navegar).toHaveBeenCalledWith('/agenda');
  });

  it('respeta redirectTo cuando es una ruta interna', async () => {
    await crear({ redirectTo: '/mis-reservas' });
    auth.login.mockReturnValue(of(USUARIO_JUGADOR));
    await rellenar('ana@vibora.es', 'secreto123');

    await enviar();

    expect(navegar).toHaveBeenCalledWith('/mis-reservas');
  });

  it.each(['https://malo.example.com', '//malo.example.com', 'javascript:alert(1)'])(
    'ignora un redirectTo externo (%s)',
    async (redirectTo) => {
      await crear({ redirectTo });
      auth.login.mockReturnValue(of(USUARIO_JUGADOR));
      await rellenar('ana@vibora.es', 'secreto123');

      await enviar();

      expect(navegar).toHaveBeenCalledWith('/agenda');
    },
  );

  it('deshabilita el botón y muestra el estado de carga mientras envía', async () => {
    await crear();
    const respuesta = new Subject<typeof USUARIO_JUGADOR>();
    auth.login.mockReturnValue(respuesta);
    await rellenar('ana@vibora.es', 'secreto123');

    await enviar();

    expect(boton().disabled).toBe(true);
    expect(boton().textContent).toContain('Entrando');

    respuesta.next(USUARIO_JUGADOR);
    respuesta.complete();
    await fixture.whenStable();
    expect(navegar).toHaveBeenCalledWith('/agenda');
  });

  it('muestra el mensaje del backend ante credenciales inválidas y no navega', async () => {
    await crear();
    auth.login.mockReturnValue(
      throwError(
        () => new HttpErrorResponse({ status: 401, error: { error: 'Credenciales incorrectas' } }),
      ),
    );
    await rellenar('ana@vibora.es', 'mala-clave');

    await enviar();

    const alerta = el().querySelector('[role="alert"]');
    expect(alerta?.textContent).toContain('Credenciales incorrectas');
    expect(navegar).not.toHaveBeenCalled();
    expect(boton().disabled).toBe(false);
  });
});
