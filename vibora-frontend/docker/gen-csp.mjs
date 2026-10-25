// Genera el snippet de nginx con la Content-Security-Policy a partir del index.html ya compilado.
// La politica en si vive en csp.mjs (con su prueba csp.test.mjs).
// Uso: node docker/gen-csp.mjs <index.html> <salida.conf>
import { readFileSync, writeFileSync } from 'node:fs';
import { construirPolitica, hashesDeScripts } from './csp.mjs';

const [, , entrada, salida] = process.argv;
if (!entrada || !salida) {
  console.error('Uso: node docker/gen-csp.mjs <index.html> <salida.conf>');
  process.exit(1);
}

const html = readFileSync(entrada, 'utf8');
const politica = construirPolitica(html);

writeFileSync(salida, `add_header Content-Security-Policy "${politica}" always;\n`);
console.log(
  `CSP escrita en ${salida} (${hashesDeScripts(html).length} script(s) en linea con hash)`,
);
