import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { Notificacion, TipoNotificacion } from '../../../shared/models/notificacion.model';
import { crearNotificacion } from '../../../testing/test-utils';
import { NotificacionesStore } from '../notificaciones.store';
import { Campana } from './campana';

describe('Campana', () => {
  let fixture: ComponentFixture<Campana>;
  const items = signal<Notificacion[]>([]);
  const noLeidas = signal(0);
  const store = { items, noLeidas, marcarLeida: vi.fn(), marcarTodas: vi.fn() };

  async function crear(lista: Notificacion[] = [], sinLeer = 0): Promise<void> {
    items.set(lista);
    noLeidas.set(sinLeer);
    await TestBed.configureTestingModule({
      imports: [Campana],
      providers: [provideRouter([]), { provide: NotificacionesStore, useValue: store }],
    }).compileComponents();
    fixture = TestBed.createComponent(Campana);
    await fixture.whenStable();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const boton = (): HTMLButtonElement => el().querySelector('button[data-accion="campana"]')!;
  const insignia = (): string =>
    el().querySelector('.mat-badge-content')?.textContent?.trim() ?? '';
  const filas = (): HTMLElement[] =>
    Array.from(document.querySelectorAll<HTMLElement>('.cdk-overlay-container .notificacion'));

  async function abrir(): Promise<void> {
    boton().click();
    await fixture.whenStable();
  }

  beforeEach(() => vi.resetAllMocks());

  it('muestra el número de no leídas en la insignia', async () => {
    await crear([], 3);

    expect(insignia()).toBe('3');
    expect(boton().classList).not.toContain('mat-badge-hidden');
  });

  it('oculta la insignia cuando no hay no leídas', async () => {
    await crear([], 0);

    expect(boton().classList).toContain('mat-badge-hidden');
  });

  it('limita la insignia a "9+"', async () => {
    await crear([], 9);
    expect(insignia()).toBe('9');

    noLeidas.set(10);
    await fixture.whenStable();
    expect(insignia()).toBe('9+');

    noLeidas.set(250);
    await fixture.whenStable();
    expect(insignia()).toBe('9+');
  });

  it('el botón tiene un aria-label con el número de no leídas', async () => {
    await crear([], 4);
    expect(boton().getAttribute('aria-label')).toBe('Notificaciones, 4 sin leer');

    noLeidas.set(0);
    await fixture.whenStable();
    expect(boton().getAttribute('aria-label')).toBe('Notificaciones, ninguna sin leer');
  });

  it('al abrir el menú lista las notificaciones con su mensaje y su tiempo relativo', async () => {
    await crear([crearNotificacion(5), crearNotificacion(120)], 2);

    await abrir();

    expect(filas()).toHaveLength(2);
    expect(filas()[0].textContent).toContain('Aviso 5');
    expect(filas()[0].textContent).toContain('hace 5 min');
    expect(filas()[1].textContent).toContain('hace 2 h');
  });

  it('destaca las no leídas', async () => {
    await crear([crearNotificacion(1), crearNotificacion(2, { leida: true })], 1);

    await abrir();

    expect(filas()[0].classList).toContain('no-leida');
    expect(filas()[1].classList).not.toContain('no-leida');
  });

  it('muestra un estado vacío si no hay notificaciones', async () => {
    await crear([], 0);

    await abrir();

    expect(document.querySelector('.cdk-overlay-container')!.textContent).toContain(
      'No tienes notificaciones',
    );
  });

  it('al pulsar una notificación la marca como leída y navega según su tipo', async () => {
    await crear([crearNotificacion(7, { tipo: 'PAGO_RECIBIDO' })], 1);
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await abrir();

    filas()[0].click();
    await fixture.whenStable();

    expect(store.marcarLeida).toHaveBeenCalledWith(7);
    expect(navegar).toHaveBeenCalledWith('/deudas');
  });

  it('una notificación ya leída navega pero no vuelve a marcarse', async () => {
    await crear([crearNotificacion(8, { tipo: 'JUGADOR_SALIO', leida: true })], 0);
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await abrir();

    filas()[0].click();
    await fixture.whenStable();

    expect(store.marcarLeida).not.toHaveBeenCalled();
    expect(navegar).toHaveBeenCalledWith('/mis-reservas');
  });

  it.each<[TipoNotificacion, string]>([
    ['PERSONAL_ANADIDO', '/agenda'],
    ['INCIDENCIA_NUEVA', '/incidencias'],
    ['INCIDENCIA_COMENTARIO', '/incidencias'],
  ])('una notificación %s se marca leída y navega a %s sin lanzar', async (tipo, destino) => {
    await crear([crearNotificacion(9, { tipo })], 1);
    // como el Router real: una URL que no es string revienta
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockImplementation(((
      url: unknown,
    ) => {
      if (typeof url !== 'string') {
        throw new TypeError('destino inválido');
      }
      return Promise.resolve(true);
    }) as Router['navigateByUrl']);
    await abrir();

    filas()[0].click();
    await fixture.whenStable();

    expect(store.marcarLeida).toHaveBeenCalledWith(9);
    expect(navegar).toHaveBeenCalledWith(destino);
  });

  it('"Marcar todas como leídas" llama al store y está deshabilitado sin no leídas', async () => {
    await crear([crearNotificacion(1)], 1);
    await abrir();

    const marcar = document.querySelector<HTMLButtonElement>('button[data-accion="marcar-todas"]')!;
    expect(marcar.disabled).toBe(false);
    marcar.click();
    await fixture.whenStable();

    expect(store.marcarTodas).toHaveBeenCalledTimes(1);
  });

  it('con todo leído el botón de marcar todas está deshabilitado', async () => {
    await crear([crearNotificacion(1, { leida: true })], 0);
    await abrir();

    const marcar = document.querySelector<HTMLButtonElement>('button[data-accion="marcar-todas"]')!;
    expect(marcar.disabled).toBe(true);
  });
});
