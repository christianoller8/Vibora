import { test } from 'node:test';
import assert from 'node:assert/strict';
import { fechaMadridMasDias, instanteMadrid, fechaYHoraMadrid } from './fechas.mjs';

test('instanteMadrid: invierno (CET, UTC+1)', () => {
  assert.equal(instanteMadrid('2026-01-15', '18:00').toISOString(), '2026-01-15T17:00:00.000Z');
});

test('instanteMadrid: verano (CEST, UTC+2)', () => {
  assert.equal(instanteMadrid('2026-07-15', '18:00').toISOString(), '2026-07-15T16:00:00.000Z');
});

test('instanteMadrid: cambio de marzo (29/03/2026), antes y despues', () => {
  assert.equal(instanteMadrid('2026-03-28', '18:00').toISOString(), '2026-03-28T17:00:00.000Z'); // CET
  assert.equal(instanteMadrid('2026-03-29', '00:30').toISOString(), '2026-03-28T23:30:00.000Z'); // aun CET
  assert.equal(instanteMadrid('2026-03-29', '18:00').toISOString(), '2026-03-29T16:00:00.000Z'); // ya CEST
  assert.equal(instanteMadrid('2026-03-30', '18:00').toISOString(), '2026-03-30T16:00:00.000Z');
});

test('instanteMadrid: cambio de octubre (25/10/2026), antes y despues', () => {
  assert.equal(instanteMadrid('2026-10-24', '18:00').toISOString(), '2026-10-24T16:00:00.000Z'); // CEST
  assert.equal(instanteMadrid('2026-10-25', '18:00').toISOString(), '2026-10-25T17:00:00.000Z'); // ya CET
  assert.equal(instanteMadrid('2026-10-26', '18:00').toISOString(), '2026-10-26T17:00:00.000Z');
});

test('instanteMadrid: medianoche local de Madrid no es medianoche UTC', () => {
  assert.equal(instanteMadrid('2026-07-01', '00:00').toISOString(), '2026-06-30T22:00:00.000Z');
  assert.equal(instanteMadrid('2026-01-01', '00:00').toISOString(), '2025-12-31T23:00:00.000Z');
});

test('instanteMadrid: rechaza formatos invalidos', () => {
  assert.throws(() => instanteMadrid('15/01/2026', '18:00'));
  assert.throws(() => instanteMadrid('2026-01-15', '6pm'));
});

test('fechaMadridMasDias: 00:30 en Madrid sigue siendo "ayer" en UTC', () => {
  // 22:30Z del 23/10 = 00:30 del 24/10 en Madrid (CEST)
  const ahora = new Date('2026-10-23T22:30:00Z');
  assert.equal(fechaMadridMasDias(ahora, 0), '2026-10-24');
  assert.equal(fechaMadridMasDias(ahora, 3), '2026-10-27');
});

test('fechaMadridMasDias: 23:59 en Madrid sigue siendo el mismo dia', () => {
  const ahora = new Date('2026-10-23T21:59:00Z'); // 23:59 CEST
  assert.equal(fechaMadridMasDias(ahora, 0), '2026-10-23');
  assert.equal(fechaMadridMasDias(ahora, 3), '2026-10-26');
});

test('fechaMadridMasDias: invierno, 23:30Z ya es el dia siguiente en Madrid (CET)', () => {
  assert.equal(fechaMadridMasDias(new Date('2026-01-15T23:30:00Z'), 0), '2026-01-16');
});

test('fechaMadridMasDias: suma dias de calendario aunque haya dias de 23 o 25 horas', () => {
  assert.equal(fechaMadridMasDias(new Date('2026-03-27T12:00:00Z'), 3), '2026-03-30'); // cruza el cambio de marzo
  assert.equal(fechaMadridMasDias(new Date('2026-10-23T12:00:00Z'), 3), '2026-10-26'); // cruza el cambio de octubre
  assert.equal(fechaMadridMasDias(new Date('2026-12-30T12:00:00Z'), 3), '2027-01-02'); // cruza el fin de anyo
});

test('fechaYHoraMadrid: descompone un instante en fecha y hora locales', () => {
  assert.deepEqual(fechaYHoraMadrid(new Date('2026-10-26T17:00:00Z')), { fecha: '2026-10-26', hora: '18:00' });
  assert.deepEqual(fechaYHoraMadrid(new Date('2026-07-15T16:00:00Z')), { fecha: '2026-07-15', hora: '18:00' });
  assert.deepEqual(fechaYHoraMadrid(new Date('2026-06-30T22:00:00Z')), { fecha: '2026-07-01', hora: '00:00' });
});
