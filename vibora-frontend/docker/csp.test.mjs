// Prueba de la politica CSP generada. Uso (desde vibora-frontend): node --test docker/csp.test.mjs
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { test } from 'node:test';
import { construirPolitica, hashesDeScripts } from './csp.mjs';

const SCRIPT = 'window.__estilos = true;';
const HTML = `<html><head><script>${SCRIPT}</script><script src="main.js"></script></head></html>`;

/** Directivas como mapa nombre -> lista de fuentes. */
function directivas(politica) {
  return new Map(
    politica.split('; ').map((d) => {
      const [nombre, ...fuentes] = d.split(' ');
      return [nombre, fuentes];
    }),
  );
}

test('img-src permite blob: (las fotos de incidencias se muestran desde URLs de objeto)', () => {
  const img = directivas(construirPolitica(HTML)).get('img-src');
  assert.deepEqual(img, ["'self'", 'data:', 'blob:']);
});

test('blob: aparece SOLO en img-src', () => {
  for (const [nombre, fuentes] of directivas(construirPolitica(HTML))) {
    if (nombre !== 'img-src') {
      assert.ok(!fuentes.includes('blob:'), `${nombre} no debe permitir blob:`);
    }
  }
});

test('el resto de la politica sigue siendo estricta', () => {
  const d = directivas(construirPolitica(HTML));
  assert.deepEqual(d.get('default-src'), ["'self'"]);
  assert.deepEqual(d.get('connect-src'), ["'self'"]);
  assert.deepEqual(d.get('object-src'), ["'none'"]);
  assert.deepEqual(d.get('frame-ancestors'), ["'none'"]);
  assert.deepEqual(d.get('base-uri'), ["'self'"]);
  assert.deepEqual(d.get('form-action'), ["'self'"]);
  assert.ok(!d.get('script-src').includes("'unsafe-inline'"));
  assert.ok(!d.get('script-src').includes("'unsafe-eval'"));
  assert.ok(!construirPolitica(HTML).includes('*'));
});

test('script-src lleva el hash SHA-256 de cada script en linea y nada mas', () => {
  const esperado = `'sha256-${createHash('sha256').update(SCRIPT).digest('base64')}'`;
  assert.deepEqual(hashesDeScripts(HTML), [esperado]);
  assert.deepEqual(directivas(construirPolitica(HTML)).get('script-src'), ["'self'", esperado]);
});
