import { Club } from '../../shared/models/club.model';
import {
  CLAVE_CLUB_INCIDENCIAS,
  elegirClubInicial,
  guardarClubPreferido,
  leerClubPreferido,
} from './club-preferido';

function club(id: number): Club {
  return {
    id,
    nombre: `Club ${id}`,
    direccion: null,
    ciudad: null,
    horaApertura: '08:00:00',
    horaCierre: '22:00:00',
  };
}

describe('elegirClubInicial', () => {
  const clubes = [club(3), club(5), club(8)];

  it('prefiere el club del usuario', () => {
    expect(elegirClubInicial(clubes, 5, 8)).toBe(5);
  });

  it('sin club propio usa el último elegido', () => {
    expect(elegirClubInicial(clubes, null, 8)).toBe(8);
  });

  it('si el club propio no está en la lista cae en el último elegido', () => {
    expect(elegirClubInicial(clubes, 99, 8)).toBe(8);
  });

  it('si el último elegido ya no existe cae en el primer club', () => {
    expect(elegirClubInicial(clubes, null, 77)).toBe(3);
  });

  it('sin nada que preferir usa el primero', () => {
    expect(elegirClubInicial(clubes, null, null)).toBe(3);
  });

  it('sin clubes devuelve null', () => {
    expect(elegirClubInicial([], 5, 8)).toBeNull();
  });
});

describe('club preferido en localStorage', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it('guarda y lee el id', () => {
    guardarClubPreferido(8);

    expect(localStorage.getItem(CLAVE_CLUB_INCIDENCIAS)).toBe('8');
    expect(leerClubPreferido()).toBe(8);
  });

  it('sin nada guardado devuelve null', () => {
    expect(leerClubPreferido()).toBeNull();
  });

  it.each(['abc', '', '-3', '0', '3.5'])('un valor corrupto ("%s") se ignora', (valor) => {
    localStorage.setItem(CLAVE_CLUB_INCIDENCIAS, valor);

    expect(leerClubPreferido()).toBeNull();
  });

  it('si el almacenamiento falla al leer devuelve null sin lanzar', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('bloqueado');
    });

    expect(leerClubPreferido()).toBeNull();
  });

  it('si el almacenamiento falla al guardar no lanza', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('cuota');
    });

    expect(() => guardarClubPreferido(8)).not.toThrow();
  });
});
