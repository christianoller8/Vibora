import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { FotoIncidencia } from '../../../shared/models/incidencia.model';
import { FotoBlobCache } from '../foto-blob-cache';
import { FotoVisorDialog, FotoVisorDialogData } from './foto-visor-dialog';

function foto(id: number, nombre: string): FotoIncidencia {
  return {
    id,
    nombreOriginal: nombre,
    tipoContenido: 'image/png',
    tamano: 100,
    creadaEn: '2026-07-15T16:00:00Z',
    subidaPor: { id: 1, nombre: 'Ana García' },
  };
}

describe('FotoVisorDialog', () => {
  let fixture: ComponentFixture<FotoVisorDialog>;
  const dialogRef = { close: vi.fn() };
  const cache = {
    estado: vi.fn((_i: number, fotoId: number) =>
      signal({ tipo: 'lista' as const, url: `blob:prueba/${fotoId}` }),
    ),
    reintentar: vi.fn(),
  };

  async function crear(data: FotoVisorDialogData): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [FotoVisorDialog],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: FotoBlobCache, useValue: cache },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(FotoVisorDialog);
    await fixture.whenStable();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const imagen = (): HTMLImageElement => el().querySelector<HTMLImageElement>('img')!;
  const boton = (accion: string): HTMLButtonElement | null =>
    el().querySelector<HTMLButtonElement>(`button[data-accion="${accion}"]`);
  const fotos = [foto(1, 'uno.png'), foto(2, 'dos.png'), foto(3, 'tres.png')];

  beforeEach(() => vi.clearAllMocks());

  it('muestra la foto elegida a tamaño completo, con su nombre y texto alternativo', async () => {
    await crear({ incidenciaId: 7, fotos, indice: 1 });

    expect(el().querySelector('h2')?.textContent).toContain('dos.png');
    expect(imagen().getAttribute('src')).toBe('blob:prueba/2');
    expect(imagen().getAttribute('alt')).toBe('Foto de la incidencia: dos.png');
    expect(el().querySelector('.posicion')?.textContent).toContain('2 de 3');
  });

  it('"Siguiente" y "Anterior" recorren las fotos y dan la vuelta', async () => {
    await crear({ incidenciaId: 7, fotos, indice: 2 });

    boton('siguiente')!.click();
    await fixture.whenStable();
    expect(imagen().getAttribute('alt')).toContain('uno.png');

    boton('anterior')!.click();
    await fixture.whenStable();
    expect(imagen().getAttribute('alt')).toContain('tres.png');
  });

  it('las flechas del teclado también navegan', async () => {
    await crear({ incidenciaId: 7, fotos, indice: 0 });

    el().dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    await fixture.whenStable();
    expect(imagen().getAttribute('alt')).toContain('dos.png');

    el().dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true }));
    await fixture.whenStable();
    expect(imagen().getAttribute('alt')).toContain('uno.png');
  });

  it('con una sola foto no hay navegación', async () => {
    await crear({ incidenciaId: 7, fotos: [fotos[0]], indice: 0 });

    expect(boton('siguiente')).toBeNull();
    expect(boton('anterior')).toBeNull();
  });

  it('el botón de cerrar tiene etiqueta accesible y cierra el diálogo', async () => {
    await crear({ incidenciaId: 7, fotos, indice: 0 });

    const cerrar = boton('cerrar')!;
    expect(cerrar.getAttribute('aria-label')).toBe('Cerrar el visor de fotos');
    cerrar.click();

    expect(dialogRef.close).toHaveBeenCalledTimes(1);
  });
});
