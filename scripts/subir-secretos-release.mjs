#!/usr/bin/env node
// Sube a GitHub Actions los 4 secretos de firma del workflow android.yml desde
// %USERPROFILE%/.rallycopilot/release-signing.properties y su keystore. Los valores van por stdin a `gh secret set`;
// solo se imprime el nombre de cada secreto y si se subió.
// Uso: node scripts/subir-secretos-release.mjs [--entorno <nombre>]
//   --entorno release  sube los secretos al Environment `release` (restringido a main) en vez de al repositorio
//                      (`gh secret set --env release`); es lo que usan los jobs release y play de android.yml.

import { existsSync, readFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';
import { gh } from './gh.mjs';

const REPO = 'trombonet12/Rally-Copilot';

// Opcional: `--entorno <nombre>` (o `--entorno=<nombre>`) apunta a un Environment de GitHub.
let entorno = null;
const args = process.argv.slice(2);
for (let i = 0; i < args.length; i++) {
  if (args[i] === '--entorno') entorno = args[++i] ?? '';
  else if (args[i].startsWith('--entorno=')) entorno = args[i].slice('--entorno='.length);
  else {
    console.error(`subir-secretos: argumento desconocido '${args[i]}' (uso: [--entorno <nombre>])`);
    process.exit(1);
  }
}
if (entorno !== null && !/^[A-Za-z0-9_.-]+$/.test(entorno)) {
  console.error('subir-secretos: --entorno necesita un nombre valido (letras, digitos, _ . -)');
  process.exit(1);
}
const propsFile = join(homedir(), '.rallycopilot', 'release-signing.properties');
if (!existsSync(propsFile)) {
  console.error('subir-secretos: falta .rallycopilot/release-signing.properties');
  process.exit(1);
}
const props = {};
for (const line of readFileSync(propsFile, 'utf8').split(/\r?\n/)) {
  const m = /^([A-Z_]+)=(.*)$/.exec(line.trim());
  if (m) props[m[1]] = m[2].trim();
}
const keystore = props.RALLY_KEYSTORE_FILE;
if (!keystore || !existsSync(keystore)) {
  console.error('subir-secretos: RALLY_KEYSTORE_FILE no apunta a un archivo existente');
  process.exit(1);
}

const secretos = {
  ANDROID_KEYSTORE_PASSWORD: props.RALLY_KEYSTORE_PASSWORD,
  ANDROID_KEY_ALIAS: props.RALLY_KEY_ALIAS,
  ANDROID_KEY_PASSWORD: props.RALLY_KEY_PASSWORD,
  ANDROID_KEYSTORE_BASE64: readFileSync(keystore).toString('base64'),
};

let fallos = 0;
for (const [nombre, valor] of Object.entries(secretos)) {
  if (!valor) {
    console.log(`${nombre}: falta en el archivo`);
    fallos++;
    continue;
  }
  const r = gh(['secret', 'set', nombre, '-R', REPO, ...(entorno ? ['--env', entorno] : [])], { input: valor });
  const ok = r.status === 0;
  console.log(`${nombre}: ${ok ? 'ok' : 'fallo'}`);
  if (!ok) {
    fallos++;
    console.log(`  ${(r.stderr ?? '').trim()}`);
  }
}
process.exit(fallos ? 1 : 0);
