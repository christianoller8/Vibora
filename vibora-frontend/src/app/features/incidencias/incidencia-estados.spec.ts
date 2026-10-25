import {
  EstadoIncidencia,
  ESTADOS_INCIDENCIA,
  IncidenciaResumen,
} from '../../shared/models/incidencia.model';
import { Usuario } from '../../shared/models/usuario.model';
import { USUARIO_ADMIN, USUARIO_JUGADOR } from '../../testing/test-utils';
import {
  AccionIncidencia,
  accionesDisponibles,
  contextoDeIncidencia,
  estadoDestino,
  puedeAdjuntarFotos,
  puedeComentar,
} from './incidencia-estados';

/*
 * Espejo de IncidenciaService.cambiarEstado / asignar del backend:
 *  - asignar (solo admin): ABIERTA, ASIGNADA, EN_CURSO
 *  - ABIERTA -> RECHAZADA: admin
 *  - ASIGNADA -> EN_CURSO: admin o asignado
 *  - EN_CURSO -> RESUELTA: admin o asignado
 *  - RESUELTA -> CERRADA / EN_CURSO (reabrir): admin o reportero
 *  - CERRADA y RECHAZADA: terminales
 * Clave de cada caso: A = administrador del club, S = asignado (mantenimiento), R = reportero ("-" = no).
 */
const ASIGNAR_Y_RECHAZAR: AccionIncidencia[] = ['ASIGNAR', 'RECHAZAR'];
const ASIGNAR_E_INICIAR: AccionIncidencia[] = ['ASIGNAR', 'INICIAR'];
const ASIGNAR_Y_RESOLVER: AccionIncidencia[] = ['ASIGNAR', 'RESOLVER'];
const CERRAR_O_REABRIR: AccionIncidencia[] = ['CERRAR', 'REABRIR'];

const ESPERADO: Record<EstadoIncidencia, Record<string, AccionIncidencia[]>> = {
  ABIERTA: {
    '---': [],
    '--R': [],
    '-S-': [],
    '-SR': [],
    'A--': ASIGNAR_Y_RECHAZAR,
    'A-R': ASIGNAR_Y_RECHAZAR,
    'AS-': ASIGNAR_Y_RECHAZAR,
    ASR: ASIGNAR_Y_RECHAZAR,
  },
  ASIGNADA: {
    '---': [],
    '--R': [],
    '-S-': ['INICIAR'],
    '-SR': ['INICIAR'],
    'A--': ASIGNAR_E_INICIAR,
    'A-R': ASIGNAR_E_INICIAR,
    'AS-': ASIGNAR_E_INICIAR,
    ASR: ASIGNAR_E_INICIAR,
  },
  EN_CURSO: {
    '---': [],
    '--R': [],
    '-S-': ['RESOLVER'],
    '-SR': ['RESOLVER'],
    'A--': ASIGNAR_Y_RESOLVER,
    'A-R': ASIGNAR_Y_RESOLVER,
    'AS-': ASIGNAR_Y_RESOLVER,
    ASR: ASIGNAR_Y_RESOLVER,
  },
  RESUELTA: {
    '---': [],
    '--R': CERRAR_O_REABRIR,
    '-S-': [],
    '-SR': CERRAR_O_REABRIR,
    'A--': CERRAR_O_REABRIR,
    'A-R': CERRAR_O_REABRIR,
    'AS-': CERRAR_O_REABRIR,
    ASR: CERRAR_O_REABRIR,
  },
  CERRADA: { '---': [], '--R': [], '-S-': [], '-SR': [], 'A--': [], 'A-R': [], 'AS-': [], ASR: [] },
  RECHAZADA: {
    '---': [],
    '--R': [],
    '-S-': [],
    '-SR': [],
    'A--': [],
    'A-R': [],
    'AS-': [],
    ASR: [],
  },
};

describe('accionesDisponibles (matriz estado × rol)', () => {
  for (const estado of ESTADOS_INCIDENCIA) {
    for (const [clave, esperado] of Object.entries(ESPERADO[estado])) {
      it(`${estado} con [${clave}] ofrece [${esperado.join(', ')}]`, () => {
        const contexto = {
          esAdmin: clave[0] === 'A',
          esAsignado: clave[1] === 'S',
          esReportero: clave[2] === 'R',
        };
        expect(accionesDisponibles(estado, contexto)).toEqual(esperado);
      });
    }
  }

  it('la matriz cubre los 6 estados × 8 combinaciones de rol', () => {
    expect(Object.keys(ESPERADO)).toHaveLength(6);
    for (const estado of ESTADOS_INCIDENCIA) {
      expect(Object.keys(ESPERADO[estado])).toHaveLength(8);
    }
  });
});

describe('estadoDestino', () => {
  it.each<[Exclude<AccionIncidencia, 'ASIGNAR'>, EstadoIncidencia]>([
    ['INICIAR', 'EN_CURSO'],
    ['RESOLVER', 'RESUELTA'],
    ['CERRAR', 'CERRADA'],
    ['REABRIR', 'EN_CURSO'],
    ['RECHAZAR', 'RECHAZADA'],
  ])('%s lleva a %s', (accion, destino) => {
    expect(estadoDestino(accion)).toBe(destino);
  });
});

describe('puedeComentar / puedeAdjuntarFotos', () => {
  it.each<[EstadoIncidencia, boolean]>([
    ['ABIERTA', true],
    ['ASIGNADA', true],
    ['EN_CURSO', true],
    ['RESUELTA', true],
    ['CERRADA', false],
    ['RECHAZADA', false],
  ])('en %s: %s', (estado, esperado) => {
    expect(puedeComentar(estado)).toBe(esperado);
    expect(puedeAdjuntarFotos(estado)).toBe(esperado);
  });
});

describe('contextoDeIncidencia', () => {
  const incidencia: Pick<IncidenciaResumen, 'clubId' | 'reportadaPor' | 'asignadaA'> = {
    clubId: 5,
    reportadaPor: { id: 1, nombre: 'Ana García López' },
    asignadaA: { id: 20, nombre: 'Pepe Mantenimiento' },
  };
  const mantenimiento: Usuario = {
    ...USUARIO_ADMIN,
    id: 20,
    rol: 'MANTENIMIENTO',
    clubId: 5,
  };

  it('el administrador de ESE club es admin', () => {
    expect(contextoDeIncidencia(incidencia, USUARIO_ADMIN)).toEqual({
      esAdmin: true,
      esAsignado: false,
      esReportero: false,
    });
  });

  it('el administrador de OTRO club no lo es', () => {
    const otro: Usuario = { ...USUARIO_ADMIN, clubId: 99 };
    expect(contextoDeIncidencia(incidencia, otro).esAdmin).toBe(false);
  });

  it('quien la reportó es reportero (por id)', () => {
    expect(contextoDeIncidencia(incidencia, USUARIO_JUGADOR)).toEqual({
      esAdmin: false,
      esAsignado: false,
      esReportero: true,
    });
  });

  it('el mantenimiento asignado, del mismo club, es asignado', () => {
    expect(contextoDeIncidencia(incidencia, mantenimiento).esAsignado).toBe(true);
  });

  it('el mantenimiento con el mismo id pero de otro club no cuenta como asignado', () => {
    expect(contextoDeIncidencia(incidencia, { ...mantenimiento, clubId: 6 }).esAsignado).toBe(
      false,
    );
  });

  it('quien tiene el id del asignado pero ya no es de mantenimiento (ex-personal) no cuenta', () => {
    expect(contextoDeIncidencia(incidencia, { ...mantenimiento, rol: 'JUGADOR' }).esAsignado).toBe(
      false,
    );
  });

  it('sin asignado nadie es asignado', () => {
    expect(contextoDeIncidencia({ ...incidencia, asignadaA: null }, mantenimiento).esAsignado).toBe(
      false,
    );
  });

  it('sin usuario no es nada', () => {
    expect(contextoDeIncidencia(incidencia, null)).toEqual({
      esAdmin: false,
      esAsignado: false,
      esReportero: false,
    });
  });
});
