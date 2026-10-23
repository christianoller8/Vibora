import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NotificacionesStore } from '../../notificaciones/notificaciones.store';
import { TiempoRealService } from '../../notificaciones/tiempo-real.service';
import { Router, provideRouter } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { crearAuthFalso } from '../../../testing/test-utils';
import { MainLayout } from './main-layout';

describe('MainLayout', () => {
  const storeFalso = {
    items: signal([]),
    noLeidas: signal(0),
    marcarLeida: vi.fn(),
    marcarTodas: vi.fn(),
  };
  let tiempoRealCreado = false;
  let component: MainLayout;
  let fixture: ComponentFixture<MainLayout>;

  async function crear(auth: ReturnType<typeof crearAuthFalso>): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [MainLayout],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: NotificacionesStore, useValue: storeFalso },
        {
          provide: TiempoRealService,
          useFactory: () => {
            tiempoRealCreado = true;
            return {};
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(MainLayout);
    component = fixture.componentInstance;
    await fixture.whenStable();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;

  afterEach(() => vi.restoreAllMocks());

  it('should create', async () => {
    await crear(crearAuthFalso());
    expect(component).toBeTruthy();
  });

  it('muestra la marca, el nombre del usuario y su rol cuando hay sesión', async () => {
    await crear(crearAuthFalso());

    expect(el().querySelector('mat-toolbar')?.textContent).toContain('Víbora');
    expect(el().querySelector('mat-toolbar')?.textContent).toContain('Ana');
    expect(el().querySelector('mat-chip, .rol')?.textContent).toContain('Jugador');
  });

  it('muestra las entradas Agenda y Mis reservas cuando hay sesión', async () => {
    await crear(crearAuthFalso());

    const enlaces = Array.from(el().querySelectorAll('a[mat-list-item]')).map((a) =>
      a.textContent?.trim(),
    );
    expect(enlaces.some((t) => t?.includes('Agenda'))).toBe(true);
    expect(enlaces.some((t) => t?.includes('Mis reservas'))).toBe(true);
  });

  it('muestra también Partidos y Deudas, con sus iconos y rutas', async () => {
    await crear(crearAuthFalso());

    const enlaces = Array.from(el().querySelectorAll<HTMLAnchorElement>('a[mat-list-item]'));
    const porTexto = (texto: string) => enlaces.find((a) => a.textContent?.includes(texto));
    expect(porTexto('Partidos')?.getAttribute('href')).toBe('/partidos');
    expect(porTexto('Partidos')?.querySelector('mat-icon')?.textContent?.trim()).toBe('groups');
    expect(porTexto('Deudas')?.getAttribute('href')).toBe('/deudas');
    expect(porTexto('Deudas')?.querySelector('mat-icon')?.textContent?.trim()).toBe('payments');
    expect(enlaces.map((a) => a.getAttribute('href'))).toEqual([
      '/agenda',
      '/mis-reservas',
      '/partidos',
      '/deudas',
    ]);
  });

  it('muestra la campana de notificaciones en la barra solo con sesión', async () => {
    await crear(crearAuthFalso());
    expect(el().querySelector('mat-toolbar app-campana')).not.toBeNull();
  });

  it('arranca el servicio de tiempo real al montarse (fuera del bundle inicial)', async () => {
    tiempoRealCreado = false;
    await crear(crearAuthFalso());
    expect(tiempoRealCreado).toBe(true);
  });

  it('no muestra la campana sin sesión', async () => {
    await crear(crearAuthFalso(null));
    expect(el().querySelector('app-campana')).toBeNull();
  });

  it('oculta el menú, el usuario y el botón de salir sin sesión', async () => {
    await crear(crearAuthFalso(null));

    expect(el().querySelectorAll('a[mat-list-item]')).toHaveLength(0);
    expect(el().querySelector('button[data-accion="salir"]')).toBeNull();
    expect(el().querySelector('mat-chip, .rol')).toBeNull();
  });

  it('al salir cierra la sesión y navega a /login', async () => {
    const auth = crearAuthFalso();
    await crear(auth);
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    el().querySelector<HTMLButtonElement>('button[data-accion="salir"]')!.click();

    expect(auth.logout).toHaveBeenCalledTimes(1);
    expect(navegar).toHaveBeenCalledWith('/login');
  });
});
