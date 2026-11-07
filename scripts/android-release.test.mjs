// node --test scripts/android-release.test.mjs
// Solo los caminos de error (no lanza Gradle ni toca la firma real): el HOME es una carpeta temporal.
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';

const SCRIPT = join(dirname(fileURLToPath(import.meta.url)), 'android-release.mjs');

/** Ejecuta el script con un HOME falso; `propiedades` es el contenido de release-signing.properties (o null). */
function ejecutar(propiedades) {
  const casa = mkdtempSync(join(tmpdir(), 'rally-release-'));
  try {
    if (propiedades !== null) {
      mkdirSync(join(casa, '.rallycopilot'));
      writeFileSync(join(casa, '.rallycopilot', 'release-signing.properties'), propiedades);
    }
    const entorno = { HOME: casa, USERPROFILE: casa, GIT_TERMINAL_PROMPT: '0' };
    if (process.env.SystemRoot) entorno.SystemRoot = process.env.SystemRoot;
    return spawnSync(process.execPath, [SCRIPT, '--version'], { encoding: 'utf8', env: entorno });
  } finally {
    rmSync(casa, { recursive: true, force: true });
  }
}

test('sin fichero de firma falla y explica cómo crearlo', () => {
  const r = ejecutar(null);
  assert.equal(r.status, 1);
  assert.match(r.stderr, /falta \.rallycopilot\/release-signing\.properties/);
  assert.match(r.stderr, /crear-keystore\.py/);
});

test('con claves incompletas nombra las que faltan, nunca los valores', () => {
  const secreto = 'valor-secreto-de-prueba-123';
  const r = ejecutar(`RALLY_KEYSTORE_FILE=/tmp/x.jks\nRALLY_KEYSTORE_PASSWORD=${secreto}\n`);
  assert.equal(r.status, 1);
  assert.match(r.stderr, /faltan claves en el fichero de firma: RALLY_KEY_ALIAS, RALLY_KEY_PASSWORD/);
  assert.doesNotMatch(`${r.stdout}${r.stderr}`, new RegExp(secreto));
});

test('ignora líneas que no son claves de firma y vacías cuentan como faltantes', () => {
  const r = ejecutar('# comentario\nOTRA=1\nRALLY_KEYSTORE_FILE=\nRALLY_KEYSTORE_PASSWORD=a\nRALLY_KEY_ALIAS=b\nRALLY_KEY_PASSWORD=c\n');
  assert.equal(r.status, 1);
  assert.match(r.stderr, /faltan claves en el fichero de firma: RALLY_KEYSTORE_FILE$/m);
});
