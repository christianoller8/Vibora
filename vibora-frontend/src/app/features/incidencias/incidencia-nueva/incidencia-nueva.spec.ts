import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { ClubService } from '../../../core/api/club.service';
import { AuthService } from '../../../core/auth/auth.service';
import { Club } from '../../../shared/models/club.model';
import { FotoIncidencia, IncidenciaDetalle } from '../../../shared/models/incidencia.model';
import { Pista } from '../../../shared/models/pista.model';
import { Usuario } from '../../../shared/models/usuario.model';
import {
  USUARIO_ADMIN,
  USUARIO_JUGADOR,
  crearAuthFalso,
  crearImagen,
  elegirArchivos,
  escribir,
  simularObjectUrls,
} from '../../../testing/test-utils';
import { CLAVE_CLUB_INCIDENCIAS } from '../club-preferido';
import { FotoIncidenciaService } from '../foto-incidencia.service';
import { IncidenciaService } from '../incidencia.service';
import { IncidenciaNueva } from './incidencia-nueva';

function club(id: number, nombre: string): Club {
  return {
    id,
    nombre,
    direccion: null,
    ciudad: null,
    horaApertura: '08:00:00',
    horaCierre: '22:00:00',
  };
}

const CLUBES = [club(3, 'Club Tres'), club(5, 'Club Víbora Demo')];
const PISTAS: Pista[] = [
  { id: 11, nombre: 'Pista 1', tipo: 'INTERIOR', activa: true, clubId: 5 },
  { id: 12, nombre: 'Pista vieja', tipo: 'EXTERIOR', activa: false, clubId: 5 },
  { id: 13, nombre: 'Pista 2', tipo: 'CUBIERTA', activa: true, clubId: 5 },
];
const CREADA = { id: 11, estado: 'ABIERTA' } as IncidenciaDetalle;
const FOTO = { id: 1 } as FotoIncidencia;

const http = (status: number, error?: unknown): HttpErrorResponse =>
  new HttpErrorResponse({ status, error });

describe('IncidenciaNueva', () => {
  let fixture: ComponentFixture<IncidenciaNueva>;
  let urls: ReturnType<typeof simularObjectUrls>;
  const clubService = { listar: vi.fn(), pistas: vi.fn() };
  const incidencias = { crear: vi.fn() };
  const fotos = { subir: vi.fn() };
  const snack = { open: vi.fn() };

  async function crear(
    usuario: Usuario = USUARIO_ADMIN,
    queryClub: string | null = null,
  ): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [IncidenciaNueva],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: crearAuthFalso(usuario) },
        { provide: ClubService, useValue: clubService },
        { provide: IncidenciaService, useValue: incidencias },
        { provide: FotoIncidenciaService, useValue: fotos },
        { provide: MatSnackBar, useValue: snack },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: convertToParamMap(queryClub === null ? {} : { club: queryClub }),
            },
          },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(IncidenciaNueva);
    await fixture.whenStable();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const campo = <T extends HTMLElement>(selector: string): T => el().querySelector<T>(selector)!;
  const titulo = (): HTMLInputElement => campo('input[name="titulo"]');
  const descripcion = (): HTMLTextAreaElement => campo('textarea[name="descripcion"]');
  const categoria = (): HTMLSelectElement => campo('select[data-campo="categoria"]');
  const pista = (): HTMLSelectElement => campo('select[data-campo="pista"]');
  const selectClub = (): HTMLSelectElement => campo('select[data-campo="club"]');
  const enviar = (): HTMLButtonElement => campo('button[type="submit"]');
  const alerta = (): string | undefined =>
    el().querySelector('[role="alert"]')?.textContent?.trim();
  const estados = (): string[] =>
    Array.from(el().querySelectorAll('.subidas li')).map((li) => li.getAttribute('data-estado')!);

  async function rellenar(extra: { cat?: string } = {}): Promise<void> {
    escribir(titulo(), '  Foco fundido  ');
    escribir(descripcion(), '  No se ve nada por la noche  ');
    escribir(categoria(), extra.cat ?? 'ILUMINACION');
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function pulsarEnviar(): Promise<void> {
    enviar().click();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  let navegar: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    vi.resetAllMocks();
    localStorage.clear();
    urls = simularObjectUrls();
    clubService.listar.mockReturnValue(of(CLUBES));
    clubService.pistas.mockReturnValue(of(PISTAS));
    incidencias.crear.mockReturnValue(of(CREADA));
    fotos.subir.mockReturnValue(of(FOTO));
  });
  afterEach(() => {
    urls.restaurar();
    vi.restoreAllMocks();
    localStorage.clear();
  });

  async function crearConRouter(usuario?: Usuario, queryClub?: string | null): Promise<void> {
    await crear(usuario, queryClub ?? null);
    navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  }

  describe('formulario', () => {
    it('ofrece las cinco categorías en español y exige elegir una', async () => {
      await crearConRouter();

      const opciones = Array.from(categoria().options)
        .filter((o) => o.value !== '')
        .map((o) => o.textContent!.trim());
      expect(opciones).toEqual(['Red', 'Iluminación', 'Cristal', 'Suelo', 'Otra']);
      expect(categoria().value).toBe('');
    });

    it('arranca con el club del usuario y carga SUS pistas, solo las activas, más "Sin pista concreta"', async () => {
      await crearConRouter(USUARIO_ADMIN);

      expect(selectClub().value).toBe('5');
      expect(clubService.pistas).toHaveBeenCalledWith(5);
      const opciones = Array.from(pista().options).map((o) => o.textContent!.trim());
      expect(opciones).toEqual(['Sin pista concreta', 'Pista 1', 'Pista 2']);
    });

    it('el club de la URL (?club=) manda sobre el del usuario', async () => {
      await crearConRouter(USUARIO_ADMIN, '3');

      expect(selectClub().value).toBe('3');
      expect(clubService.pistas).toHaveBeenCalledWith(3);
    });

    it('un ?club= que no existe se ignora', async () => {
      await crearConRouter(USUARIO_ADMIN, '999');

      expect(selectClub().value).toBe('5');
    });

    it('sin club propio usa el último elegido en localStorage, o el primero', async () => {
      localStorage.setItem(CLAVE_CLUB_INCIDENCIAS, '5');
      await crearConRouter(USUARIO_JUGADOR);
      expect(selectClub().value).toBe('5');
      TestBed.resetTestingModule();

      localStorage.clear();
      await crearConRouter(USUARIO_JUGADOR);
      expect(selectClub().value).toBe('3');
    });

    it('cambiar de club recarga las pistas y deja la pista en "Sin pista concreta"', async () => {
      await crearConRouter(USUARIO_ADMIN);
      escribir(pista(), '13');
      fixture.detectChanges();

      escribir(selectClub(), '3');
      await fixture.whenStable();
      fixture.detectChanges();

      expect(clubService.pistas).toHaveBeenLastCalledWith(3);
      expect(pista().value).toBe('');
    });

    it('si fallan las pistas lo avisa pero deja reportar sin pista', async () => {
      clubService.pistas.mockReturnValue(throwError(() => http(500)));

      await crearConRouter();

      expect(alerta()).toContain('No se pudieron cargar las pistas');
      expect(Array.from(pista().options).map((o) => o.textContent!.trim())).toEqual([
        'Sin pista concreta',
      ]);
    });

    it('no se puede enviar vacío ni con solo espacios', async () => {
      await crearConRouter();
      expect(enviar().disabled).toBe(true);

      escribir(titulo(), '   ');
      escribir(descripcion(), '   ');
      escribir(categoria(), 'RED');
      fixture.detectChanges();

      expect(enviar().disabled).toBe(true);
    });

    it('el título admite 120 caracteres y no 121; la descripción 2000 y no 2001', async () => {
      await crearConRouter();
      await rellenar();
      expect(enviar().disabled).toBe(false);

      escribir(titulo(), 'a'.repeat(120));
      fixture.detectChanges();
      expect(enviar().disabled).toBe(false);
      escribir(titulo(), 'a'.repeat(121));
      fixture.detectChanges();
      expect(enviar().disabled).toBe(true);
      expect(el().textContent).toContain('120 caracteres');

      escribir(titulo(), 'ok');
      escribir(descripcion(), 'b'.repeat(2000));
      fixture.detectChanges();
      expect(enviar().disabled).toBe(false);
      escribir(descripcion(), 'b'.repeat(2001));
      fixture.detectChanges();
      expect(enviar().disabled).toBe(true);
      expect(el().textContent).toContain('2000 caracteres');
    });

    it('el selector de fotos solo acepta JPEG, PNG y WebP', async () => {
      await crearConRouter();

      expect(campo<HTMLInputElement>('input[type="file"]').getAttribute('accept')).toBe(
        'image/jpeg,image/png,image/webp',
      );
    });
  });

  describe('envío sin fotos', () => {
    it('crea la incidencia en el club elegido con los datos recortados y navega a su detalle', async () => {
      await crearConRouter();
      await rellenar();
      escribir(pista(), '13');
      fixture.detectChanges();

      await pulsarEnviar();

      expect(incidencias.crear).toHaveBeenCalledWith(5, {
        titulo: 'Foco fundido',
        descripcion: 'No se ve nada por la noche',
        categoria: 'ILUMINACION',
        pistaId: 13,
      });
      expect(fotos.subir).not.toHaveBeenCalled();
      expect(navegar).toHaveBeenCalledWith(['/incidencias', 11]);
      expect(snack.open).toHaveBeenCalled();
    });

    it('sin pista elegida envía pistaId null', async () => {
      await crearConRouter();
      await rellenar();

      await pulsarEnviar();

      expect(incidencias.crear.mock.calls[0][1].pistaId).toBeNull();
    });

    it('recuerda el club para la próxima vez', async () => {
      await crearConRouter(USUARIO_JUGADOR);
      escribir(selectClub(), '5');
      await fixture.whenStable();
      await rellenar();

      await pulsarEnviar();

      expect(localStorage.getItem(CLAVE_CLUB_INCIDENCIAS)).toBe('5');
    });

    it('mientras envía bloquea el botón y no crea dos incidencias', async () => {
      incidencias.crear.mockReturnValue(new Subject<IncidenciaDetalle>());
      await crearConRouter();
      await rellenar();

      enviar().click();
      fixture.detectChanges();
      expect(enviar().disabled).toBe(true);
      enviar().click();

      expect(incidencias.crear).toHaveBeenCalledTimes(1);
    });
  });

  describe('errores del backend', () => {
    it('409 por el tope de 5 incidencias abiertas: muestra el mensaje y deja reintentar', async () => {
      incidencias.crear.mockReturnValue(
        throwError(() =>
          http(409, {
            error: 'Ya tienes 5 incidencias sin cerrar en este club; espera a que se resuelvan',
          }),
        ),
      );
      await crearConRouter();
      await rellenar();

      await pulsarEnviar();

      expect(alerta()).toBe(
        'Ya tienes 5 incidencias sin cerrar en este club; espera a que se resuelvan',
      );
      expect(enviar().disabled).toBe(false);
      expect(navegar).not.toHaveBeenCalled();
      expect(fotos.subir).not.toHaveBeenCalled();
    });

    it('403 sin vínculo con el club: explica que solo se puede reportar donde se ha jugado', async () => {
      incidencias.crear.mockReturnValue(throwError(() => http(403)));
      await crearConRouter(USUARIO_JUGADOR);
      await rellenar();

      await pulsarEnviar();

      expect(alerta()).toBe(
        'Solo puedes reportar incidencias en clubes donde has reservado o jugado.',
      );
    });

    it('400 de validación: muestra el mensaje del backend', async () => {
      incidencias.crear.mockReturnValue(throwError(() => http(400, { error: 'Datos no válidos' })));
      await crearConRouter();
      await rellenar();

      await pulsarEnviar();

      expect(alerta()).toBe('Datos no válidos');
    });

    it('un error de red usa el texto de conexión', async () => {
      incidencias.crear.mockReturnValue(throwError(() => http(0)));
      await crearConRouter();
      await rellenar();

      await pulsarEnviar();

      expect(alerta()).toContain('No se pudo conectar con el servidor');
    });

    it('el mensaje de error desaparece al reintentar', async () => {
      incidencias.crear.mockReturnValueOnce(throwError(() => http(409, { error: 'Tope' })));
      await crearConRouter();
      await rellenar();
      await pulsarEnviar();
      expect(alerta()).toBe('Tope');

      await pulsarEnviar();

      expect(alerta()).toBeUndefined();
    });
  });

  describe('subida de fotos tras crear', () => {
    async function conFotos(...nombres: string[]): Promise<void> {
      await crearConRouter();
      await rellenar();
      elegirArchivos(
        campo<HTMLInputElement>('input[type="file"]'),
        nombres.map((n) => crearImagen(n)),
      );
      await fixture.whenStable();
      fixture.detectChanges();
    }

    it('crea primero la incidencia y sube las fotos de una en una, en orden', async () => {
      const primera = new Subject<FotoIncidencia>();
      const segunda = new Subject<FotoIncidencia>();
      fotos.subir.mockReturnValueOnce(primera).mockReturnValueOnce(segunda);
      await conFotos('uno.png', 'dos.png');

      await pulsarEnviar();

      expect(incidencias.crear).toHaveBeenCalledTimes(1);
      expect(fotos.subir).toHaveBeenCalledTimes(1);
      expect(fotos.subir.mock.calls[0][0]).toBe(11);
      expect((fotos.subir.mock.calls[0][1] as File).name).toBe('uno.png');
      expect(estados()).toEqual(['subiendo', 'pendiente']);
      expect(navegar).not.toHaveBeenCalled();

      primera.next(FOTO);
      primera.complete();
      await fixture.whenStable();
      fixture.detectChanges();
      expect(fotos.subir).toHaveBeenCalledTimes(2);
      expect((fotos.subir.mock.calls[1][1] as File).name).toBe('dos.png');
      expect(estados()).toEqual(['subida', 'subiendo']);

      segunda.next(FOTO);
      segunda.complete();
      await fixture.whenStable();

      expect(navegar).toHaveBeenCalledWith(['/incidencias', 11]);
    });

    it('muestra el nombre de cada foto y su progreso mientras se suben', async () => {
      fotos.subir.mockReturnValue(new Subject<FotoIncidencia>());
      await conFotos('uno.png', 'dos.png');

      await pulsarEnviar();

      const nombres = Array.from(el().querySelectorAll('.subidas li .nombre')).map((n) =>
        n.textContent!.trim(),
      );
      expect(nombres).toEqual(['uno.png', 'dos.png']);
      expect(el().querySelector('.subidas')?.getAttribute('aria-label')).toBe('Subida de fotos');
    });

    it('si alguna foto falla conserva la incidencia, dice cuál falló y por qué, y NO navega solo', async () => {
      fotos.subir
        .mockReturnValueOnce(of(FOTO))
        .mockReturnValueOnce(
          throwError(() => http(400, { error: 'Formato de imagen no permitido' })),
        )
        .mockReturnValueOnce(of(FOTO));
      await conFotos('uno.png', 'dos.png', 'tres.png');

      await pulsarEnviar();

      expect(incidencias.crear).toHaveBeenCalledTimes(1);
      expect(fotos.subir).toHaveBeenCalledTimes(3); // las demás se intentan igualmente
      expect(estados()).toEqual(['subida', 'error', 'subida']);
      const fallo = el().querySelector('.subidas li[data-estado="error"]')!;
      expect(fallo.textContent).toContain('dos.png');
      expect(fallo.textContent).toContain('Formato de imagen no permitido');
      expect(navegar).not.toHaveBeenCalled();
      expect(el().textContent).toContain('La incidencia se ha creado');
      expect(el().textContent).toContain('reintentarlo desde la incidencia');
    });

    it('tras un fallo parcial, "Ver la incidencia" lleva al detalle (donde se reintenta)', async () => {
      fotos.subir.mockReturnValue(throwError(() => http(500)));
      await conFotos('uno.png');
      await pulsarEnviar();

      const enlace = campo<HTMLAnchorElement>('a[data-accion="ver-incidencia"]');
      expect(enlace.getAttribute('href')).toBe('/incidencias/11');
    });

    it('un fallo sin mensaje del backend usa un texto genérico por foto', async () => {
      fotos.subir.mockReturnValue(throwError(() => http(500)));
      await conFotos('uno.png');

      await pulsarEnviar();

      expect(el().querySelector('.subidas li')?.textContent).toContain('No se pudo subir');
    });

    it('después de crear no se vuelve a crear la incidencia ni se puede editar el formulario', async () => {
      fotos.subir.mockReturnValue(throwError(() => http(500)));
      await conFotos('uno.png');
      await pulsarEnviar();

      expect(el().querySelector('button[type="submit"]')).toBeNull();
      expect(incidencias.crear).toHaveBeenCalledTimes(1);
    });
  });
});
