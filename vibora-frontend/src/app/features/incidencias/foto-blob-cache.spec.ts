import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { simularObjectUrls } from '../../testing/test-utils';
import { FotoBlobCache } from './foto-blob-cache';
import { FotoIncidenciaService } from './foto-incidencia.service';

describe('FotoBlobCache', () => {
  const api = { obtenerBlob: vi.fn() };
  let urls: ReturnType<typeof simularObjectUrls>;

  function crear(): FotoBlobCache {
    TestBed.configureTestingModule({
      providers: [FotoBlobCache, { provide: FotoIncidenciaService, useValue: api }],
    });
    return TestBed.inject(FotoBlobCache);
  }

  const blob = (): Blob => new Blob([new Uint8Array([1, 2, 3])], { type: 'image/png' });

  beforeEach(() => {
    vi.resetAllMocks();
    urls = simularObjectUrls();
  });
  afterEach(() => urls.restaurar());

  it('empieza en "cargando" y pasa a "lista" con la URL de objeto cuando llega el blob', () => {
    const respuesta = new Subject<Blob>();
    api.obtenerBlob.mockReturnValue(respuesta);
    const cache = crear();

    const estado = cache.estado(7, 3);
    expect(estado()).toEqual({ tipo: 'cargando' });

    const datos = blob();
    respuesta.next(datos);
    respuesta.complete();

    expect(urls.crear).toHaveBeenCalledWith(datos);
    expect(estado()).toEqual({ tipo: 'lista', url: 'blob:prueba/1' });
    expect(api.obtenerBlob).toHaveBeenCalledWith(7, 3);
  });

  it('pide cada foto UNA sola vez aunque se consulte varias veces', () => {
    api.obtenerBlob.mockReturnValue(of(blob()));
    const cache = crear();

    const primera = cache.estado(7, 3);
    const segunda = cache.estado(7, 3);

    expect(segunda).toBe(primera);
    expect(api.obtenerBlob).toHaveBeenCalledTimes(1);
    expect(urls.crear).toHaveBeenCalledTimes(1);
  });

  it('fotos distintas se piden por separado', () => {
    api.obtenerBlob.mockReturnValue(of(blob()));
    const cache = crear();

    cache.estado(7, 3);
    cache.estado(7, 4);

    expect(api.obtenerBlob).toHaveBeenCalledTimes(2);
    expect(urls.crear).toHaveBeenCalledTimes(2);
  });

  it('un error de descarga deja la foto en "error" y no crea ninguna URL', () => {
    api.obtenerBlob.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    const cache = crear();

    expect(cache.estado(7, 3)()).toEqual({ tipo: 'error' });
    expect(urls.crear).not.toHaveBeenCalled();
  });

  it('reintentar vuelve a pedir la foto y puede salir bien', () => {
    api.obtenerBlob.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
    const cache = crear();
    const estado = cache.estado(7, 3);
    expect(estado()).toEqual({ tipo: 'error' });

    api.obtenerBlob.mockReturnValueOnce(of(blob()));
    cache.reintentar(7, 3);

    expect(api.obtenerBlob).toHaveBeenCalledTimes(2);
    expect(estado()).toEqual({ tipo: 'lista', url: 'blob:prueba/1' });
  });

  it('reintentar no hace nada si la foto ya se cargó', () => {
    api.obtenerBlob.mockReturnValue(of(blob()));
    const cache = crear();
    cache.estado(7, 3);

    cache.reintentar(7, 3);

    expect(api.obtenerBlob).toHaveBeenCalledTimes(1);
  });

  it('olvidar libera la URL de esa foto y la siguiente consulta la vuelve a pedir', () => {
    api.obtenerBlob.mockReturnValue(of(blob()));
    const cache = crear();
    cache.estado(7, 3);
    cache.estado(7, 4);

    cache.olvidar(3);

    expect(urls.revocar).toHaveBeenCalledTimes(1);
    expect(urls.revocar).toHaveBeenCalledWith('blob:prueba/1');
    cache.estado(7, 3);
    expect(api.obtenerBlob).toHaveBeenCalledTimes(3);
  });

  it('al destruirse (el componente) revoca TODAS las URLs creadas', () => {
    api.obtenerBlob.mockReturnValue(of(blob()));
    const cache = crear();
    cache.estado(7, 3);
    cache.estado(7, 4);
    expect(urls.revocar).not.toHaveBeenCalled();

    TestBed.resetTestingModule();

    expect(urls.revocar).toHaveBeenCalledTimes(2);
    expect(urls.revocar).toHaveBeenCalledWith('blob:prueba/1');
    expect(urls.revocar).toHaveBeenCalledWith('blob:prueba/2');
  });

  it('si la respuesta llega DESPUÉS de destruirse no se crea ninguna URL (no se filtra memoria)', () => {
    const respuesta = new Subject<Blob>();
    api.obtenerBlob.mockReturnValue(respuesta);
    const cache = crear();
    cache.estado(7, 3);

    TestBed.resetTestingModule();
    respuesta.next(blob());

    expect(urls.crear).not.toHaveBeenCalled();
    expect(respuesta.observed).toBe(false);
  });

  it('destruir sin haber pedido nada no revoca nada', () => {
    crear();

    TestBed.resetTestingModule();

    expect(urls.revocar).not.toHaveBeenCalled();
  });
});
