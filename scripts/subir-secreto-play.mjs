#!/usr/bin/env node
// Sube a GitHub Actions el secreto PLAY_SERVICE_ACCOUNT_JSON (job `play` de android.yml, docs/play.md) desde la clave
// JSON de la cuenta de servicio guardada en %USERPROFILE%/.rallycopilot/. Comprueba la forma del JSON y lo manda por stdin
// a `gh secret set`; solo imprime comprobaciones (sí/no) y el resultado, nunca valores.
// Uso: node scripts/subir-secreto-play.mjs [ruta.json]   (sin ruta: el único *.json de ~/.rallycopilot)

import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';
import { gh } from './gh.mjs';

const REPO = 'trombonet12/Rally-Copilot';
const NOMBRE = 'PLAY_SERVICE_ACCOUNT_JSON';
const dir = join(homedir(), '.rallycopilot');

let archivo = process.argv[2];
if (!archivo) {
  const jsons = existsSync(dir) ? readdirSync(dir).filter((f) => f.endsWith('.json')) : [];
  if (jsons.length !== 1) {
    console.error(`subir-secreto-play: en .rallycopilot hay ${jsons.length} archivos .json; pasa la ruta como argumento`);
    process.exit(1);
  }
  archivo = join(dir, jsons[0]);
}

let clave;
try {
  clave = JSON.parse(readFileSync(archivo, 'utf8'));
} catch {
  console.error('subir-secreto-play: el archivo no es JSON válido');
  process.exit(1);
}

const comprobaciones = {
  'type = service_account': clave.type === 'service_account',
  'project_id': typeof clave.project_id === 'string' && clave.project_id.length > 0,
  'client_email de cuenta de servicio': /@.+\.iam\.gserviceaccount\.com$/.test(clave.client_email ?? ''),
  'private_key PEM': /-----BEGIN PRIVATE KEY-----/.test(clave.private_key ?? ''),
  'token_uri': typeof clave.token_uri === 'string' && clave.token_uri.startsWith('https://'),
};
let mal = 0;
for (const [que, ok] of Object.entries(comprobaciones)) {
  console.log(`${ok ? 'ok   ' : 'FALTA'} ${que}`);
  if (!ok) mal++;
}
if (mal) {
  console.error('subir-secreto-play: el JSON no parece una clave de cuenta de servicio; no se sube nada');
  process.exit(1);
}

const r = gh(['secret', 'set', NOMBRE, '-R', REPO], { input: JSON.stringify(clave) });
console.log(`${NOMBRE}: ${r.status === 0 ? 'ok' : 'fallo'}`);
if (r.status !== 0) console.log(`  ${(r.stderr ?? '').trim()}`);
process.exit(r.status === 0 ? 0 : 1);
