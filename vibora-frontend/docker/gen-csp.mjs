// Genera el snippet de nginx con la Content-Security-Policy a partir del index.html ya compilado.
//
// Angular deja un <script> en linea en index.html (activa las hojas de estilo diferidas). En vez de abrir la CSP
// con 'unsafe-inline' para scripts, se calcula el hash SHA-256 de cada script en linea y solo esos quedan permitidos.
// Uso: node docker/gen-csp.mjs <index.html> <salida.conf>
import { createHash } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';

const [, , entrada, salida] = process.argv;
if (!entrada || !salida) {
  console.error('Uso: node docker/gen-csp.mjs <index.html> <salida.conf>');
  process.exit(1);
}

const html = readFileSync(entrada, 'utf8');
const hashes = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map(
  (m) => `'sha256-${createHash('sha256').update(m[1]).digest('base64')}'`,
);

const politica = [
  "default-src 'self'",
  ["script-src 'self'", ...hashes].join(' '),
  // Angular Material usa estilos en linea; las fuentes vienen de Google Fonts (index.html)
  "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
  "font-src 'self' https://fonts.gstatic.com data:",
  "img-src 'self' data:",
  // 'self' cubre tambien ws:// y wss:// del mismo origen (STOMP en /ws)
  "connect-src 'self'",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "frame-ancestors 'none'",
].join('; ');

writeFileSync(salida, `add_header Content-Security-Policy "${politica}" always;\n`);
console.log(`CSP escrita en ${salida} (${hashes.length} script(s) en linea con hash)`);
