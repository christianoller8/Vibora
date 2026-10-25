import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FotoIncidencia } from '../../../shared/models/incidencia.model';
import { EstadoFotoBlob, FotoBlobCache } from '../foto-blob-cache';
import { FotoMiniatura } from './foto-miniatura';

const FOTO: FotoIncidencia = {
  id: 3,
  nombreOriginal: 'foco.png',
  tipoContenido: 'image/png',
  tamano: 1234,
  creadaEn: '2026-07-15T16:00:00Z',
  subidaPor: { id: 1, nombre: 'Ana García' },
};

describe('FotoMiniatura', () => {
  let fixture: ComponentFixture<FotoMiniatura>;
  const estado = signal<EstadoFotoBlob>({ tipo: 'cargando' });
  const cache = { estado: vi.fn(), reintentar: vi.fn() };

  async function crear(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [FotoMiniatura],
      providers: [{ provide: FotoBlobCache, useValue: cache }],
    }).compileComponents();
    fixture = TestBed.createComponent(FotoMiniatura);
    fixture.componentRef.setInput('incidenciaId', 7);
    fixture.componentRef.setInput('foto', FOTO);
    await fixture.whenStable();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;

  beforeEach(() => {
    vi.resetAllMocks();
    estado.set({ tipo: 'cargando' });
    cache.estado.mockReturnValue(estado);
  });

  it('pide la foto a la caché con el id de la incidencia y el de la foto', async () => {
    await crear();

    expect(cache.estado).toHaveBeenCalledWith(7, 3);
  });

  it('mientras carga muestra un indicador accesible y ninguna imagen', async () => {
    await crear();

    expect(el().querySelector('mat-spinner')).not.toBeNull();
    expect(el().querySelector('img')).toBeNull();
  });

  it('cuando está lista muestra la imagen con su URL de objeto y texto alternativo', async () => {
    await crear();

    estado.set({ tipo: 'lista', url: 'blob:prueba/1' });
    await fixture.whenStable();

    const imagen = el().querySelector('img')!;
    expect(imagen.getAttribute('src')).toBe('blob:prueba/1');
    expect(imagen.getAttribute('alt')).toBe('Foto de la incidencia: foco.png');
    expect(el().querySelector('mat-spinner')).toBeNull();
  });

  it('si falla muestra el aviso y "Reintentar" vuelve a pedirla', async () => {
    await crear();

    estado.set({ tipo: 'error' });
    await fixture.whenStable();

    expect(el().textContent).toContain('No se pudo cargar la foto');
    const reintentar = el().querySelector<HTMLButtonElement>('button[data-accion="reintentar"]')!;
    expect(reintentar.getAttribute('aria-label')).toBe('Reintentar cargar la foto foco.png');
    reintentar.click();
    expect(cache.reintentar).toHaveBeenCalledWith(7, 3);
  });
});
