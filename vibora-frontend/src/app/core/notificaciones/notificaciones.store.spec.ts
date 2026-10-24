import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { Notificacion } from '../../shared/models/notificacion.model';
import { crearAuthFalso, crearNotificacion } from '../../testing/test-utils';
import { AuthService } from '../auth/auth.service';
import { NotificacionService } from './notificacion.service';
import { NotificacionesStore } from './notificaciones.store';

describe('NotificacionesStore', () => {
  const servicio = {
    listar: vi.fn(),
    contarNoLeidas: vi.fn(),
    leer: vi.fn(),
    leerTodas: vi.fn(),
  };
  let auth: ReturnType<typeof crearAuthFalso>;

  const INICIALES: Notificacion[] = [
    crearNotificacion(3),
    crearNotificacion(2),
    crearNotificacion(1, { leida: true }),
  ];

  function crear(conSesion = true): NotificacionesStore {
    auth = crearAuthFalso(conSesion ? undefined : null);
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: NotificacionService, useValue: servicio },
      ],
    });
    const store = TestBed.inject(NotificacionesStore);
    TestBed.tick(); // ejecuta el efecto que reacciona a la sesión
    return store;
  }

  beforeEach(() => {
    vi.resetAllMocks();
    servicio.listar.mockReturnValue(of(INICIALES));
    servicio.contarNoLeidas.mockReturnValue(of(2));
    servicio.leer.mockReturnValue(of(crearNotificacion(3, { leida: true })));
    servicio.leerTodas.mockReturnValue(of(2));
  });

  it('sin sesión no pide nada y está vacío', () => {
    const store = crear(false);

    expect(servicio.listar).not.toHaveBeenCalled();
    expect(servicio.contarNoLeidas).not.toHaveBeenCalled();
    expect(store.items()).toEqual([]);
    expect(store.noLeidas()).toBe(0);
  });

  it('al haber sesión carga las 20 últimas y el contador de no leídas', () => {
    const store = crear();

    expect(servicio.listar).toHaveBeenCalledWith({ limit: 20 });
    expect(servicio.contarNoLeidas).toHaveBeenCalledTimes(1);
    expect(store.items()).toEqual(INICIALES);
    expect(store.noLeidas()).toBe(2);
  });

  it('carga cuando la sesión empieza después de crear el store', () => {
    const store = crear(false);

    auth.token.set('otro-jwt');
    TestBed.tick();

    expect(store.items()).toEqual(INICIALES);
    expect(store.noLeidas()).toBe(2);
  });

  it('al cerrar sesión vacía la lista y el contador', () => {
    const store = crear();

    auth.token.set(null);
    TestBed.tick();

    expect(store.items()).toEqual([]);
    expect(store.noLeidas()).toBe(0);
  });

  it('ignora una respuesta que llega después de cerrar sesión', () => {
    const lista = new Subject<Notificacion[]>();
    servicio.listar.mockReturnValue(lista);
    const store = crear();

    auth.token.set(null);
    TestBed.tick();
    lista.next(INICIALES);
    lista.complete();

    expect(store.items()).toEqual([]);
  });

  it('si falla la recarga conserva lo que había', () => {
    const store = crear();
    servicio.listar.mockReturnValue(throwError(() => new Error('red')));

    store.recargar();

    expect(store.items()).toEqual(INICIALES);
    expect(store.noLeidas()).toBe(2);
  });

  describe('recibir (push)', () => {
    it('antepone la nueva y suma una no leída', () => {
      const store = crear();

      store.recibir(crearNotificacion(10));

      expect(store.items()[0].id).toBe(10);
      expect(store.items()).toHaveLength(4);
      expect(store.noLeidas()).toBe(3);
    });

    it('acepta los tipos de personal e incidencias', () => {
      const store = crear();

      store.recibir(crearNotificacion(10, { tipo: 'INCIDENCIA_NUEVA' }));
      store.recibir(crearNotificacion(11, { tipo: 'PERSONAL_QUITADO' }));

      expect(store.items().map((n) => n.id)).toEqual([11, 10, 3, 2, 1]);
      expect(store.noLeidas()).toBe(4);
    });

    it('descarta duplicados por id sin volver a contar', () => {
      const store = crear();

      store.recibir(crearNotificacion(10));
      store.recibir(crearNotificacion(10));
      store.recibir(crearNotificacion(3)); // ya estaba cargada por REST

      expect(store.items().map((n) => n.id)).toEqual([10, 3, 2, 1]);
      expect(store.noLeidas()).toBe(3);
    });

    it('emite cada notificación empujada en recibidas$ para que las páginas reaccionen', () => {
      const store = crear();
      const vistas: number[] = [];
      store.recibidas$.subscribe((n) => vistas.push(n.id));

      store.recibir(crearNotificacion(10));
      store.recibir(crearNotificacion(11, { tipo: 'PARTIDO_COMPLETO' }));

      expect(vistas).toEqual([10, 11]);
    });

    it('recargar (REST) no emite en recibidas$: solo cuentan los push', () => {
      const store = crear();
      const vistas: number[] = [];
      store.recibidas$.subscribe((n) => vistas.push(n.id));

      store.recargar();

      expect(vistas).toEqual([]);
    });

    it('no pasa de 20 elementos en la lista', () => {
      servicio.listar.mockReturnValue(
        of(Array.from({ length: 20 }, (_, i) => crearNotificacion(i + 1))),
      );
      const store = crear();

      store.recibir(crearNotificacion(99));

      expect(store.items()).toHaveLength(20);
      expect(store.items()[0].id).toBe(99);
    });
  });

  describe('marcarLeida', () => {
    it('es optimista: marca la notificación y resta una antes de que responda el servidor', () => {
      const respuesta = new Subject<Notificacion>();
      servicio.leer.mockReturnValue(respuesta);
      const store = crear();

      store.marcarLeida(3);

      expect(servicio.leer).toHaveBeenCalledWith(3);
      expect(store.items()[0].leida).toBe(true);
      expect(store.noLeidas()).toBe(1);
    });

    it('si falla, deshace el cambio', () => {
      servicio.leer.mockReturnValue(throwError(() => new Error('500')));
      const store = crear();

      store.marcarLeida(3);

      expect(store.items()[0].leida).toBe(false);
      expect(store.noLeidas()).toBe(2);
    });

    it('si ya estaba leída no hace nada', () => {
      const store = crear();

      store.marcarLeida(1);

      expect(servicio.leer).not.toHaveBeenCalled();
      expect(store.noLeidas()).toBe(2);
    });

    it('nunca deja el contador por debajo de cero', () => {
      servicio.contarNoLeidas.mockReturnValue(of(0));
      const store = crear();

      store.marcarLeida(3);

      expect(store.noLeidas()).toBe(0);
    });
  });

  describe('marcarTodas', () => {
    it('marca todas y deja el contador a cero', () => {
      const store = crear();

      store.marcarTodas();

      expect(servicio.leerTodas).toHaveBeenCalledTimes(1);
      expect(store.items().every((n) => n.leida)).toBe(true);
      expect(store.noLeidas()).toBe(0);
    });

    it('si falla, restaura lista y contador', () => {
      servicio.leerTodas.mockReturnValue(throwError(() => new Error('500')));
      const store = crear();

      store.marcarTodas();

      expect(store.items()).toEqual(INICIALES);
      expect(store.noLeidas()).toBe(2);
    });
  });

  it('recargar sustituye la lista y el contador por lo que dice el servidor', () => {
    const store = crear();
    servicio.listar.mockReturnValue(of([crearNotificacion(50)]));
    servicio.contarNoLeidas.mockReturnValue(of(7));

    store.recargar();

    expect(store.items().map((n) => n.id)).toEqual([50]);
    expect(store.noLeidas()).toBe(7);
  });
});
