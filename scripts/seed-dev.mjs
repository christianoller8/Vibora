#!/usr/bin/env node
// Datos de ejemplo para desarrollo de Vibora. Idempotente: se puede ejecutar varias veces.
//
//   node scripts/seed-dev.mjs [--base-url http://localhost:8081]
//
// Requiere Node >= 20 (fetch integrado) y el backend en marcha. SOLO DESARROLLO.

import { fechaMadridMasDias, instanteMadrid, fechaYHoraMadrid } from './lib/fechas.mjs';

// ---------------------------------------------------------------- configuracion

const BASE_URL_POR_DEFECTO = 'http://localhost:8081';
const HOSTS_PERMITIDOS = ['localhost', '127.0.0.1', '[::1]'];
const FRONTEND_URL = 'http://localhost:4200';

/** Contrasena comun de TODOS los usuarios de ejemplo (conocida: nunca usar fuera de desarrollo). */
const PASSWORD_DEV = 'Vibora-dev-2026';

const DOMINIO = 'vibora.test';
const ADMIN = { email: `admin@${DOMINIO}`, nombre: 'Admin', apellidos: 'Demo' };
const JUGADORES = [1, 2, 3, 4].map((n) => ({
  email: `jugador${n}@${DOMINIO}`,
  nombre: `Jugador${n}`,
  apellidos: 'Demo',
}));

const CLUB = {
  nombre: 'Club Vibora Demo',
  direccion: 'Calle Demo 1',
  ciudad: 'Madrid',
  horaApertura: '09:00',
  horaCierre: '21:00',
};

const PISTAS = [
  { nombre: 'Pista 1', tipo: 'INTERIOR' },
  { nombre: 'Pista 2', tipo: 'EXTERIOR' },
];

const DIAS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
const FRANJAS = [
  { horaInicio: '09:00', horaFin: '18:00', precioHora: 20.0, tipo: 'NORMAL' },
  { horaInicio: '18:00', horaFin: '21:00', precioHora: 30.0, tipo: 'HORA_PUNTA' },
];

const DIAS_ADELANTE = 3;
const HORA_PARTIDO = '18:00';
const PISTA_PARTIDO = 'Pista 1';

// ---------------------------------------------------------------- utilidades

class ErrorSeed extends Error {}

const log = (msg) => console.log(msg);
const paso = (icono, msg) => log(`  ${icono} ${msg}`);

function parsearArgumentos(argv) {
  let baseUrl = BASE_URL_POR_DEFECTO;
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--base-url') {
      baseUrl = argv[++i];
      if (!baseUrl) throw new ErrorSeed('Falta el valor de --base-url');
    } else if (a.startsWith('--base-url=')) {
      baseUrl = a.slice('--base-url='.length);
    } else if (a === '--help' || a === '-h') {
      log('Uso: node scripts/seed-dev.mjs [--base-url http://localhost:8081]');
      process.exit(0);
    } else {
      throw new ErrorSeed(`Argumento desconocido: ${a}`);
    }
  }
  let url;
  try {
    url = new URL(baseUrl);
  } catch {
    throw new ErrorSeed(`--base-url no es una URL valida: ${baseUrl}`);
  }
  if (!HOSTS_PERMITIDOS.includes(url.hostname)) {
    throw new ErrorSeed(
      `Por seguridad este script solo se ejecuta contra ${HOSTS_PERMITIDOS.join(', ')} ` +
        `(crea usuarios con una contrasena conocida). Host recibido: ${url.hostname}`,
    );
  }
  return { baseUrl: baseUrl.replace(/\/+$/, '') };
}

/** Cliente HTTP minimo: devuelve { status, data } y traduce fallos de red a un mensaje legible. */
function crearCliente(baseUrl) {
  return async function api(metodo, ruta, { token, body } = {}) {
    const cabeceras = { Accept: 'application/json' };
    if (body !== undefined) cabeceras['Content-Type'] = 'application/json';
    if (token) cabeceras.Authorization = `Bearer ${token}`;
    let res;
    try {
      res = await fetch(baseUrl + ruta, {
        method: metodo,
        headers: cabeceras,
        body: body === undefined ? undefined : JSON.stringify(body),
      });
    } catch (e) {
      throw new ErrorSeed(
        `No se pudo conectar con el backend en ${baseUrl} (${e.cause?.code ?? e.message}). ` +
          'Esta arrancado? (cd vibora-backend && ./mvnw spring-boot:run)',
      );
    }
    const texto = await res.text();
    let data = null;
    if (texto) {
      try {
        data = JSON.parse(texto);
      } catch {
        data = texto;
      }
    }
    return { status: res.status, data };
  };
}

/** Exige que el estado sea uno de los esperados; si no, falla con estado + {"error"} del backend. */
function exigir(res, esperados, descripcion) {
  if (esperados.includes(res.status)) return res.data;
  const detalle = res.data && typeof res.data === 'object' ? (res.data.error ?? JSON.stringify(res.data)) : res.data;
  throw new ErrorSeed(`${descripcion}: respuesta inesperada ${res.status}${detalle ? ` - ${detalle}` : ''}`);
}

// ---------------------------------------------------------------- pasos

async function comprobarBackend(api, baseUrl) {
  const res = await api('GET', '/api/auth/me');
  if (res.status !== 401 || typeof res.data !== 'object' || res.data?.error !== 'No autenticado') {
    throw new ErrorSeed(`${baseUrl} responde, pero no parece el backend de Vibora (GET /api/auth/me -> ${res.status}).`);
  }
  paso('ok', `Backend de Vibora alcanzable en ${baseUrl}`);
}

async function login(api, usuario) {
  const res = await api('POST', '/api/auth/login', { body: { email: usuario.email, password: PASSWORD_DEV } });
  if (res.status === 401) {
    throw new ErrorSeed(
      `${usuario.email} existe pero su contrasena no es la del seed. ` +
        'Borra ese usuario o restablece la BD de desarrollo.',
    );
  }
  return exigir(res, [200], `Login de ${usuario.email}`).token;
}

/** Registra (409 = ya existe) e inicia sesion. Devuelve el token (nunca se imprime). */
async function asegurarUsuario(api, usuario) {
  const reg = await api('POST', '/api/auth/registro', {
    body: { email: usuario.email, password: PASSWORD_DEV, nombre: usuario.nombre, apellidos: usuario.apellidos },
  });
  exigir(reg, [201, 409], `Registro de ${usuario.email}`);
  const token = await login(api, usuario);
  paso(reg.status === 201 ? '+' : '=', `${usuario.email} ${reg.status === 201 ? 'creado' : 'ya existia'}`);
  return token;
}

/** Devuelve { club, tokenAdmin } (el token se renueva si el admin acaba de pasar a ADMIN_CLUB). */
async function asegurarClub(api, tokenAdmin) {
  const lista = exigir(await api('GET', '/api/clubes', { token: tokenAdmin }), [200], 'Listar clubes');
  const existente = lista.find((c) => c.nombre === CLUB.nombre);
  if (existente) {
    paso('=', `Club "${CLUB.nombre}" ya existia (id ${existente.id})`);
    return { club: existente, tokenAdmin };
  }
  const res = await api('POST', '/api/clubes', { token: tokenAdmin, body: CLUB });
  if (res.status === 409) {
    throw new ErrorSeed(
      `${ADMIN.email} ya pertenece a otro club distinto de "${CLUB.nombre}". ` +
        'Usa una BD limpia o elimina ese usuario.',
    );
  }
  const club = exigir(res, [201], `Crear club "${CLUB.nombre}"`);
  paso('+', `Club "${CLUB.nombre}" creado (id ${club.id}); ${ADMIN.email} pasa a ADMIN_CLUB`);
  // El rol viaja en el JWT: hay que volver a iniciar sesion para un token ADMIN_CLUB
  const nuevoToken = await login(api, ADMIN);
  paso('>', 'Sesion de admin renovada para obtener el rol ADMIN_CLUB');
  return { club, tokenAdmin: nuevoToken };
}

async function asegurarPistas(api, club, tokenAdmin) {
  const ruta = `/api/clubes/${club.id}/pistas`;
  let existentes = exigir(await api('GET', ruta, { token: tokenAdmin }), [200], 'Listar pistas');
  for (const pista of PISTAS) {
    if (existentes.some((p) => p.nombre === pista.nombre)) {
      paso('=', `${pista.nombre} ya existia`);
      continue;
    }
    const res = await api('POST', ruta, { token: tokenAdmin, body: pista });
    if (res.status === 403) {
      throw new ErrorSeed(`${ADMIN.email} no es administrador del club "${CLUB.nombre}" (403 al crear pistas).`);
    }
    exigir(res, [201], `Crear ${pista.nombre}`);
    paso('+', `${pista.nombre} (${pista.tipo}) creada`);
  }
  existentes = exigir(await api('GET', ruta, { token: tokenAdmin }), [200], 'Listar pistas');
  return existentes;
}

async function asegurarTarifas(api, club, tokenAdmin) {
  const ruta = `/api/clubes/${club.id}/tarifas`;
  const existentes = exigir(await api('GET', ruta, { token: tokenAdmin }), [200], 'Listar tarifas');
  if (existentes.length > 0) {
    paso('=', `El club ya tiene ${existentes.length} franjas tarifarias`);
    return;
  }
  for (const diaSemana of DIAS) {
    for (const franja of FRANJAS) {
      exigir(await api('POST', ruta, { token: tokenAdmin, body: { diaSemana, ...franja } }), [201], `Crear tarifa ${diaSemana}`);
    }
  }
  paso('+', `${DIAS.length * FRANJAS.length} franjas creadas (09-18 NORMAL 20 EUR/h, 18-21 HORA_PUNTA 30 EUR/h, todos los dias)`);
}

/**
 * Partido abierto de ejemplo: jugador1 reserva (o reutiliza) la casilla de las 18:00 de Madrid, la abre y
 * jugador2 se apunta. Devuelve un resumen para el informe final, o null si no se pudo crear.
 */
async function asegurarPartido(api, club, pista, tokens) {
  const ahora = new Date();
  const fecha = fechaMadridMasDias(ahora, DIAS_ADELANTE);
  const inicioDeseado = instanteMadrid(fecha, HORA_PARTIDO);
  const t1 = tokens[JUGADORES[0].email];
  const t2 = tokens[JUGADORES[1].email];

  // 1) Reutilizar un partido seed ya existente (cualquier fecha futura a las 18:00 en esa pista)
  const mias = exigir(await api('GET', '/api/reservas/mias', { token: t1 }), [200], 'Listar reservas de jugador1');
  let reserva = mias.find(
    (r) =>
      r.pistaId === pista.id &&
      r.estado === 'CONFIRMADA' &&
      new Date(r.inicio) > ahora &&
      fechaYHoraMadrid(new Date(r.inicio)).hora === HORA_PARTIDO,
  );
  let creada = false;

  if (reserva) {
    const { fecha: f } = fechaYHoraMadrid(new Date(reserva.inicio));
    paso('=', `Reserva de ejemplo ya existia (${f} ${HORA_PARTIDO}, ${pista.nombre}, id ${reserva.id})`);
  } else {
    // 2) Crear en la casilla libre
    const agenda = exigir(
      await api('GET', `/api/clubes/${club.id}/pistas/${pista.id}/agenda?fecha=${fecha}`, { token: t1 }),
      [200],
      'Consultar agenda',
    );
    const casilla = agenda.find((c) => new Date(c.inicio).getTime() === inicioDeseado.getTime());
    if (!casilla) {
      throw new ErrorSeed(`La casilla de las ${HORA_PARTIDO} del ${fecha} no existe en la rejilla del club.`);
    }
    if (!casilla.disponible) {
      paso('!', `La casilla ${fecha} ${HORA_PARTIDO} en ${pista.nombre} esta ocupada por otra reserva: se omite el partido de ejemplo`);
      return null;
    }
    const res = await api('POST', `/api/clubes/${club.id}/pistas/${pista.id}/reservas`, {
      token: t1,
      body: { inicio: inicioDeseado.toISOString() },
    });
    reserva = exigir(res, [201], 'Reservar casilla');
    creada = true;
    paso('+', `Reserva creada por ${JUGADORES[0].email} (${fecha} ${HORA_PARTIDO}, ${pista.nombre}, id ${reserva.id})`);
  }

  // 3) Marcar como abierta
  if (reserva.abierta) {
    paso('=', 'El partido ya estaba abierto');
  } else {
    reserva = exigir(
      await api('PUT', `/api/reservas/${reserva.id}/abierta`, { token: t1, body: { abierta: true } }),
      [200],
      'Abrir partido',
    );
    paso('+', 'Partido marcado como abierto');
  }

  // 4) jugador2 se apunta (si todavia no lo esta)
  let partidos = exigir(await api('GET', '/api/partidos', { token: t2 }), [200], 'Listar partidos');
  let partido = partidos.find((p) => p.reservaId === reserva.id);
  if (partido?.yaApuntado) {
    paso('=', `${JUGADORES[1].email} ya estaba apuntado`);
  } else {
    const res = await api('POST', `/api/reservas/${reserva.id}/apuntarse`, { token: t2 });
    exigir(res, [201], `Apuntar a ${JUGADORES[1].email}`);
    paso('+', `${JUGADORES[1].email} apuntado al partido`);
  }

  partidos = exigir(await api('GET', '/api/partidos', { token: t2 }), [200], 'Listar partidos');
  partido = partidos.find((p) => p.reservaId === reserva.id);
  if (!partido) throw new ErrorSeed('El partido de ejemplo no aparece en GET /api/partidos');
  const { fecha: f, hora } = fechaYHoraMadrid(new Date(partido.inicio));
  return { creada, fecha: f, hora, pista: partido.pistaNombre, plazasLibres: partido.plazasLibres, reservaId: reserva.id };
}

// ---------------------------------------------------------------- principal

async function main() {
  if (Number(process.versions.node.split('.')[0]) < 20) {
    throw new ErrorSeed(`Se necesita Node >= 20 (version actual: ${process.versions.node}).`);
  }
  const { baseUrl } = parsearArgumentos(process.argv.slice(2));

  log('==================================================================');
  log(' ATENCION: script SOLO PARA DESARROLLO.');
  log(' Crea usuarios con una contrasena conocida (visible en este script).');
  log(' No lo ejecutes contra ningun entorno compartido ni de produccion.');
  log('==================================================================');
  log(`Base URL: ${baseUrl}\n`);

  const api = crearCliente(baseUrl);
  await comprobarBackend(api, baseUrl);

  log('\nUsuarios');
  const tokens = {};
  tokens[ADMIN.email] = await asegurarUsuario(api, ADMIN);
  for (const j of JUGADORES) tokens[j.email] = await asegurarUsuario(api, j);

  log('\nClub');
  const { club, tokenAdmin } = await asegurarClub(api, tokens[ADMIN.email]);
  tokens[ADMIN.email] = tokenAdmin;

  log('\nPistas');
  const pistas = await asegurarPistas(api, club, tokenAdmin);

  log('\nTarifas');
  await asegurarTarifas(api, club, tokenAdmin);

  log('\nPartido abierto');
  const pista = pistas.find((p) => p.nombre === PISTA_PARTIDO);
  const partido = await asegurarPartido(api, club, pista, tokens);

  log('\n==================================================================');
  log(' RESUMEN');
  log('==================================================================');
  log(`Base URL : ${baseUrl}`);
  log(`Club     : ${club.nombre} (id ${club.id}), ${CLUB.horaApertura}-${CLUB.horaCierre}`);
  log(`Pistas   : ${pistas.map((p) => `${p.nombre} (${p.tipo})`).join(', ')}`);
  log('\nCredenciales (contrasena comun)');
  log(`  ${'email'.padEnd(26)} rol`);
  log(`  ${ADMIN.email.padEnd(26)} ADMIN_CLUB`);
  for (const j of JUGADORES) log(`  ${j.email.padEnd(26)} JUGADOR`);
  log(`  contrasena: ${PASSWORD_DEV}`);
  log('\nPartido abierto');
  if (partido) {
    log(`  ${partido.fecha} ${partido.hora} (hora de Madrid), ${partido.pista}, reserva ${partido.reservaId}`);
    log(`  organizador: ${JUGADORES[0].email}; plazas libres: ${partido.plazasLibres}`);
  } else {
    log('  (no creado: la casilla estaba ocupada)');
  }
  log(`\nFrontend: ${FRONTEND_URL}`);
}

main().catch((e) => {
  if (e instanceof ErrorSeed) {
    console.error(`\nERROR: ${e.message}`);
  } else {
    console.error(`\nERROR inesperado: ${e?.message ?? e}`);
  }
  process.exit(1);
});
