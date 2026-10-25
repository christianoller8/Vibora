// Politica Content-Security-Policy de la SPA, separada del generador para poder probarla (csp.test.mjs).
import { createHash } from 'node:crypto';

/**
 * Hash SHA-256 (formato CSP) de cada <script> en linea del index.html.
 *
 * Angular deja un <script> en linea en index.html (activa las hojas de estilo diferidas). En vez de abrir la CSP
 * con 'unsafe-inline' para scripts, solo quedan permitidos los scripts cuyo hash se calcula aqui.
 */
export function hashesDeScripts(html) {
  return [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map(
    (m) => `'sha256-${createHash('sha256').update(m[1]).digest('base64')}'`,
  );
}

export function construirPolitica(html) {
  return [
    "default-src 'self'",
    ["script-src 'self'", ...hashesDeScripts(html)].join(' '),
    // Angular Material usa estilos en linea; las fuentes vienen de Google Fonts (index.html)
    "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
    "font-src 'self' https://fonts.gstatic.com data:",
    // blob: para las fotos de incidencias: se descargan con HttpClient (JWT) y se muestran con URL.createObjectURL
    "img-src 'self' data: blob:",
    // 'self' cubre tambien ws:// y wss:// del mismo origen (STOMP en /ws)
    "connect-src 'self'",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'",
  ].join('; ');
}
