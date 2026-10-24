import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { NEVER, of } from 'rxjs';
import { TipoNotificacion } from '../../shared/models/notificacion.model';
import { crearAuthFalso, crearNotificacion } from '../../testing/test-utils';
import { AuthService } from '../auth/auth.service';
import {
  ClienteTiempoReal,
  FABRICA_CLIENTE_TIEMPO_REAL,
  OpcionesClienteTiempoReal,
} from './cliente-tiempo-real';
import { NotificacionesStore } from './notificaciones.store';
import { TiempoRealService } from './tiempo-real.service';

describe('TiempoRealService', () => {
  let auth: ReturnType<typeof crearAuthFalso>;
  let clientes: { activar: ReturnType<typeof vi.fn>; desactivar: ReturnType<typeof vi.fn> }[];
  let opciones: OpcionesClienteTiempoReal[];
  let liberarFabrica: (() => void) | null;
  const store = { recargar: vi.fn(), recibir: vi.fn(), marcarLeida: vi.fn() };
  const router = { navigateByUrl: vi.fn(), navigate: vi.fn(), url: '/deudas' };
  const snack = { open: vi.fn() };
  const fabrica = vi.fn();

  function crear(conSesion = true, fabricaLenta = false): TiempoRealService {
    auth = crearAuthFalso(conSesion ? undefined : null);
    auth.logout.mockImplementation(() => auth.token.set(null));
    fabrica.mockImplementation((op: OpcionesClienteTiempoReal): Promise<ClienteTiempoReal> => {
      opciones.push(op);
      const cliente = { activar: vi.fn(), desactivar: vi.fn().mockResolvedValue(undefined) };
      clientes.push(cliente);
      if (!fabricaLenta) {
        return Promise.resolve(cliente);
      }
      return new Promise((resolver) => {
        liberarFabrica = () => resolver(cliente);
      });
    });
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: NotificacionesStore, useValue: store },
        { provide: Router, useValue: router },
        { provide: MatSnackBar, useValue: snack },
        { provide: FABRICA_CLIENTE_TIEMPO_REAL, useValue: fabrica },
      ],
    });
    const servicio = TestBed.inject(TiempoRealService);
    TestBed.tick();
    return servicio;
  }

  const asentar = (): Promise<void> => new Promise((resolver) => setTimeout(resolver, 0));

  beforeEach(() => {
    vi.resetAllMocks();
    clientes = [];
    opciones = [];
    liberarFabrica = null;
    snack.open.mockReturnValue({ onAction: () => of(undefined) });
  });

  it('sin sesión no abre ninguna conexión', async () => {
    crear(false);
    await asentar();

    expect(fabrica).not.toHaveBeenCalled();
  });

  it('con sesión conecta a /ws con la cabecera Authorization y se suscribe a la cola del usuario', async () => {
    crear();
    await asentar();

    expect(fabrica).toHaveBeenCalledTimes(1);
    expect(opciones[0].url).toMatch(/^wss?:\/\/[^/]+\/ws$/);
    expect(opciones[0].cabeceras).toEqual({ Authorization: 'Bearer jwt-de-prueba' });
    expect(opciones[0].destino).toBe('/user/queue/notificaciones');
    expect(clientes[0].activar).toHaveBeenCalledTimes(1);
  });

  it('conecta cuando la sesión empieza más tarde', async () => {
    crear(false);
    auth.token.set('jwt-nuevo');
    TestBed.tick();
    await asentar();

    expect(opciones[0].cabeceras).toEqual({ Authorization: 'Bearer jwt-nuevo' });
  });

  it('en cada (re)conexión vuelve a pedir las notificaciones por REST', async () => {
    crear();
    await asentar();

    opciones[0].alConectar();
    opciones[0].alConectar(); // reconexión automática de la librería

    expect(store.recargar).toHaveBeenCalledTimes(2);
  });

  describe('mensaje empujado', () => {
    const push = crearNotificacion(42, { tipo: 'CARGO_NUEVO', mensaje: 'Tienes un cargo nuevo' });

    it('lo entrega al store y avisa con un snackbar con la acción "Ver"', async () => {
      crear();
      await asentar();

      opciones[0].alMensaje(JSON.stringify(push));

      expect(store.recibir).toHaveBeenCalledWith(push);
      expect(snack.open).toHaveBeenCalledTimes(1);
      expect(snack.open.mock.calls[0][0]).toBe('Tienes un cargo nuevo');
      expect(snack.open.mock.calls[0][1]).toBe('Ver');
    });

    it('al pulsar "Ver" navega según el tipo y la marca como leída', async () => {
      crear();
      await asentar();

      opciones[0].alMensaje(JSON.stringify(push));

      expect(router.navigateByUrl).toHaveBeenCalledWith('/deudas');
      expect(store.marcarLeida).toHaveBeenCalledWith(42);
    });

    it.each([
      'PERSONAL_ANADIDO',
      'PERSONAL_QUITADO',
      'INCIDENCIA_NUEVA',
      'INCIDENCIA_ASIGNADA',
      'INCIDENCIA_ESTADO',
      'INCIDENCIA_COMENTARIO',
      'FACTURA_RECTIFICATIVA',
    ] as TipoNotificacion[])('un push %s se entrega y "Ver" navega y marca leída', async (tipo) => {
      // como el Router real: una URL que no es string revienta
      router.navigateByUrl.mockImplementation((url: unknown) => {
        if (typeof url !== 'string') {
          throw new TypeError('destino inválido');
        }
        return Promise.resolve(true);
      });
      crear();
      await asentar();
      const nueva = crearNotificacion(77, { tipo });

      opciones[0].alMensaje(JSON.stringify(nueva));

      expect(store.recibir).toHaveBeenCalledWith(nueva);
      expect(router.navigateByUrl).toHaveBeenCalledWith('/agenda');
      expect(store.marcarLeida).toHaveBeenCalledWith(77);
    });

    it('si no se pulsa "Ver" no navega ni marca nada', async () => {
      snack.open.mockReturnValue({ onAction: () => NEVER });
      crear();
      await asentar();

      opciones[0].alMensaje(JSON.stringify(push));

      expect(router.navigateByUrl).not.toHaveBeenCalled();
      expect(store.marcarLeida).not.toHaveBeenCalled();
    });

    it('ignora cuerpos que no son una notificación válida', async () => {
      crear();
      await asentar();

      opciones[0].alMensaje('esto no es json');
      opciones[0].alMensaje('{"id":"x"}');
      opciones[0].alMensaje('null');

      expect(store.recibir).not.toHaveBeenCalled();
      expect(snack.open).not.toHaveBeenCalled();
    });
  });

  describe('cierre de sesión', () => {
    it('al hacer logout desconecta', async () => {
      crear();
      await asentar();

      auth.token.set(null);
      TestBed.tick();

      expect(clientes[0].desactivar).toHaveBeenCalledTimes(1);
    });

    it('un nuevo login abre una conexión nueva con el token nuevo', async () => {
      crear();
      await asentar();
      auth.token.set(null);
      TestBed.tick();

      auth.token.set('jwt-2');
      TestBed.tick();
      await asentar();

      expect(fabrica).toHaveBeenCalledTimes(2);
      expect(opciones[1].cabeceras).toEqual({ Authorization: 'Bearer jwt-2' });
    });

    it('si cierra sesión antes de que la librería cargue, no llega a activar el cliente', async () => {
      crear(true, true);
      auth.token.set(null);
      TestBed.tick();

      liberarFabrica!();
      await asentar();

      expect(clientes[0].activar).not.toHaveBeenCalled();
      expect(clientes[0].desactivar).toHaveBeenCalledTimes(1);
    });

    it('ignora eventos de una conexión que ya se descartó', async () => {
      crear();
      await asentar();
      const viejas = opciones[0];
      auth.token.set(null);
      TestBed.tick();

      viejas.alConectar();
      viejas.alMensaje(JSON.stringify(crearNotificacion(5)));

      expect(store.recargar).not.toHaveBeenCalled();
      expect(store.recibir).not.toHaveBeenCalled();
    });
  });

  describe('token rechazado por el servidor', () => {
    it('un ERROR "No autenticado" cierra la sesión, desconecta y lleva a /login sin reconectar', async () => {
      crear();
      await asentar();

      opciones[0].alErrorStomp('No autenticado');
      TestBed.tick();
      await asentar();

      expect(clientes[0].desactivar).toHaveBeenCalled();
      expect(auth.logout).toHaveBeenCalledTimes(1);
      expect(router.navigate).toHaveBeenCalledWith(['/login'], {
        queryParams: { redirectTo: '/deudas' },
      });
      expect(fabrica).toHaveBeenCalledTimes(1);
    });

    it('otro tipo de error STOMP no cierra la sesión', async () => {
      crear();
      await asentar();

      opciones[0].alErrorStomp('Acceso denegado');

      expect(auth.logout).not.toHaveBeenCalled();
      expect(clientes[0].desactivar).not.toHaveBeenCalled();
    });
  });

  it('nunca escribe el token en la consola', async () => {
    const espias = (['log', 'info', 'warn', 'error', 'debug'] as const).map((m) =>
      vi.spyOn(console, m).mockImplementation(() => undefined),
    );
    crear();
    await asentar();
    opciones[0].alConectar();
    opciones[0].alErrorStomp('Acceso denegado');

    for (const espia of espias) {
      expect(JSON.stringify(espia.mock.calls)).not.toContain('jwt-de-prueba');
    }
    vi.restoreAllMocks();
  });
});
