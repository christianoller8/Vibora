import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { crearImagen, elegirArchivos, simularObjectUrls } from '../../../testing/test-utils';
import { SelectorFotos } from './selector-fotos';

@Component({
  imports: [SelectorFotos],
  template: `<app-selector-fotos [(archivos)]="archivos" [deshabilitado]="bloqueado()" />`,
})
class Anfitrion {
  readonly archivos = signal<File[]>([]);
  readonly bloqueado = signal(false);
}

describe('SelectorFotos', () => {
  let fixture: ComponentFixture<Anfitrion>;
  let urls: ReturnType<typeof simularObjectUrls>;

  async function crear(): Promise<void> {
    await TestBed.configureTestingModule({ imports: [Anfitrion] }).compileComponents();
    fixture = TestBed.createComponent(Anfitrion);
    await fixture.whenStable();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const entrada = (): HTMLInputElement =>
    el().querySelector<HTMLInputElement>('input[type="file"]')!;
  const miniaturas = (): HTMLElement[] =>
    Array.from(el().querySelectorAll<HTMLElement>('.miniatura'));
  const errores = (): string[] =>
    Array.from(el().querySelectorAll('.errores li')).map((li) => li.textContent!.trim());
  const botonElegir = (): HTMLButtonElement =>
    el().querySelector<HTMLButtonElement>('button[data-accion="elegir"]')!;

  async function elegir(...archivos: File[]): Promise<void> {
    elegirArchivos(entrada(), archivos);
    await fixture.whenStable();
  }

  beforeEach(() => (urls = simularObjectUrls()));
  afterEach(() => urls.restaurar());

  it('el campo de archivos solo admite JPEG, PNG y WebP y permite elegir varios', async () => {
    await crear();

    expect(entrada().getAttribute('accept')).toBe('image/jpeg,image/png,image/webp');
    expect(entrada().multiple).toBe(true);
  });

  it('el botón "Añadir fotos" abre el selector de archivos', async () => {
    await crear();
    const abrir = vi.spyOn(entrada(), 'click');

    botonElegir().click();

    expect(abrir).toHaveBeenCalledTimes(1);
  });

  it('al elegir fotos válidas las añade al modelo y muestra una miniatura con texto alternativo', async () => {
    await crear();
    const a = crearImagen('foco.png');
    const b = crearImagen('red.jpg', 'image/jpeg');

    await elegir(a, b);

    expect(fixture.componentInstance.archivos()).toEqual([a, b]);
    expect(miniaturas()).toHaveLength(2);
    const imagen = miniaturas()[0].querySelector('img')!;
    expect(imagen.getAttribute('src')).toBe('blob:prueba/1');
    expect(imagen.getAttribute('alt')).toBe('Vista previa de foco.png');
    expect(el().querySelector('.contador')?.textContent).toContain('2 de 4');
  });

  it('un archivo de tipo no permitido se rechaza con un mensaje claro y no se añade', async () => {
    await crear();

    await elegir(crearImagen('animacion.gif', 'image/gif'));

    expect(fixture.componentInstance.archivos()).toEqual([]);
    expect(errores()).toHaveLength(1);
    expect(errores()[0]).toContain('animacion.gif');
    expect(errores()[0]).toContain('JPEG, PNG o WebP');
    expect(el().querySelector('.errores')?.getAttribute('role')).toBe('alert');
  });

  it('un archivo de más de 5 MB se rechaza', async () => {
    await crear();

    await elegir(crearImagen('enorme.png', 'image/png', 5 * 1024 * 1024 + 1));

    expect(fixture.componentInstance.archivos()).toEqual([]);
    expect(errores()[0]).toContain('5 MB');
  });

  it('admite como máximo 4 fotos: las sobrantes se rechazan nombrándolas y el botón se deshabilita', async () => {
    await crear();

    await elegir(...[1, 2, 3, 4, 5].map((i) => crearImagen(`f${i}.png`)));

    expect(fixture.componentInstance.archivos()).toHaveLength(4);
    expect(errores()).toHaveLength(1);
    expect(errores()[0]).toContain('f5.png');
    expect(botonElegir().disabled).toBe(true);
  });

  it('los errores de una elección anterior desaparecen cuando la siguiente es válida', async () => {
    await crear();
    await elegir(crearImagen('malo.gif', 'image/gif'));
    expect(errores()).toHaveLength(1);

    await elegir(crearImagen('bueno.png'));

    expect(errores()).toHaveLength(0);
  });

  it('quitar una foto la saca del modelo y revoca su URL de vista previa', async () => {
    await crear();
    const a = crearImagen('a.png');
    const b = crearImagen('b.png');
    await elegir(a, b);

    const quitar = miniaturas()[0].querySelector<HTMLButtonElement>(
      'button[data-accion="quitar"]',
    )!;
    expect(quitar.getAttribute('aria-label')).toBe('Quitar la foto a.png');
    quitar.click();
    await fixture.whenStable();

    expect(fixture.componentInstance.archivos()).toEqual([b]);
    expect(miniaturas()).toHaveLength(1);
    expect(urls.revocar).toHaveBeenCalledWith('blob:prueba/1');
    expect(botonElegir().disabled).toBe(false);
  });

  it('al destruirse revoca todas las URLs de vista previa', async () => {
    await crear();
    await elegir(crearImagen('a.png'), crearImagen('b.png'));
    expect(urls.revocar).not.toHaveBeenCalled();

    fixture.destroy();

    expect(urls.revocar).toHaveBeenCalledTimes(2);
  });

  it('deshabilitado no deja elegir ni quitar', async () => {
    await crear();
    await elegir(crearImagen('a.png'));

    fixture.componentInstance.bloqueado.set(true);
    await fixture.whenStable();

    expect(botonElegir().disabled).toBe(true);
    expect(
      miniaturas()[0].querySelector<HTMLButtonElement>('button[data-accion="quitar"]')!.disabled,
    ).toBe(true);
  });
});
