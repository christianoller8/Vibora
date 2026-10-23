import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { NEVER, Subject, of } from 'rxjs';
import { ClubService } from '../../../core/api/club.service';
import { AuthService } from '../../../core/auth/auth.service';
import { NotificacionesStore } from '../../../core/notificaciones/notificaciones.store';
import { Notificacion } from '../../../shared/models/notificacion.model';
import { USUARIO_ADMIN, crearAuthFalso } from '../../../testing/test-utils';
import { ReservaService } from '../../reservas/reserva.service';
import { PistaService } from '../pista.service';
import { TarifaService } from '../tarifa.service';
import { ClubAdmin } from './club-admin';

describe('ClubAdmin', () => {
  let fixture: ComponentFixture<ClubAdmin>;
  const clubes = { obtener: vi.fn(), actualizar: vi.fn(), pistas: vi.fn() };
  const tarifas = { listar: vi.fn() };
  const reservas = { delClub: vi.fn(), cancelar: vi.fn() };

  async function crear(auth = crearAuthFalso(USUARIO_ADMIN)): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [ClubAdmin],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: ClubService, useValue: clubes },
        { provide: PistaService, useValue: {} },
        { provide: TarifaService, useValue: tarifas },
        { provide: ReservaService, useValue: reservas },
        { provide: NotificacionesStore, useValue: { recibidas$: new Subject<Notificacion>() } },
        { provide: MatDialog, useValue: { open: vi.fn() } },
        { provide: MatSnackBar, useValue: { open: vi.fn() } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ClubAdmin);
    await fixture.whenStable();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const pestanas = (): HTMLElement[] =>
    Array.from(el().querySelectorAll<HTMLElement>('[role="tab"]'));

  beforeEach(() => {
    vi.resetAllMocks();
    clubes.obtener.mockReturnValue(
      of({
        id: 5,
        nombre: 'Club Víbora Demo',
        direccion: null,
        ciudad: null,
        horaApertura: '09:00:00',
        horaCierre: '22:00:00',
      }),
    );
    clubes.pistas.mockReturnValue(NEVER);
    tarifas.listar.mockReturnValue(NEVER);
    reservas.delClub.mockReturnValue(NEVER);
  });

  it('muestra el nombre del club y las cuatro pestañas en orden', async () => {
    await crear();

    expect(el().querySelector('h1')?.textContent).toContain('Mi club');
    expect(el().querySelector('h1')?.textContent).toContain('Club Víbora Demo');
    expect(pestanas().map((p) => p.textContent!.trim())).toEqual([
      'Datos',
      'Pistas',
      'Tarifas',
      'Reservas',
    ]);
  });

  it('arranca en "Datos" y solo carga lo de la pestaña activa', async () => {
    await crear();

    expect(clubes.obtener).toHaveBeenCalledWith(5);
    expect(clubes.pistas).not.toHaveBeenCalled();
    expect(tarifas.listar).not.toHaveBeenCalled();
    expect(reservas.delClub).not.toHaveBeenCalled();
  });

  it('cada pestaña carga sus datos con el club del administrador al abrirla', async () => {
    await crear();

    pestanas()[1].click();
    await fixture.whenStable();
    expect(clubes.pistas).toHaveBeenCalledWith(5);

    pestanas()[2].click();
    await fixture.whenStable();
    expect(tarifas.listar).toHaveBeenCalledWith(5);

    pestanas()[3].click();
    await fixture.whenStable();
    expect(reservas.delClub.mock.calls[0][0]).toBe(5);
  });

  it('sin club en la sesión muestra un aviso en vez de las pestañas', async () => {
    await crear(crearAuthFalso({ ...USUARIO_ADMIN, clubId: null, clubNombre: null }));

    expect(el().querySelector('[role="alert"]')).not.toBeNull();
    expect(pestanas()).toHaveLength(0);
  });
});
