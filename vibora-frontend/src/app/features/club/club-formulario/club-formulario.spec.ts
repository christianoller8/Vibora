import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Club, ClubRequest } from '../../../shared/models/club.model';
import { escribir } from '../../../testing/test-utils';
import { ClubFormulario } from './club-formulario';

const CLUB: Club = {
  id: 5,
  nombre: 'Club Víbora Demo',
  direccion: 'Calle Mayor 1',
  ciudad: 'Madrid',
  horaApertura: '09:00:00',
  horaCierre: '22:00:00',
};

describe('ClubFormulario', () => {
  let fixture: ComponentFixture<ClubFormulario>;
  let enviados: ClubRequest[];

  async function crear(entradas: Record<string, unknown> = {}): Promise<void> {
    await TestBed.configureTestingModule({ imports: [ClubFormulario] }).compileComponents();
    fixture = TestBed.createComponent(ClubFormulario);
    fixture.componentRef.setInput('textoEnviar', 'Guardar');
    for (const [clave, valor] of Object.entries(entradas)) {
      fixture.componentRef.setInput(clave, valor);
    }
    enviados = [];
    fixture.componentInstance.enviar.subscribe((datos) => enviados.push(datos));
    await fixture.whenStable();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const campo = (nombre: string): HTMLInputElement =>
    el().querySelector<HTMLInputElement>(`input[name="${nombre}"]`)!;
  const boton = (): HTMLButtonElement =>
    el().querySelector<HTMLButtonElement>('button[type="submit"]')!;
  const rellenarValido = (): void => {
    escribir(campo('nombre'), 'Club Nuevo');
    escribir(campo('horaApertura'), '09:00');
    escribir(campo('horaCierre'), '22:00');
  };

  it('pinta nombre, dirección, ciudad y las horas como campos de hora', async () => {
    await crear();

    expect(campo('nombre')).not.toBeNull();
    expect(campo('direccion')).not.toBeNull();
    expect(campo('ciudad')).not.toBeNull();
    expect(campo('horaApertura').type).toBe('time');
    expect(campo('horaCierre').type).toBe('time');
    expect(boton().textContent).toContain('Guardar');
  });

  it('precarga los datos del club y recorta los segundos de las horas', async () => {
    await crear({ club: CLUB });
    await fixture.whenStable();

    expect(campo('nombre').value).toBe('Club Víbora Demo');
    expect(campo('direccion').value).toBe('Calle Mayor 1');
    expect(campo('ciudad').value).toBe('Madrid');
    expect(campo('horaApertura').value).toBe('09:00');
    expect(campo('horaCierre').value).toBe('22:00');
  });

  it('el nombre es obligatorio: sin él no se puede enviar', async () => {
    await crear();
    escribir(campo('nombre'), '');
    fixture.detectChanges();

    expect(boton().disabled).toBe(true);
    expect(el().querySelector('mat-error')?.textContent).toContain('nombre es obligatorio');
  });

  it('un nombre solo con espacios tampoco vale', async () => {
    await crear();
    rellenarValido();
    escribir(campo('nombre'), '   ');
    fixture.detectChanges();

    expect(boton().disabled).toBe(true);
  });

  it('exige que el cierre sea posterior a la apertura y lo explica', async () => {
    await crear();
    rellenarValido();
    escribir(campo('horaCierre'), '09:00');
    fixture.detectChanges();

    expect(boton().disabled).toBe(true);
    expect(el().textContent).toContain('La hora de cierre debe ser posterior a la de apertura');
  });

  it('un cierre anterior a la apertura también se rechaza, y al corregirlo se habilita', async () => {
    await crear();
    rellenarValido();
    escribir(campo('horaCierre'), '08:00');
    fixture.detectChanges();
    expect(boton().disabled).toBe(true);

    escribir(campo('horaCierre'), '23:00');
    fixture.detectChanges();
    expect(boton().disabled).toBe(false);
  });

  it('emite la petición con los textos recortados, las horas en HH:mm y null en los opcionales vacíos', async () => {
    await crear();
    rellenarValido();
    escribir(campo('nombre'), '  Club Nuevo  ');
    escribir(campo('direccion'), '   ');
    escribir(campo('ciudad'), ' Getafe ');
    fixture.detectChanges();

    boton().click();

    expect(enviados).toEqual([
      {
        nombre: 'Club Nuevo',
        direccion: null,
        ciudad: 'Getafe',
        horaApertura: '09:00',
        horaCierre: '22:00',
      },
    ]);
  });

  it('con un club precargado envía sus horas como HH:mm', async () => {
    await crear({ club: CLUB });
    await fixture.whenStable();

    boton().click();

    expect(enviados[0].horaApertura).toBe('09:00');
    expect(enviados[0].horaCierre).toBe('22:00');
  });

  it('mientras envía bloquea el botón y no emite de nuevo', async () => {
    await crear({ enviando: true });
    rellenarValido();
    fixture.detectChanges();

    expect(boton().disabled).toBe(true);
  });

  it('muestra el error del servidor como alerta', async () => {
    await crear({ error: 'Ya perteneces a un club' });

    expect(el().querySelector('[role="alert"]')?.textContent).toContain('Ya perteneces a un club');
  });
});
