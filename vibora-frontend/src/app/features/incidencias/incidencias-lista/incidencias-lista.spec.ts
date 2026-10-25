import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { ClubService } from '../../../core/api/club.service';
import { AuthService } from '../../../core/auth/auth.service';
import { NotificacionesStore } from '../../../core/notificaciones/notificaciones.store';
import { Club } from '../../../shared/models/club.model';
import { IncidenciaResumen } from '../../../shared/models/incidencia.model';
import { Notificacion, TipoNotificacion } from '../../../shared/models/notificacion.model';
import { Pista } from '../../../shared/models/pista.model';
import { Usuario } from '../../../shared/models/usuario.model';
import {
  USUARIO_ADMIN,
  USUARIO_JUGADOR,
  crearAuthFalso,
  crearNotificacion,
  escribir,
} from '../../../testing/test-utils';
import { PersonalService } from '../../club/personal.service';
import { CLAVE_CLUB_INCIDENCIAS } from '../club-preferido';
import { IncidenciaService } from '../incidencia.service';
import { IncidenciasLista } from './incidencias-lista';

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

function incidencia(id: number, cambios: Partial<IncidenciaResumen> = {}): IncidenciaResumen {
  return {
    id,
    clubId: 5,
    pistaId: 11,
    pistaNombre: 'Pista 1',
    titulo: `Incidencia ${id}`,
    categoria: 'ILUMINACION',
    prioridad: 'MEDIA',
    estado: 'ABIERTA',
    reportadaPor: { id: 1, nombre: 'Ana García' },
    asignadaA: null,
    creadaEn: new Date(Date.now() - 5 * 60_000).toISOString(),
    actualizadaEn: new Date().toISOString(),
    ...cambios,
  };
}

const CLUBES = [club(3, 'Club Tres'), club(5, 'Club Víbora Demo')];
const PISTAS: Pista[] = [
  { id: 11, nombre: 'Pista 1', tipo: 'INTERIOR', activa: true, clubId: 5 },
  { id: 12, nombre: 'Pista 2', tipo: 'EXTERIOR', activa: false, clubId: 5 },
];
const MANTENIMIENTO: Usuario = { ...USUARIO_ADMIN, id: 20, rol: 'MANTENIMIENTO' };

describe('IncidenciasLista', () => {
  let fixture: ComponentFixture<IncidenciasLista>;
  const clubService = { listar: vi.fn(), pistas: vi.fn() };
  const incidencias = { listar: vi.fn() };
  const personal = { listar: vi.fn() };
  let recibidas: Subject<Notificacion>;

  async function crear(usuario: Usuario | null = USUARIO_ADMIN): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [IncidenciasLista],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: crearAuthFalso(usuario) },
        { provide: ClubService, useValue: clubService },
        { provide: IncidenciaService, useValue: incidencias },
        { provide: PersonalService, useValue: personal },
        { provide: NotificacionesStore, useValue: { recibidas$: recibidas.asObservable() } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(IncidenciasLista);
    await fixture.whenStable();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const selectClub = (): HTMLSelectElement =>
    el().querySelector<HTMLSelectElement>('select[data-campo="club"]')!;
  const filas = (): HTMLElement[] => Array.from(el().querySelectorAll<HTMLElement>('.incidencia'));
  const texto = (e: Element | null): string => e?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  const chips = (): HTMLElement[] =>
    Array.from(el().querySelectorAll<HTMLElement>('mat-chip-option'));
  async function elegirChip(indice: number): Promise<void> {
    chips()
      [indice].querySelector<HTMLElement>('button.mdc-evolution-chip__action--primary')!
      .click();
    await fixture.whenStable();
  }

  beforeEach(() => {
    vi.resetAllMocks();
    localStorage.clear();
    recibidas = new Subject<Notificacion>();
    clubService.listar.mockReturnValue(of(CLUBES));
    clubService.pistas.mockReturnValue(of(PISTAS));
    personal.listar.mockReturnValue(
      of([
        { usuarioId: 20, nombre: 'Pepe', apellidos: 'Gil', email: 'p@x.es', rol: 'MANTENIMIENTO' },
        { usuarioId: 21, nombre: 'Lola', apellidos: 'Ruiz', email: 'l@x.es', rol: 'MONITOR' },
      ]),
    );
    incidencias.listar.mockReturnValue(of([incidencia(1), incidencia(2)]));
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    localStorage.clear();
  });

  describe('club inicial', () => {
    it('usa el club del usuario', async () => {
      await crear(USUARIO_ADMIN); // clubId 5

      expect(selectClub().value).toBe('5');
      expect(incidencias.listar).toHaveBeenCalledWith(5, { page: 0, limit: 20 });
    });

    it('sin club propio usa el último club elegido, guardado en localStorage', async () => {
      localStorage.setItem(CLAVE_CLUB_INCIDENCIAS, '3');

      await crear(USUARIO_JUGADOR);

      expect(selectClub().value).toBe('3');
      expect(incidencias.listar).toHaveBeenCalledWith(3, { page: 0, limit: 20 });
    });

    it('sin club propio ni guardado usa el primer club', async () => {
      await crear(USUARIO_JUGADOR);

      expect(selectClub().value).toBe('3');
    });

    it('un club guardado que ya no existe se ignora', async () => {
      localStorage.setItem(CLAVE_CLUB_INCIDENCIAS, '99');

      await crear(USUARIO_JUGADOR);

      expect(selectClub().value).toBe('3');
    });

    it('si localStorage falla al leer o al guardar, funciona igual con el primer club', async () => {
      vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
        throw new Error('bloqueado');
      });
      vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
        throw new Error('cuota');
      });

      await crear(USUARIO_JUGADOR);
      escribir(selectClub(), '5');
      await fixture.whenStable();

      expect(incidencias.listar).toHaveBeenLastCalledWith(5, { page: 0, limit: 20 });
      expect(el().querySelector('[role="alert"]')).toBeNull();
    });

    it('cambiar de club recarga la lista y lo recuerda', async () => {
      await crear(USUARIO_JUGADOR);

      escribir(selectClub(), '5');
      await fixture.whenStable();

      expect(incidencias.listar).toHaveBeenLastCalledWith(5, { page: 0, limit: 20 });
      expect(localStorage.getItem(CLAVE_CLUB_INCIDENCIAS)).toBe('5');
    });

    it('sin clubes lo explica y no pide incidencias', async () => {
      clubService.listar.mockReturnValue(of([]));

      await crear();

      expect(texto(el())).toContain('Todavía no hay clubes');
      expect(incidencias.listar).not.toHaveBeenCalled();
    });

    it('si fallan los clubes muestra el error', async () => {
      clubService.listar.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

      await crear();

      expect(el().querySelector('[role="alert"]')?.textContent).toContain(
        'No se pudieron cargar los clubes',
      );
    });
  });

  describe('listado', () => {
    it('cada incidencia muestra título (enlace al detalle), categoría, prioridad, estado, pista y fecha relativa', async () => {
      incidencias.listar.mockReturnValue(
        of([
          incidencia(7, {
            titulo: 'Foco fundido',
            prioridad: 'URGENTE',
            estado: 'EN_CURSO',
            categoria: 'ILUMINACION',
          }),
        ]),
      );

      await crear();

      const fila = filas()[0];
      const enlace = fila.querySelector<HTMLAnchorElement>('a.titulo')!;
      expect(texto(enlace)).toBe('Foco fundido');
      expect(enlace.getAttribute('href')).toBe('/incidencias/7');
      expect(texto(fila.querySelector('.categoria'))).toBe('Iluminación');
      expect(texto(fila.querySelector('[data-tipo="prioridad"]'))).toBe('Urgente');
      expect(texto(fila.querySelector('[data-tipo="estado"]'))).toBe('En curso');
      expect(texto(fila.querySelector('.pista'))).toBe('Pista 1');
      expect(texto(fila.querySelector('.fecha'))).toBe('hace 5 min');
      expect(fila.querySelector('.fecha')?.getAttribute('title')).toMatch(/de \d{4}, \d{2}:\d{2}$/);
    });

    it('una incidencia sin pista lo indica', async () => {
      incidencias.listar.mockReturnValue(of([incidencia(7, { pistaId: null, pistaNombre: null })]));

      await crear();

      expect(texto(filas()[0].querySelector('.pista'))).toBe('Sin pista concreta');
    });

    it('conserva el orden que devuelve el servidor', async () => {
      incidencias.listar.mockReturnValue(
        of([incidencia(9, { prioridad: 'URGENTE' }), incidencia(2), incidencia(5)]),
      );

      await crear();

      expect(filas().map((f) => texto(f.querySelector('a.titulo')))).toEqual([
        'Incidencia 9',
        'Incidencia 2',
        'Incidencia 5',
      ]);
    });

    it('mientras carga muestra un indicador y no el vacío', async () => {
      incidencias.listar.mockReturnValue(new Subject<IncidenciaResumen[]>());

      await crear();

      expect(el().querySelector('mat-spinner')).not.toBeNull();
      expect(el().querySelector('.vacio')).toBeNull();
    });

    it('si falla la carga muestra el error y "Reintentar" vuelve a pedir', async () => {
      incidencias.listar.mockReturnValueOnce(
        throwError(
          () => new HttpErrorResponse({ status: 500, error: { error: 'Fallo del servidor' } }),
        ),
      );

      await crear();

      expect(el().querySelector('[role="alert"]')?.textContent).toContain('Fallo del servidor');
      incidencias.listar.mockReturnValue(of([incidencia(1)]));
      el().querySelector<HTMLButtonElement>('button[data-accion="reintentar"]')!.click();
      await fixture.whenStable();

      expect(filas()).toHaveLength(1);
      expect(el().querySelector('[role="alert"]')).toBeNull();
    });

    it('el botón "Reportar incidencia" lleva a /incidencias/nueva con el club elegido', async () => {
      await crear();

      const boton = el().querySelector<HTMLAnchorElement>('a[data-accion="reportar"]')!;
      expect(texto(boton)).toContain('Reportar incidencia');
      expect(boton.getAttribute('href')).toBe('/incidencias/nueva?club=5');
    });
  });

  describe('vacío según el rol', () => {
    beforeEach(() => incidencias.listar.mockReturnValue(of([])));

    it('un jugador: "No tienes incidencias en este club"', async () => {
      await crear(USUARIO_JUGADOR);

      expect(texto(el().querySelector('.vacio'))).toContain('No tienes incidencias en este club');
    });

    it('mantenimiento: no tiene ninguna asignada', async () => {
      await crear(MANTENIMIENTO);

      expect(texto(el().querySelector('.vacio'))).toContain(
        'No tienes incidencias asignadas en este club',
      );
    });

    it('el administrador: no hay incidencias en el club', async () => {
      await crear(USUARIO_ADMIN);

      expect(texto(el().querySelector('.vacio'))).toContain('No hay incidencias en este club');
    });

    it('con un filtro activo dice que ninguna coincide', async () => {
      await crear(USUARIO_ADMIN);

      await elegirChip(1); // Abierta

      expect(texto(el().querySelector('.vacio'))).toContain('Ninguna incidencia coincide');
    });
  });

  describe('filtros', () => {
    it('los chips ofrecen Todas y los seis estados en español', async () => {
      await crear();

      expect(chips().map((c) => texto(c))).toEqual([
        'Todas',
        'Abierta',
        'Asignada',
        'En curso',
        'Resuelta',
        'Cerrada',
        'Rechazada',
      ]);
    });

    it('elegir un estado lo envía al servidor y vuelve a la primera página', async () => {
      await crear();

      await elegirChip(3); // En curso

      expect(incidencias.listar).toHaveBeenLastCalledWith(5, {
        estado: 'EN_CURSO',
        page: 0,
        limit: 20,
      });
    });

    it('"Todas" quita el filtro de estado', async () => {
      await crear();
      await elegirChip(2);

      await elegirChip(0);

      expect(incidencias.listar).toHaveBeenLastCalledWith(5, { page: 0, limit: 20 });
    });

    it('el administrador ve los filtros de pista y de asignación; un jugador no', async () => {
      await crear(USUARIO_ADMIN);
      expect(el().querySelector('select[data-campo="pista"]')).not.toBeNull();
      expect(el().querySelector('select[data-campo="asignacion"]')).not.toBeNull();
      expect(clubService.pistas).toHaveBeenCalledWith(5);
      expect(personal.listar).toHaveBeenCalledWith(5);
      TestBed.resetTestingModule();

      await crear(USUARIO_JUGADOR);
      expect(el().querySelector('select[data-campo="pista"]')).toBeNull();
      expect(el().querySelector('select[data-campo="asignacion"]')).toBeNull();
    });

    it('el administrador de OTRO club no ve esos filtros ni pide su personal', async () => {
      await crear(USUARIO_ADMIN);
      personal.listar.mockClear();

      escribir(selectClub(), '3');
      await fixture.whenStable();

      expect(el().querySelector('select[data-campo="pista"]')).toBeNull();
      expect(personal.listar).not.toHaveBeenCalled();
    });

    it('filtrar por pista envía pistaId', async () => {
      await crear();

      escribir(el().querySelector<HTMLSelectElement>('select[data-campo="pista"]')!, '11');
      await fixture.whenStable();

      expect(incidencias.listar).toHaveBeenLastCalledWith(5, { pistaId: 11, page: 0, limit: 20 });
    });

    it('filtrar por una persona de mantenimiento envía asignadaA (solo ofrece a mantenimiento)', async () => {
      await crear();
      const asignacion = el().querySelector<HTMLSelectElement>('select[data-campo="asignacion"]')!;
      const opciones = Array.from(asignacion.options).map((o) => o.textContent!.trim());
      expect(opciones).toEqual(['Todas', 'Sin asignar', 'Pepe Gil']);

      escribir(asignacion, '20');
      await fixture.whenStable();

      expect(incidencias.listar).toHaveBeenLastCalledWith(5, { asignadaA: 20, page: 0, limit: 20 });
    });

    it('"Sin asignar" oculta las que ya tienen responsable', async () => {
      incidencias.listar.mockReturnValue(
        of([incidencia(1), incidencia(2, { asignadaA: { id: 20, nombre: 'Pepe Gil' } })]),
      );
      await crear();

      escribir(el().querySelector<HTMLSelectElement>('select[data-campo="asignacion"]')!, 'SIN');
      await fixture.whenStable();

      expect(filas().map((f) => texto(f.querySelector('a.titulo')))).toEqual(['Incidencia 1']);
    });
  });

  describe('paginación ("Cargar más")', () => {
    const pagina = (desde: number, n: number): IncidenciaResumen[] =>
      Array.from({ length: n }, (_, i) => incidencia(desde + i));

    it('con una página completa ofrece cargar más; pide la siguiente y la añade al final', async () => {
      incidencias.listar.mockReturnValueOnce(of(pagina(1, 20)));
      await crear();
      expect(filas()).toHaveLength(20);

      incidencias.listar.mockReturnValueOnce(of(pagina(21, 5)));
      el().querySelector<HTMLButtonElement>('button[data-accion="mas"]')!.click();
      await fixture.whenStable();

      expect(incidencias.listar).toHaveBeenLastCalledWith(5, { page: 1, limit: 20 });
      expect(filas()).toHaveLength(25);
      expect(texto(filas()[24].querySelector('a.titulo'))).toBe('Incidencia 25');
      expect(el().querySelector('button[data-accion="mas"]')).toBeNull(); // última página
    });

    it('con una página incompleta no ofrece cargar más', async () => {
      incidencias.listar.mockReturnValue(of(pagina(1, 19)));

      await crear();

      expect(el().querySelector('button[data-accion="mas"]')).toBeNull();
    });

    it('cambiar un filtro vuelve a la primera página y descarta lo cargado', async () => {
      incidencias.listar
        .mockReturnValueOnce(of(pagina(1, 20)))
        .mockReturnValueOnce(of(pagina(21, 20)));
      await crear();
      el().querySelector<HTMLButtonElement>('button[data-accion="mas"]')!.click();
      await fixture.whenStable();
      expect(filas()).toHaveLength(40);

      incidencias.listar.mockReturnValue(of(pagina(100, 2)));
      await elegirChip(1);

      expect(filas()).toHaveLength(2);
      expect(incidencias.listar).toHaveBeenLastCalledWith(5, {
        estado: 'ABIERTA',
        page: 0,
        limit: 20,
      });
    });
  });

  describe('"Cargar más" cancelado o fallido', () => {
    const pagina = (desde: number, n: number): IncidenciaResumen[] =>
      Array.from({ length: n }, (_, i) => incidencia(desde + i));
    const botonMas = (): HTMLButtonElement =>
      el().querySelector<HTMLButtonElement>('button[data-accion="mas"]')!;

    /** Página completa en pantalla y una petición "Cargar más" colgada (sin respuesta). */
    async function conMasPendiente(): Promise<Subject<IncidenciaResumen[]>> {
      incidencias.listar.mockReturnValueOnce(of(pagina(1, 20)));
      await crear();
      const pendiente = new Subject<IncidenciaResumen[]>();
      incidencias.listar.mockReturnValueOnce(pendiente);
      botonMas().click();
      fixture.detectChanges();
      expect(botonMas().disabled).toBe(true);
      expect(texto(botonMas())).toBe('Cargando…');
      return pendiente;
    }

    function esperarBotonLibre(): void {
      fixture.detectChanges();
      expect(botonMas().disabled).toBe(false);
      expect(texto(botonMas())).toBe('Cargar más');
    }

    it('una recarga silenciosa (push) que sustituye a "Cargar más" libera el botón', async () => {
      await conMasPendiente();
      vi.useFakeTimers();
      incidencias.listar.mockReturnValue(of(pagina(1, 20)));

      recibidas.next(crearNotificacion(1, { tipo: 'INCIDENCIA_ESTADO' }));
      vi.advanceTimersByTime(300);

      esperarBotonLibre();
    });

    it('un cambio de filtro que sustituye a "Cargar más" libera el botón', async () => {
      await conMasPendiente();
      incidencias.listar.mockReturnValue(of(pagina(100, 20)));

      await elegirChip(1);

      esperarBotonLibre();
    });

    it('un cambio de club que sustituye a "Cargar más" libera el botón', async () => {
      await conMasPendiente();
      incidencias.listar.mockReturnValue(of(pagina(200, 20)));

      escribir(selectClub(), '3');
      await fixture.whenStable();

      esperarBotonLibre();
    });

    it('tras liberarlo se puede volver a pulsar "Cargar más"', async () => {
      await conMasPendiente();
      incidencias.listar.mockReturnValue(of(pagina(100, 20)));
      await elegirChip(1);
      incidencias.listar.mockClear();
      incidencias.listar.mockReturnValue(of(pagina(120, 3)));

      botonMas().click();
      await fixture.whenStable();

      expect(incidencias.listar).toHaveBeenCalledWith(5, { estado: 'ABIERTA', page: 1, limit: 20 });
      expect(filas()).toHaveLength(23);
    });

    it('el flujo normal sigue añadiendo y limpia la marca de carga', async () => {
      const pendiente = await conMasPendiente();

      pendiente.next(pagina(21, 20)); // otra página completa: el botón sigue y vuelve a estar libre
      fixture.detectChanges();

      expect(filas()).toHaveLength(40);
      esperarBotonLibre();
    });

    it('un error al cargar más libera el botón y conserva lo cargado', async () => {
      const pendiente = await conMasPendiente();

      pendiente.error(new HttpErrorResponse({ status: 500 }));
      esperarBotonLibre();

      expect(filas()).toHaveLength(20);
    });
  });

  describe('actualización en tiempo real', () => {
    const push =(tipo: TipoNotificacion): void =>
      recibidas.next(crearNotificacion(Math.floor(Math.random() * 1e6), { tipo }));

    /** Crea la página con reloj real (whenStable lo necesita) y luego congela el tiempo para el debounce. */
    async function arrancar(): Promise<void> {
      await crear();
      vi.useFakeTimers();
      incidencias.listar.mockClear();
    }

    it.each<TipoNotificacion>([
      'INCIDENCIA_NUEVA',
      'INCIDENCIA_ASIGNADA',
      'INCIDENCIA_ESTADO',
      'INCIDENCIA_COMENTARIO',
    ])('recarga en silencio al llegar %s', async (tipo) => {
      await arrancar();

      push(tipo);
      expect(incidencias.listar).not.toHaveBeenCalled(); // espera al debounce
      vi.advanceTimersByTime(300);

      expect(incidencias.listar).toHaveBeenCalledTimes(1);
      expect(incidencias.listar).toHaveBeenCalledWith(5, { page: 0, limit: 20 });
    });

    it.each<TipoNotificacion>(['CARGO_NUEVO', 'JUGADOR_SE_APUNTO', 'PERSONAL_ANADIDO'])(
      'ignora %s: no cambia ninguna incidencia',
      async (tipo) => {
        await arrancar();

        push(tipo);
        vi.advanceTimersByTime(1000);

        expect(incidencias.listar).not.toHaveBeenCalled();
      },
    );

    it('agrupa ráfagas: diez push seguidos hacen una sola recarga', async () => {
      await arrancar();

      for (let i = 0; i < 10; i++) {
        push('INCIDENCIA_ESTADO');
        vi.advanceTimersByTime(50);
      }
      expect(incidencias.listar).not.toHaveBeenCalled();
      vi.advanceTimersByTime(300);

      expect(incidencias.listar).toHaveBeenCalledTimes(1);
    });

    it('la recarga es silenciosa: sin spinner, con el filtro elegido y la lista a la vista', async () => {
      await crear();
      await elegirChip(1); // Abierta
      vi.useFakeTimers();
      const pendiente = new Subject<IncidenciaResumen[]>();
      incidencias.listar.mockReturnValue(pendiente);

      push('INCIDENCIA_NUEVA');
      vi.advanceTimersByTime(300);
      fixture.detectChanges();

      expect(incidencias.listar).toHaveBeenLastCalledWith(5, {
        estado: 'ABIERTA',
        page: 0,
        limit: 20,
      });
      expect(el().querySelector('mat-spinner')).toBeNull();
      expect(filas()).toHaveLength(2);

      pendiente.next([incidencia(1), incidencia(2), incidencia(3)]);
      fixture.detectChanges();
      expect(filas()).toHaveLength(3);
    });

    it('un fallo de la recarga silenciosa no sustituye la lista por un error', async () => {
      await arrancar();
      incidencias.listar.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

      push('INCIDENCIA_ESTADO');
      vi.advanceTimersByTime(300);
      fixture.detectChanges();

      expect(el().querySelector('[role="alert"]')).toBeNull();
      expect(filas()).toHaveLength(2);
    });

    it('al destruir la página deja de escuchar: no recarga aunque llegue un push', async () => {
      await arrancar();

      push('INCIDENCIA_NUEVA'); // pendiente de debounce
      fixture.destroy();
      vi.advanceTimersByTime(300);
      push('INCIDENCIA_ESTADO');
      vi.advanceTimersByTime(300);

      expect(incidencias.listar).not.toHaveBeenCalled();
      expect(recibidas.observed).toBe(false);
    });
  });
});
