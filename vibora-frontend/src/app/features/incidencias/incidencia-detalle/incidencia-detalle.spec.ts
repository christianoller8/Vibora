import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { ClubService } from '../../../core/api/club.service';
import { AuthService } from '../../../core/auth/auth.service';
import { NotificacionesStore } from '../../../core/notificaciones/notificaciones.store';
import { ConfirmDialog } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { BloqueoCreado, BloqueoPista, ReservaAfectada } from '../../../shared/models/bloqueo.model';
import {
  EventoIncidencia,
  FotoIncidencia,
  IncidenciaDetalle,
  TipoEventoIncidencia,
} from '../../../shared/models/incidencia.model';
import { Notificacion, TipoNotificacion } from '../../../shared/models/notificacion.model';
import { Usuario } from '../../../shared/models/usuario.model';
import {
  USUARIO_ADMIN,
  USUARIO_JUGADOR,
  crearAuthFalso,
  crearImagen,
  crearNotificacion,
  elegirArchivos,
  escribir,
  simularObjectUrls,
} from '../../../testing/test-utils';
import { BloqueoService } from '../../club/bloqueo.service';
import { AfectadasDialog } from '../../club/bloqueos-club/afectadas-dialog';
import { AsignarDialog } from '../asignar-dialog/asignar-dialog';
import { EstadoDialog } from '../estado-dialog/estado-dialog';
import { FotoIncidenciaService } from '../foto-incidencia.service';
import { FotoVisorDialog } from '../foto-visor-dialog/foto-visor-dialog';
import { IncidenciaService } from '../incidencia.service';
import { DetalleIncidencia as PaginaDetalle } from './incidencia-detalle';

const MANTENIMIENTO: Usuario = {
  ...USUARIO_ADMIN,
  id: 20,
  email: 'pepe@club.es',
  nombre: 'Pepe',
  apellidos: 'Gil',
  rol: 'MANTENIMIENTO',
};
// ni reportó la incidencia ni subió ninguna de las fotos del test (la 2 la sube el usuario 2)
const OTRO_JUGADOR: Usuario = { ...USUARIO_JUGADOR, id: 3, email: 'eva@x.es', nombre: 'Eva' };

function evento(
  id: number,
  tipo: TipoEventoIncidencia,
  c: Partial<EventoIncidencia> = {},
): EventoIncidencia {
  return {
    id,
    tipo,
    usuario: { id: 1, nombre: 'Ana García López' },
    estadoAnterior: null,
    estadoNuevo: null,
    texto: null,
    creadoEn: '2026-07-15T16:00:00Z',
    ...c,
  };
}

function foto(id: number, c: Partial<FotoIncidencia> = {}): FotoIncidencia {
  return {
    id,
    nombreOriginal: `foto${id}.png`,
    tipoContenido: 'image/png',
    tamano: 1000,
    creadaEn: '2026-07-15T16:05:00Z',
    subidaPor: { id: 1, nombre: 'Ana García López' },
    ...c,
  };
}

function detalle(c: Partial<IncidenciaDetalle> = {}): IncidenciaDetalle {
  return {
    id: 7,
    clubId: 5,
    pistaId: 11,
    pistaNombre: 'Pista 1',
    titulo: 'Foco fundido',
    descripcion: 'No se ve nada por la noche',
    categoria: 'ILUMINACION',
    prioridad: 'MEDIA',
    estado: 'ABIERTA',
    reportadaPor: { id: 1, nombre: 'Ana García López' },
    asignadaA: null,
    creadaEn: '2026-07-15T16:00:00Z',
    actualizadaEn: '2026-07-15T17:30:00Z',
    resueltaEn: null,
    eventos: [evento(1, 'CREADA', { estadoNuevo: 'ABIERTA' })],
    fotos: [],
    bloqueoActivo: null,
    ...c,
  };
}

const http = (status: number, error?: unknown): HttpErrorResponse =>
  new HttpErrorResponse({ status, error });
const CONFLICTO = http(409, { error: 'La incidencia fue modificada por otra persona, recarga' });

describe('IncidenciaDetalle (página)', () => {
  let fixture: ComponentFixture<PaginaDetalle>;
  let urls: ReturnType<typeof simularObjectUrls>;
  let recibidas: Subject<Notificacion>;
  const incidencias = { obtener: vi.fn(), comentar: vi.fn() };
  const fotosApi = { subir: vi.fn(), eliminar: vi.fn(), obtenerBlob: vi.fn() };
  const clubService = { obtener: vi.fn() };
  const bloqueosApi = {
    bloquearPistaDeIncidencia: vi.fn(),
    afectadas: vi.fn(),
    levantar: vi.fn(),
    cancelarAfectadas: vi.fn(),
  };
  const dialogo = { open: vi.fn() };
  const snack = { open: vi.fn() };

  async function crear(usuario: Usuario | null = USUARIO_JUGADOR, id = '7'): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [PaginaDetalle],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: crearAuthFalso(usuario) },
        { provide: IncidenciaService, useValue: incidencias },
        { provide: FotoIncidenciaService, useValue: fotosApi },
        { provide: BloqueoService, useValue: bloqueosApi },
        { provide: ClubService, useValue: clubService },
        { provide: MatDialog, useValue: dialogo },
        { provide: MatSnackBar, useValue: snack },
        { provide: NotificacionesStore, useValue: { recibidas$: recibidas.asObservable() } },
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id })) } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(PaginaDetalle);
    await fixture.whenStable();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const q = <T extends Element>(s: string): T | null => el().querySelector<T>(s);
  const texto = (e: Element | null): string => e?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  const accion = (nombre: string): HTMLButtonElement | null =>
    q<HTMLButtonElement>(`button[data-accion="${nombre}"]`);
  const acciones = (): string[] =>
    Array.from(el().querySelectorAll('.acciones button')).map((b) =>
      b.getAttribute('data-accion')!,
    );
  const miniaturas = (): HTMLElement[] => Array.from(el().querySelectorAll<HTMLElement>('.foto'));

  /** Lo que el diálogo devuelve al cerrarse. */
  const cierraCon = (resultado: unknown): void => {
    dialogo.open.mockReturnValue({ afterClosed: () => of(resultado) });
  };

  beforeEach(() => {
    vi.resetAllMocks();
    urls = simularObjectUrls();
    recibidas = new Subject<Notificacion>();
    incidencias.obtener.mockReturnValue(of(detalle()));
    fotosApi.obtenerBlob.mockImplementation(() => of(new Blob([new Uint8Array([1])])));
    clubService.obtener.mockReturnValue(
      of({
        id: 5,
        nombre: 'Club Víbora Demo',
        direccion: null,
        ciudad: null,
        horaApertura: '08:00:00',
        horaCierre: '22:00:00',
      }),
    );
    dialogo.open.mockReturnValue({ afterClosed: () => of(undefined) });
  });
  afterEach(() => {
    vi.useRealTimers();
    urls.restaurar();
  });

  describe('cabecera', () => {
    it('pide la incidencia por el id de la ruta y mientras tanto muestra un indicador', async () => {
      incidencias.obtener.mockReturnValue(new Subject<IncidenciaDetalle>());

      await crear();

      expect(incidencias.obtener).toHaveBeenCalledWith(7);
      expect(q('mat-spinner')).not.toBeNull();
      expect(q('h1')).toBeNull();
    });

    it('muestra título, estado, prioridad, categoría, pista, club, reportante y descripción', async () => {
      incidencias.obtener.mockReturnValue(
        of(
          detalle({
            prioridad: 'URGENTE',
            estado: 'EN_CURSO',
            asignadaA: { id: 20, nombre: 'Pepe Gil' },
          }),
        ),
      );

      await crear();

      expect(texto(q('h1'))).toBe('Foco fundido');
      expect(texto(q('[data-tipo="estado"]'))).toBe('En curso');
      expect(texto(q('[data-tipo="prioridad"]'))).toBe('Urgente');
      expect(texto(q('.meta'))).toContain('Iluminación');
      expect(texto(q('.meta'))).toContain('Pista 1');
      expect(texto(q('.meta'))).toContain('Club Víbora Demo');
      expect(texto(q('.meta'))).toContain('Ana García López');
      expect(texto(q('.meta'))).toContain('Pepe Gil');
      expect(texto(q('.descripcion'))).toBe('No se ve nada por la noche');
      expect(clubService.obtener).toHaveBeenCalledWith(5);
    });

    it('las fechas salen en hora de Madrid', async () => {
      await crear();

      expect(texto(q('.meta'))).toContain('15 de julio de 2026, 18:00'); // 16:00 UTC en verano
      expect(texto(q('.meta'))).toContain('15 de julio de 2026, 19:30');
    });

    it('sin pista ni asignado lo dice; si no se puede leer el club no rompe la página', async () => {
      incidencias.obtener.mockReturnValue(of(detalle({ pistaId: null, pistaNombre: null })));
      clubService.obtener.mockReturnValue(throwError(() => http(403)));

      await crear();

      expect(texto(q('.meta'))).toContain('Sin pista concreta');
      expect(texto(q('.meta'))).toContain('Sin asignar');
      expect(texto(q('h1'))).toBe('Foco fundido');
    });

    it('si no existe (404) muestra el mensaje y permite reintentar', async () => {
      incidencias.obtener.mockReturnValueOnce(
        throwError(() => http(404, { error: 'Incidencia no encontrada' })),
      );

      await crear();

      expect(q('[role="alert"]')?.textContent).toContain('Incidencia no encontrada');
      incidencias.obtener.mockReturnValue(of(detalle()));
      accion('reintentar')!.click();
      await fixture.whenStable();
      expect(texto(q('h1'))).toBe('Foco fundido');
    });

    it('un id no numérico no llama al servidor', async () => {
      await crear(USUARIO_JUGADOR, 'abc');

      expect(incidencias.obtener).not.toHaveBeenCalled();
      expect(q('[role="alert"]')?.textContent).toContain('no es válida');
    });
  });

  describe('fotos', () => {
    it('muestra una miniatura por foto, cargada con blob y con botón accesible para abrirla', async () => {
      incidencias.obtener.mockReturnValue(of(detalle({ fotos: [foto(1), foto(2)] })));

      await crear();

      expect(miniaturas()).toHaveLength(2);
      expect(fotosApi.obtenerBlob).toHaveBeenCalledWith(7, 1);
      expect(fotosApi.obtenerBlob).toHaveBeenCalledWith(7, 2);
      expect(q('.foto img')?.getAttribute('src')).toMatch(/^blob:prueba\//);
      expect(q('.foto img')?.getAttribute('alt')).toBe('Foto de la incidencia: foto1.png');
      const abrir = q<HTMLButtonElement>('.foto button[data-accion="abrir-foto"]')!;
      expect(abrir.getAttribute('aria-label')).toBe('Ver la foto foto1.png');
    });

    it('sin fotos no pinta la galería', async () => {
      await crear();

      expect(q('.fotos')).toBeNull();
    });

    it('pulsar una miniatura abre el visor en esa foto, con todas las fotos', async () => {
      const fotos = [foto(1), foto(2)];
      incidencias.obtener.mockReturnValue(of(detalle({ fotos })));
      await crear();

      el().querySelectorAll<HTMLButtonElement>('button[data-accion="abrir-foto"]')[1].click();

      const [componente, config] = dialogo.open.mock.calls[0];
      expect(componente).toBe(FotoVisorDialog);
      expect(config.data).toMatchObject({ incidenciaId: 7, fotos, indice: 1 });
      expect(config.ariaLabel ?? config.data.incidenciaId).toBeDefined();
    });

    it('la foto la borra quien la subió (con confirmación) y el administrador; nadie más', async () => {
      incidencias.obtener.mockReturnValue(
        of(detalle({ fotos: [foto(1), foto(2, { subidaPor: { id: 2, nombre: 'Luis' } })] })),
      );

      await crear(USUARIO_JUGADOR); // sube la 1, no la 2
      expect(el().querySelectorAll('button[data-accion="eliminar-foto"]')).toHaveLength(1);
      expect(q('.foto button[data-accion="eliminar-foto"]')?.getAttribute('aria-label')).toBe(
        'Eliminar la foto foto1.png',
      );
      TestBed.resetTestingModule();

      await crear(USUARIO_ADMIN);
      expect(el().querySelectorAll('button[data-accion="eliminar-foto"]')).toHaveLength(2);
      TestBed.resetTestingModule();

      await crear(OTRO_JUGADOR);
      expect(el().querySelectorAll('button[data-accion="eliminar-foto"]')).toHaveLength(0);
    });

    it('en una incidencia terminal no se puede eliminar ni añadir fotos', async () => {
      incidencias.obtener.mockReturnValue(of(detalle({ estado: 'CERRADA', fotos: [foto(1)] })));

      await crear(USUARIO_ADMIN);

      expect(accion('eliminar-foto')).toBeNull();
      expect(accion('anadir-foto')).toBeNull();
    });

    it('eliminar pide confirmación; al confirmar borra, libera su URL y recarga', async () => {
      incidencias.obtener.mockReturnValue(of(detalle({ fotos: [foto(1), foto(2)] })));
      fotosApi.eliminar.mockReturnValue(of(undefined));
      await crear(USUARIO_JUGADOR);
      incidencias.obtener.mockReturnValue(of(detalle({ fotos: [foto(2)] })));
      cierraCon(true);

      accion('eliminar-foto')!.click();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(dialogo.open.mock.calls[0][0]).toBe(ConfirmDialog);
      expect(dialogo.open.mock.calls[0][1].data.titulo).toContain('Eliminar foto');
      expect(fotosApi.eliminar).toHaveBeenCalledWith(7, 1);
      expect(miniaturas()).toHaveLength(1);
      expect(urls.revocar).toHaveBeenCalledTimes(1); // la URL de la foto 1
    });

    it('si se cancela la confirmación no se borra nada', async () => {
      incidencias.obtener.mockReturnValue(of(detalle({ fotos: [foto(1)] })));
      await crear(USUARIO_JUGADOR);
      cierraCon(false);

      accion('eliminar-foto')!.click();

      expect(fotosApi.eliminar).not.toHaveBeenCalled();
    });

    it('un error al eliminar se avisa con el mensaje del backend', async () => {
      incidencias.obtener.mockReturnValue(of(detalle({ fotos: [foto(1)] })));
      fotosApi.eliminar.mockReturnValue(
        throwError(() =>
          http(409, { error: 'No se pueden añadir ni quitar fotos de una incidencia cerrada' }),
        ),
      );
      await crear(USUARIO_JUGADOR);
      cierraCon(true);

      accion('eliminar-foto')!.click();
      await fixture.whenStable();

      expect(snack.open.mock.calls[0][0]).toContain('No se pueden añadir ni quitar fotos');
    });

    describe('añadir fotos', () => {
      const campoFoto = (): HTMLInputElement => q<HTMLInputElement>('input[data-campo="foto"]')!;

      it('ofrece añadir mientras haya menos de 4 y el estado no sea terminal; el campo solo acepta imágenes', async () => {
        incidencias.obtener.mockReturnValue(of(detalle({ fotos: [foto(1), foto(2), foto(3)] })));
        await crear();

        expect(accion('anadir-foto')).not.toBeNull();
        expect(campoFoto().getAttribute('accept')).toBe('image/jpeg,image/png,image/webp');
        expect(q('.cupo')?.textContent).toContain('3 de 4');
      });

      it('con 4 fotos ya no se ofrece', async () => {
        incidencias.obtener.mockReturnValue(
          of(detalle({ fotos: [foto(1), foto(2), foto(3), foto(4)] })),
        );
        await crear();

        expect(accion('anadir-foto')).toBeNull();
      });

      it('sube las fotos elegidas de una en una y recarga la incidencia', async () => {
        fotosApi.subir.mockReturnValue(of(foto(9)));
        await crear();
        incidencias.obtener.mockClear();
        incidencias.obtener.mockReturnValue(of(detalle({ fotos: [foto(8), foto(9)] })));

        elegirArchivos(campoFoto(), [crearImagen('a.png'), crearImagen('b.jpg', 'image/jpeg')]);
        await fixture.whenStable();
        fixture.detectChanges();

        expect(fotosApi.subir).toHaveBeenCalledTimes(2);
        expect((fotosApi.subir.mock.calls[0][1] as File).name).toBe('a.png');
        expect(fotosApi.subir.mock.calls[0][0]).toBe(7);
        expect(incidencias.obtener).toHaveBeenCalledTimes(1);
        expect(miniaturas()).toHaveLength(2);
      });

      it('rechaza tipos y tamaños no válidos con un mensaje, sin llamar al servidor', async () => {
        await crear();

        elegirArchivos(campoFoto(), [
          crearImagen('malo.gif', 'image/gif'),
          crearImagen('enorme.png', 'image/png', 5 * 1024 * 1024 + 1),
        ]);
        await fixture.whenStable();
        fixture.detectChanges();

        expect(fotosApi.subir).not.toHaveBeenCalled();
        const mensajes = texto(q('.errores-fotos'));
        expect(mensajes).toContain('malo.gif');
        expect(mensajes).toContain('enorme.png');
      });

      it('cuenta las fotos que ya tiene: con 3 y 2 nuevas solo sube 1 y avisa de la otra', async () => {
        incidencias.obtener.mockReturnValue(of(detalle({ fotos: [foto(1), foto(2), foto(3)] })));
        fotosApi.subir.mockReturnValue(of(foto(9)));
        await crear();

        elegirArchivos(campoFoto(), [crearImagen('a.png'), crearImagen('b.png')]);
        await fixture.whenStable();
        fixture.detectChanges();

        expect(fotosApi.subir).toHaveBeenCalledTimes(1);
        expect(texto(q('.errores-fotos'))).toContain('b.png');
      });

      it('si una foto falla muestra el motivo del backend y sigue con las demás', async () => {
        fotosApi.subir
          .mockReturnValueOnce(
            throwError(() => http(413, { error: 'La imagen supera el tamaño máximo de 5 MB' })),
          )
          .mockReturnValueOnce(of(foto(9)));
        await crear();

        elegirArchivos(campoFoto(), [crearImagen('a.png'), crearImagen('b.png')]);
        await fixture.whenStable();
        fixture.detectChanges();

        expect(fotosApi.subir).toHaveBeenCalledTimes(2);
        expect(texto(q('.errores-fotos'))).toContain('a.png');
        expect(texto(q('.errores-fotos'))).toContain('La imagen supera el tamaño máximo de 5 MB');
      });

      it('dos fotos con el mismo nombre y el mismo fallo se listan las dos, sin claves duplicadas', async () => {
        const aviso = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
        const error = vi.spyOn(console, 'error').mockImplementation(() => undefined);
        fotosApi.subir.mockReturnValue(
          throwError(() => http(413, { error: 'La imagen supera el tamaño máximo de 5 MB' })),
        );
        await crear();

        // Angular solo detecta claves repetidas al actualizar una lista ya pintada: se elige dos veces
        for (let intento = 0; intento < 2; intento++) {
          elegirArchivos(campoFoto(), [crearImagen('a.png'), crearImagen('a.png')]);
          await fixture.whenStable();
          fixture.detectChanges();
        }

        expect(fotosApi.subir).toHaveBeenCalledTimes(4);
        expect(el().querySelectorAll('.errores-fotos li')).toHaveLength(2);
        const registros = [...aviso.mock.calls, ...error.mock.calls].map((c) => String(c[0]));
        expect(registros.filter((m) => m.includes('NG0955'))).toEqual([]);
      });
    });
  });

  describe('historial', () => {
    it('lista los eventos en el orden recibido con autor, texto y tiempo (exacto al pasar el ratón)', async () => {
      incidencias.obtener.mockReturnValue(
        of(
          detalle({
            eventos: [
              evento(1, 'CREADA', { estadoNuevo: 'ABIERTA', creadoEn: '2026-07-15T16:00:00Z' }),
              evento(2, 'COMENTARIO', {
                usuario: { id: 9, nombre: 'Marta Ruiz' },
                texto: 'Mañana lo miramos',
                creadoEn: '2026-07-15T17:00:00Z',
              }),
            ],
          }),
        ),
      );

      await crear();

      const filas = Array.from(el().querySelectorAll<HTMLElement>('.evento'));
      expect(filas).toHaveLength(2);
      expect(texto(filas[0].querySelector('.actor'))).toBe('Ana García López');
      expect(texto(filas[1].querySelector('.actor'))).toBe('Marta Ruiz');
      expect(texto(filas[1].querySelector('.texto'))).toBe('Mañana lo miramos');
      const hora = filas[1].querySelector('time')!;
      expect(hora.getAttribute('title')).toBe('15 de julio de 2026, 19:00');
      expect(hora.getAttribute('datetime')).toBe('2026-07-15T17:00:00Z');
    });

    it.each<[TipoEventoIncidencia, string]>([
      ['CREADA', 'flag'],
      ['ASIGNADA', 'person_add'],
      ['ESTADO', 'sync_alt'],
      ['COMENTARIO', 'chat_bubble'],
      ['PRIORIDAD', 'priority_high'],
      ['FOTO_ANADIDA', 'add_a_photo'],
      ['FOTO_ELIMINADA', 'no_photography'],
      ['BLOQUEO_CREADO', 'block'],
      ['BLOQUEO_LEVANTADO', 'lock_open'],
    ])('el evento %s lleva el icono %s', async (tipo, icono) => {
      incidencias.obtener.mockReturnValue(of(detalle({ eventos: [evento(1, tipo)] })));

      await crear();

      expect(texto(q('.evento mat-icon'))).toBe(icono);
    });

    it('un cambio de estado se muestra "Abierta → Asignada"; uno de prioridad, con su etiqueta', async () => {
      incidencias.obtener.mockReturnValue(
        of(
          detalle({
            eventos: [
              evento(1, 'ESTADO', {
                estadoAnterior: 'EN_CURSO',
                estadoNuevo: 'RESUELTA',
                texto: 'Cambiado el foco',
              }),
              evento(2, 'PRIORIDAD', { estadoAnterior: 'MEDIA', estadoNuevo: 'URGENTE' }),
            ],
          }),
        ),
      );

      await crear();

      const filas = Array.from(el().querySelectorAll<HTMLElement>('.evento'));
      expect(texto(filas[0].querySelector('.cambio'))).toBe('En curso → Resuelta');
      expect(texto(filas[0].querySelector('.texto'))).toBe('Cambiado el foco');
      expect(texto(filas[1].querySelector('.cambio'))).toBe('Media → Urgente');
    });

    it('el texto del evento se pinta como texto, nunca como HTML', async () => {
      incidencias.obtener.mockReturnValue(
        of(
          detalle({
            eventos: [evento(1, 'COMENTARIO', { texto: '<img src=x onerror=alert(1)>' })],
          }),
        ),
      );

      await crear();

      expect(q('.evento .texto img')).toBeNull();
      expect(texto(q('.evento .texto'))).toBe('<img src=x onerror=alert(1)>');
    });
  });

  describe('comentarios', () => {
    const caja = (): HTMLTextAreaElement => q<HTMLTextAreaElement>('textarea[name="comentario"]')!;

    it('se puede comentar mientras no sea terminal; el comentario se envía recortado y se ve el resultado', async () => {
      await crear();
      expect(accion('comentar')!.disabled).toBe(true); // vacío
      incidencias.comentar.mockReturnValue(
        of(
          detalle({
            eventos: [evento(1, 'CREADA'), evento(2, 'COMENTARIO', { texto: 'Gracias' })],
          }),
        ),
      );

      escribir(caja(), '  Gracias  ');
      fixture.detectChanges();
      accion('comentar')!.click();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(incidencias.comentar).toHaveBeenCalledWith(7, 'Gracias');
      expect(el().querySelectorAll('.evento')).toHaveLength(2);
      expect(caja().value).toBe(''); // se vacía tras enviar
    });

    it('un comentario de solo espacios o de más de 2000 caracteres no se puede enviar', async () => {
      await crear();

      escribir(caja(), '    ');
      fixture.detectChanges();
      expect(accion('comentar')!.disabled).toBe(true);

      escribir(caja(), 'a'.repeat(2001));
      fixture.detectChanges();
      expect(accion('comentar')!.disabled).toBe(true);
    });

    it('si falla conserva lo escrito y muestra el motivo', async () => {
      incidencias.comentar.mockReturnValue(
        throwError(() => http(409, { error: 'No se puede comentar una incidencia cerrada' })),
      );
      await crear();

      escribir(caja(), 'Hola');
      fixture.detectChanges();
      accion('comentar')!.click();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(q('.comentar [role="alert"]')?.textContent).toContain(
        'No se puede comentar una incidencia cerrada',
      );
      expect(caja().value).toBe('Hola');
    });

    it.each(['CERRADA', 'RECHAZADA'] as const)(
      'en una incidencia %s no hay formulario de comentarios y se explica',
      async (estado) => {
        incidencias.obtener.mockReturnValue(of(detalle({ estado })));

        await crear(USUARIO_ADMIN);

        expect(q('textarea[name="comentario"]')).toBeNull();
        expect(accion('comentar')).toBeNull();
        expect(texto(q('.comentar'))).toContain('ya no admite comentarios');
      },
    );
  });

  describe('acciones según el rol y el estado', () => {
    it.each<[string, Usuario, Partial<IncidenciaDetalle>, string[]]>([
      ['administrador, ABIERTA', USUARIO_ADMIN, { estado: 'ABIERTA' }, ['asignar', 'rechazar']],
      [
        'administrador, ASIGNADA',
        USUARIO_ADMIN,
        { estado: 'ASIGNADA', asignadaA: { id: 20, nombre: 'Pepe Gil' } },
        ['asignar', 'iniciar'],
      ],
      [
        'administrador, EN_CURSO',
        USUARIO_ADMIN,
        { estado: 'EN_CURSO', asignadaA: { id: 20, nombre: 'Pepe Gil' } },
        ['asignar', 'resolver'],
      ],
      ['administrador, RESUELTA', USUARIO_ADMIN, { estado: 'RESUELTA' }, ['cerrar', 'reabrir']],
      ['administrador, CERRADA', USUARIO_ADMIN, { estado: 'CERRADA' }, []],
      [
        'mantenimiento asignado, ASIGNADA',
        MANTENIMIENTO,
        { estado: 'ASIGNADA', asignadaA: { id: 20, nombre: 'Pepe Gil' } },
        ['iniciar'],
      ],
      [
        'mantenimiento asignado, EN_CURSO',
        MANTENIMIENTO,
        { estado: 'EN_CURSO', asignadaA: { id: 20, nombre: 'Pepe Gil' } },
        ['resolver'],
      ],
      [
        'mantenimiento asignado, RESUELTA',
        MANTENIMIENTO,
        { estado: 'RESUELTA', asignadaA: { id: 20, nombre: 'Pepe Gil' } },
        [],
      ],
      ['reportero, ABIERTA', USUARIO_JUGADOR, { estado: 'ABIERTA' }, []],
      ['reportero, RESUELTA', USUARIO_JUGADOR, { estado: 'RESUELTA' }, ['cerrar', 'reabrir']],
      ['reportero, CERRADA', USUARIO_JUGADOR, { estado: 'CERRADA' }, []],
    ])('%s ofrece %j', async (_caso, usuario, cambios, esperado) => {
      incidencias.obtener.mockReturnValue(of(detalle(cambios)));

      await crear(usuario);

      expect(acciones()).toEqual(esperado);
      if (esperado.length === 0) {
        expect(q('.acciones')).toBeNull();
      }
    });

    it('los botones tienen etiqueta visible en español ("Reasignar" si ya hay responsable)', async () => {
      incidencias.obtener.mockReturnValue(
        of(detalle({ estado: 'ASIGNADA', asignadaA: { id: 20, nombre: 'Pepe Gil' } })),
      );

      await crear(USUARIO_ADMIN);

      expect(texto(accion('asignar'))).toBe('Reasignar');
      expect(texto(accion('iniciar'))).toBe('Marcar en curso');
    });
  });

  describe('flujos de las acciones', () => {
    it('Asignar abre el diálogo con la incidencia y aplica el resultado con un aviso', async () => {
      await crear(USUARIO_ADMIN);
      const asignada = detalle({
        estado: 'ASIGNADA',
        prioridad: 'ALTA',
        asignadaA: { id: 20, nombre: 'Pepe Gil' },
      });
      cierraCon({ tipo: 'ok', incidencia: asignada });
      incidencias.obtener.mockClear();

      accion('asignar')!.click();
      await fixture.whenStable();
      fixture.detectChanges();

      const [componente, config] = dialogo.open.mock.calls[0];
      expect(componente).toBe(AsignarDialog);
      expect(config.data).toEqual({
        incidenciaId: 7,
        clubId: 5,
        prioridad: 'MEDIA',
        asignadaA: null,
      });
      expect(texto(q('[data-tipo="estado"]'))).toBe('Asignada');
      expect(texto(q('[data-tipo="prioridad"]'))).toBe('Alta');
      expect(snack.open.mock.calls[0][0]).toContain('asignada');
      expect(incidencias.obtener).not.toHaveBeenCalled(); // la respuesta ya trae el detalle
    });

    it.each<[string, Partial<IncidenciaDetalle>, string]>([
      ['rechazar', { estado: 'ABIERTA' }, 'RECHAZAR'],
      ['iniciar', { estado: 'ASIGNADA', asignadaA: { id: 20, nombre: 'Pepe Gil' } }, 'INICIAR'],
      ['resolver', { estado: 'EN_CURSO', asignadaA: { id: 20, nombre: 'Pepe Gil' } }, 'RESOLVER'],
      ['cerrar', { estado: 'RESUELTA' }, 'CERRAR'],
      ['reabrir', { estado: 'RESUELTA' }, 'REABRIR'],
    ])('"%s" abre el diálogo de estado con la acción %s', async (boton, cambios, esperada) => {
      incidencias.obtener.mockReturnValue(of(detalle(cambios)));
      await crear(USUARIO_ADMIN);
      cierraCon(undefined);

      accion(boton)!.click();

      const [componente, config] = dialogo.open.mock.calls[0];
      expect(componente).toBe(EstadoDialog);
      expect(config.data).toEqual({ incidenciaId: 7, accion: esperada });
    });

    it('al aplicarse un cambio de estado la página muestra el nuevo detalle (y las acciones nuevas)', async () => {
      incidencias.obtener.mockReturnValue(
        of(detalle({ estado: 'EN_CURSO', asignadaA: { id: 20, nombre: 'Pepe Gil' } })),
      );
      await crear(MANTENIMIENTO);
      expect(acciones()).toEqual(['resolver']);
      cierraCon({
        tipo: 'ok',
        incidencia: detalle({ estado: 'RESUELTA', asignadaA: { id: 20, nombre: 'Pepe Gil' } }),
      });

      accion('resolver')!.click();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(texto(q('[data-tipo="estado"]'))).toBe('Resuelta');
      expect(acciones()).toEqual([]); // el asignado no puede cerrar ni reabrir
    });

    it('un diálogo descartado no cambia nada ni avisa', async () => {
      await crear(USUARIO_ADMIN);
      cierraCon(undefined);
      incidencias.obtener.mockClear();

      accion('rechazar')!.click();
      await fixture.whenStable();

      expect(incidencias.obtener).not.toHaveBeenCalled();
      expect(snack.open).not.toHaveBeenCalled();
    });

    it('un 409 de bloqueo optimista recarga en silencio y avisa con un snackbar', async () => {
      await crear(USUARIO_ADMIN);
      const mensaje = 'La incidencia fue modificada por otra persona, recarga';
      cierraCon({ tipo: 'conflicto', mensaje });
      incidencias.obtener.mockClear();
      incidencias.obtener.mockReturnValue(
        of(detalle({ estado: 'ASIGNADA', asignadaA: { id: 20, nombre: 'Pepe Gil' } })),
      );

      accion('rechazar')!.click();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(incidencias.obtener).toHaveBeenCalledTimes(1);
      expect(q('mat-spinner')).toBeNull(); // silenciosa
      expect(texto(q('[data-tipo="estado"]'))).toBe('Asignada');
      expect(snack.open.mock.calls[0][0]).toContain('modificada por otra persona');
    });

    it('un comentario que choca (409 de versión) también recarga y avisa', async () => {
      await crear(USUARIO_JUGADOR);
      incidencias.comentar.mockReturnValue(throwError(() => CONFLICTO));
      incidencias.obtener.mockClear();

      escribir(q<HTMLTextAreaElement>('textarea[name="comentario"]')!, 'Hola');
      fixture.detectChanges();
      accion('comentar')!.click();
      await fixture.whenStable();

      expect(incidencias.obtener).toHaveBeenCalledTimes(1);
      expect(snack.open.mock.calls[0][0]).toContain('modificada por otra persona');
    });
  });

  describe('bloqueo de pista activo', () => {
    const bloqueo = { id: 4, desde: '2026-07-15T16:00:00Z', hasta: null, motivo: 'Foco fundido' };

    it('lo ve el administrador y el mantenimiento como aviso informativo', async () => {
      incidencias.obtener.mockReturnValue(
        of(
          detalle({
            bloqueoActivo: bloqueo,
            asignadaA: { id: 20, nombre: 'Pepe Gil' },
            estado: 'EN_CURSO',
          }),
        ),
      );

      await crear(USUARIO_ADMIN);
      expect(texto(q('.bloqueo'))).toContain('Pista bloqueada');
      expect(texto(q('.bloqueo'))).toContain('Foco fundido');
      expect(texto(q('.bloqueo'))).toContain('sin fecha de fin');
      expect(q('.bloqueo')?.getAttribute('role')).toBe('status');
      TestBed.resetTestingModule();

      await crear(MANTENIMIENTO);
      expect(q('.bloqueo')).not.toBeNull();
    });

    it('con fecha de fin la muestra en hora de Madrid', async () => {
      incidencias.obtener.mockReturnValue(
        of(detalle({ bloqueoActivo: { ...bloqueo, hasta: '2026-07-20T10:00:00Z' } })),
      );

      await crear(USUARIO_ADMIN);

      expect(texto(q('.bloqueo'))).toContain('20 de julio de 2026, 12:00');
    });

    it('un jugador no lo ve, ni se ve si no hay bloqueo', async () => {
      incidencias.obtener.mockReturnValue(of(detalle({ bloqueoActivo: bloqueo })));
      await crear(USUARIO_JUGADOR);
      expect(q('.bloqueo')).toBeNull();
      TestBed.resetTestingModule();

      incidencias.obtener.mockReturnValue(of(detalle({ bloqueoActivo: null })));
      await crear(USUARIO_ADMIN);
      expect(q('.bloqueo')).toBeNull();
    });
  });

  describe('gestión del bloqueo por el administrador', () => {
    const bloqueoActivo = {
      id: 4,
      desde: '2026-07-15T16:00:00Z',
      hasta: null,
      motivo: 'Foco fundido',
    };
    const bloqueoPista = (c: Partial<BloqueoPista> = {}): BloqueoPista => ({
      id: 4,
      pistaId: 11,
      pistaNombre: 'Pista 1',
      incidenciaId: 7,
      motivo: 'Foco fundido',
      desde: '2026-07-15T16:00:00Z',
      hasta: null,
      creadoPor: { id: 9, nombre: 'Marta Ruiz' },
      creadoEn: '2026-07-15T16:00:00Z',
      levantadoEn: null,
      levantadoPor: null,
      activo: true,
      ...c,
    });
    const afectada = (id: number): ReservaAfectada => ({
      id,
      inicio: '2026-07-15T16:00:00Z',
      fin: '2026-07-15T17:30:00Z',
      organizador: { id: 1, nombre: 'Ana García López', email: 'ana@x.es' },
      jugadores: [{ id: 1, nombre: 'Ana García López' }],
    });
    const creado = (afectadas: ReservaAfectada[]): BloqueoCreado => ({
      bloqueo: bloqueoPista(),
      afectadas,
    });
    const OTRO_ADMIN: Usuario = { ...USUARIO_ADMIN, id: 40, clubId: 6 };
    const CON_RESPONSABLE = { id: 20, nombre: 'Pepe Gil' };

    describe('"Bloquear pista"', () => {
      it.each<[string, Usuario, Partial<IncidenciaDetalle>, boolean]>([
        ['administrador, ABIERTA con pista', USUARIO_ADMIN, { estado: 'ABIERTA' }, true],
        [
          'administrador, ASIGNADA',
          USUARIO_ADMIN,
          { estado: 'ASIGNADA', asignadaA: CON_RESPONSABLE },
          true,
        ],
        [
          'administrador, EN_CURSO',
          USUARIO_ADMIN,
          { estado: 'EN_CURSO', asignadaA: CON_RESPONSABLE },
          true,
        ],
        ['administrador, RESUELTA', USUARIO_ADMIN, { estado: 'RESUELTA' }, true],
        ['administrador, CERRADA (terminal)', USUARIO_ADMIN, { estado: 'CERRADA' }, false],
        ['administrador, RECHAZADA (terminal)', USUARIO_ADMIN, { estado: 'RECHAZADA' }, false],
        [
          'administrador, incidencia sin pista concreta',
          USUARIO_ADMIN,
          { pistaId: null, pistaNombre: null },
          false,
        ],
        ['administrador, con un bloqueo ya activo', USUARIO_ADMIN, { bloqueoActivo }, false],
        ['administrador de OTRO club', OTRO_ADMIN, {}, false],
        [
          'mantenimiento asignado',
          MANTENIMIENTO,
          { estado: 'EN_CURSO', asignadaA: CON_RESPONSABLE },
          false,
        ],
        ['reportero', USUARIO_JUGADOR, {}, false],
      ])('%s: visible=%s', async (_caso, usuario, cambios, visible) => {
        incidencias.obtener.mockReturnValue(of(detalle(cambios)));

        await crear(usuario);

        expect(accion('bloquear-pista') !== null).toBe(visible);
      });

      it('no se mezcla con las acciones de estado de la incidencia', async () => {
        await crear(USUARIO_ADMIN);

        expect(acciones()).toEqual(['asignar', 'rechazar']);
        expect(accion('bloquear-pista')).not.toBeNull();
      });

      it('el botón nombra la pista y es accesible', async () => {
        await crear(USUARIO_ADMIN);

        expect(texto(accion('bloquear-pista'))).toContain('Bloquear pista');
        expect(accion('bloquear-pista')!.getAttribute('aria-label')).toBe(
          'Bloquear pista (Pista 1) por esta incidencia',
        );
      });

      it('llama al atajo, abre las afectadas al momento con la respuesta, avisa y recarga la incidencia', async () => {
        const respuesta = creado([afectada(1), afectada(2)]);
        bloqueosApi.bloquearPistaDeIncidencia.mockReturnValue(of(respuesta));
        await crear(USUARIO_ADMIN);
        incidencias.obtener.mockClear();
        incidencias.obtener.mockReturnValue(of(detalle({ bloqueoActivo })));

        accion('bloquear-pista')!.click();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(bloqueosApi.bloquearPistaDeIncidencia).toHaveBeenCalledWith(7);
        const [componente, config] = dialogo.open.mock.calls[0];
        expect(componente).toBe(AfectadasDialog);
        expect(config.data).toEqual({
          clubId: 5,
          pistaId: 11,
          pistaNombre: 'Pista 1',
          bloqueoId: 4,
          afectadas: respuesta.afectadas,
        });
        expect(snack.open.mock.calls[0][0]).toContain('Pista bloqueada');
        expect(incidencias.obtener).toHaveBeenCalledTimes(1);
        expect(q('mat-spinner')).toBeNull(); // recarga silenciosa
        expect(q('.bloqueo')).not.toBeNull();
        expect(accion('bloquear-pista')).toBeNull(); // ya hay bloqueo activo
      });

      it('si no hay reservas afectadas no abre el diálogo y lo dice', async () => {
        bloqueosApi.bloquearPistaDeIncidencia.mockReturnValue(of(creado([])));
        await crear(USUARIO_ADMIN);

        accion('bloquear-pista')!.click();
        await fixture.whenStable();

        expect(dialogo.open).not.toHaveBeenCalled();
        expect(snack.open.mock.calls[0][0]).toContain('No hay reservas afectadas');
      });

      it('un 409 del backend se avisa con su mensaje, sin diálogo, y recarga para mostrar el estado real', async () => {
        bloqueosApi.bloquearPistaDeIncidencia.mockReturnValue(
          throwError(() => http(409, { error: 'La incidencia ya tiene un bloqueo activo' })),
        );
        await crear(USUARIO_ADMIN);
        incidencias.obtener.mockClear();

        accion('bloquear-pista')!.click();
        await fixture.whenStable();

        expect(snack.open.mock.calls[0][0]).toBe('La incidencia ya tiene un bloqueo activo');
        expect(dialogo.open).not.toHaveBeenCalled();
        expect(incidencias.obtener).toHaveBeenCalledTimes(1);
      });

      it('mientras la petición está en vuelo el botón se deshabilita y no se repite', async () => {
        bloqueosApi.bloquearPistaDeIncidencia.mockReturnValue(new Subject<BloqueoCreado>());
        await crear(USUARIO_ADMIN);

        accion('bloquear-pista')!.click();
        fixture.detectChanges();
        expect(accion('bloquear-pista')!.disabled).toBe(true);
        accion('bloquear-pista')!.click();

        expect(bloqueosApi.bloquearPistaDeIncidencia).toHaveBeenCalledTimes(1);
      });
    });

    describe('acciones del aviso de bloqueo activo', () => {
      const conBloqueo = (): void => {
        incidencias.obtener.mockReturnValue(
          of(detalle({ bloqueoActivo, estado: 'EN_CURSO', asignadaA: CON_RESPONSABLE })),
        );
      };
      const acciones3 = ['ver-afectadas', 'cancelar-afectadas', 'levantar-bloqueo'];

      it('el administrador ve las tres acciones junto al aviso, con etiqueta accesible', async () => {
        conBloqueo();

        await crear(USUARIO_ADMIN);

        for (const nombre of acciones3) {
          expect(q(`.bloqueo-caja button[data-accion="${nombre}"]`)).not.toBeNull();
        }
        expect(texto(accion('ver-afectadas'))).toBe('Ver afectadas');
        expect(texto(accion('cancelar-afectadas'))).toBe('Cancelar afectadas');
        expect(texto(accion('levantar-bloqueo'))).toBe('Levantar bloqueo');
        expect(accion('levantar-bloqueo')!.getAttribute('aria-label')).toBe(
          'Levantar bloqueo de Pista 1',
        );
      });

      it('el mantenimiento sigue viendo el aviso de solo lectura, sin acciones', async () => {
        conBloqueo();

        await crear(MANTENIMIENTO);

        expect(q('.bloqueo')).not.toBeNull();
        for (const nombre of [...acciones3, 'bloquear-pista']) {
          expect(accion(nombre)).toBeNull();
        }
      });

      it('el reportero no ve el aviso ni acciones; el administrador de otro club tampoco', async () => {
        conBloqueo();
        await crear(USUARIO_JUGADOR);
        for (const nombre of [...acciones3, 'bloquear-pista']) {
          expect(accion(nombre)).toBeNull();
        }
        TestBed.resetTestingModule();

        await crear(OTRO_ADMIN);
        expect(q('.bloqueo')).toBeNull();
        expect(accion('levantar-bloqueo')).toBeNull();
      });

      it('"Ver afectadas" abre el diálogo de afectadas del bloqueo', async () => {
        conBloqueo();
        await crear(USUARIO_ADMIN);

        accion('ver-afectadas')!.click();

        const [componente, config] = dialogo.open.mock.calls[0];
        expect(componente).toBe(AfectadasDialog);
        expect(config.data).toEqual({
          clubId: 5,
          pistaId: 11,
          pistaNombre: 'Pista 1',
          bloqueoId: 4,
        });
      });

      it('"Cancelar afectadas" averigua cuántas son, confirma con el recuento, cancela y avisa del resultado', async () => {
        conBloqueo();
        bloqueosApi.afectadas.mockReturnValue(of([afectada(1), afectada(2), afectada(3)]));
        bloqueosApi.cancelarAfectadas.mockReturnValue(of({ canceladas: 3, ids: [1, 2, 3] }));
        await crear(USUARIO_ADMIN);
        cierraCon(true);

        accion('cancelar-afectadas')!.click();
        await fixture.whenStable();

        expect(bloqueosApi.afectadas).toHaveBeenCalledWith(5, 11, 4);
        const [componente, config] = dialogo.open.mock.calls[0];
        expect(componente).toBe(ConfirmDialog);
        expect(config.data.mensaje).toContain('3 reservas');
        expect(bloqueosApi.cancelarAfectadas).toHaveBeenCalledWith(5, 11, 4);
        expect(snack.open.mock.calls[0][0]).toContain('Se cancelaron 3 reservas');
      });

      it('"Cancelar afectadas" sin ninguna afectada dice que no había y no cancela', async () => {
        conBloqueo();
        bloqueosApi.afectadas.mockReturnValue(of([]));
        await crear(USUARIO_ADMIN);

        accion('cancelar-afectadas')!.click();
        await fixture.whenStable();

        expect(snack.open.mock.calls[0][0]).toBe('No había reservas afectadas.');
        expect(bloqueosApi.cancelarAfectadas).not.toHaveBeenCalled();
      });

      it('"Levantar bloqueo" pide confirmación, levanta, avisa y recarga: el aviso desaparece y se puede volver a bloquear', async () => {
        conBloqueo();
        bloqueosApi.levantar.mockReturnValue(of(undefined));
        await crear(USUARIO_ADMIN);
        cierraCon(true);
        incidencias.obtener.mockReturnValue(
          of(detalle({ estado: 'EN_CURSO', asignadaA: CON_RESPONSABLE, bloqueoActivo: null })),
        );

        accion('levantar-bloqueo')!.click();
        await fixture.whenStable();
        fixture.detectChanges();

        const [componente, config] = dialogo.open.mock.calls[0];
        expect(componente).toBe(ConfirmDialog);
        expect(config.data.titulo).toContain('Levantar');
        expect(bloqueosApi.levantar).toHaveBeenCalledWith(5, 11, 4);
        expect(snack.open.mock.calls[0][0]).toContain('Bloqueo levantado');
        expect(q('.bloqueo')).toBeNull();
        expect(accion('bloquear-pista')).not.toBeNull();
      });

      it('si se rechaza la confirmación no se levanta nada', async () => {
        conBloqueo();
        await crear(USUARIO_ADMIN);
        cierraCon(false);

        accion('levantar-bloqueo')!.click();
        await fixture.whenStable();

        expect(bloqueosApi.levantar).not.toHaveBeenCalled();
      });
    });
  });

  describe('actualización en tiempo real', () => {
    const push = (tipo: TipoNotificacion): void =>
      recibidas.next(crearNotificacion(Math.floor(Math.random() * 1e6), { tipo }));

    async function arrancar(): Promise<void> {
      await crear();
      vi.useFakeTimers();
      incidencias.obtener.mockClear();
    }

    it.each<TipoNotificacion>([
      'INCIDENCIA_NUEVA',
      'INCIDENCIA_ASIGNADA',
      'INCIDENCIA_ESTADO',
      'INCIDENCIA_COMENTARIO',
    ])('recarga en silencio al llegar %s', async (tipo) => {
      await arrancar();

      push(tipo);
      expect(incidencias.obtener).not.toHaveBeenCalled();
      vi.advanceTimersByTime(300);

      expect(incidencias.obtener).toHaveBeenCalledTimes(1);
      expect(incidencias.obtener).toHaveBeenCalledWith(7);
    });

    it.each<TipoNotificacion>(['CARGO_NUEVO', 'PERSONAL_ANADIDO'])('ignora %s', async (tipo) => {
      await arrancar();

      push(tipo);
      vi.advanceTimersByTime(1000);

      expect(incidencias.obtener).not.toHaveBeenCalled();
    });

    it('agrupa ráfagas en una sola recarga', async () => {
      await arrancar();

      for (let i = 0; i < 10; i++) {
        push('INCIDENCIA_ESTADO');
        vi.advanceTimersByTime(50);
      }
      vi.advanceTimersByTime(300);

      expect(incidencias.obtener).toHaveBeenCalledTimes(1);
    });

    it('la recarga no muestra spinner, cambia el estado a la vista y respeta lo que se está escribiendo', async () => {
      await crear();
      escribir(q<HTMLTextAreaElement>('textarea[name="comentario"]')!, 'Borrador a medias');
      vi.useFakeTimers();
      incidencias.obtener.mockReturnValue(
        of(detalle({ estado: 'ASIGNADA', asignadaA: { id: 20, nombre: 'Pepe Gil' } })),
      );

      push('INCIDENCIA_ESTADO');
      vi.advanceTimersByTime(300);
      fixture.detectChanges();

      expect(q('mat-spinner')).toBeNull();
      expect(texto(q('[data-tipo="estado"]'))).toBe('Asignada');
      expect(q<HTMLTextAreaElement>('textarea[name="comentario"]')!.value).toBe(
        'Borrador a medias',
      );
    });

    it('si la incidencia deja de ser visible (404 al recargar) lo muestra', async () => {
      await arrancar();
      incidencias.obtener.mockReturnValue(
        throwError(() => http(404, { error: 'Incidencia no encontrada' })),
      );

      push('INCIDENCIA_ESTADO');
      vi.advanceTimersByTime(300);
      fixture.detectChanges();

      expect(q('[role="alert"]')?.textContent).toContain('Incidencia no encontrada');
    });

    it('un fallo de red en la recarga silenciosa conserva lo que se veía', async () => {
      await arrancar();
      incidencias.obtener.mockReturnValue(throwError(() => http(0)));

      push('INCIDENCIA_ESTADO');
      vi.advanceTimersByTime(300);
      fixture.detectChanges();

      expect(q('[role="alert"]')).toBeNull();
      expect(texto(q('h1'))).toBe('Foco fundido');
    });

    it('al destruir la página deja de escuchar: no recarga aunque llegue un push', async () => {
      await arrancar();

      push('INCIDENCIA_NUEVA');
      fixture.destroy();
      vi.advanceTimersByTime(300);
      push('INCIDENCIA_ESTADO');
      vi.advanceTimersByTime(300);

      expect(incidencias.obtener).not.toHaveBeenCalled();
      expect(recibidas.observed).toBe(false);
    });
  });

  it('al destruir la página revoca las URLs de las fotos (la caché vive con la página)', async () => {
    incidencias.obtener.mockReturnValue(of(detalle({ fotos: [foto(1), foto(2)] })));
    await crear();
    expect(urls.crear).toHaveBeenCalledTimes(2);

    fixture.destroy();

    expect(urls.revocar).toHaveBeenCalledTimes(2);
  });
});
