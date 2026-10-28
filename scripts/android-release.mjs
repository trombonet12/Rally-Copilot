#!/usr/bin/env node
// Release Android firmado: carga las variables RALLY_* de %USERPROFILE%/.rallycopilot/release-signing.properties
// (generado una vez con keytool; nunca en el repo) y ejecuta Gradle. No imprime ningún valor de firma.
// Uso: node scripts/android-release.mjs [tareas…]   (por defecto: assembleRelease)

import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const propsFile = join(homedir(), '.rallycopilot', 'release-signing.properties');
const KEYS = [
  'RALLY_KEYSTORE_FILE',
  'RALLY_KEYSTORE_PASSWORD',
  'RALLY_KEY_ALIAS',
  'RALLY_KEY_PASSWORD',
];

if (!existsSync(propsFile)) {
  console.error(
    'android-release: falta .rallycopilot/release-signing.properties (créalo con: python -I scripts/crear-keystore.py)',
  );
  process.exit(1);
}
const env = { ...process.env };
for (const line of readFileSync(propsFile, 'utf8').split(/\r?\n/)) {
  const m = /^([A-Z_]+)=(.*)$/.exec(line.trim());
  if (m && KEYS.includes(m[1])) env[m[1]] = m[2];
}
const missing = KEYS.filter((k) => !env[k]);
if (missing.length > 0) {
  console.error(`android-release: faltan claves en el fichero de firma: ${missing.join(', ')}`);
  process.exit(1);
}

const tasks = process.argv.length > 2 ? process.argv.slice(2) : ['assembleRelease'];
const gradlew = join(ROOT, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew');
const r = spawnSync(process.platform === 'win32' ? `"${gradlew}"` : gradlew, ['--console=plain', ...tasks], {
  cwd: ROOT,
  env,
  stdio: 'inherit',
  shell: process.platform === 'win32',
});
if (r.status !== 0) process.exit(r.status ?? 1);

const outDir = join(ROOT, 'app', 'build', 'outputs', 'apk', 'release');
if (existsSync(outDir)) {
  for (const f of readdirSync(outDir).filter((n) => n.endsWith('.apk'))) {
    const p = join(outDir, f);
    const sha = createHash('sha256').update(readFileSync(p)).digest('hex');
    console.log(`${p}\n  sha256 ${sha}`);
  }
}
