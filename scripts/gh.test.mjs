// node --test scripts/gh.test.mjs
// Sin red ni secretos reales: solo la lógica de selección de binario y de token.
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { test } from 'node:test';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { ghBinario, tokenGitHub } from './gh.mjs';

const AQUI = dirname(fileURLToPath(import.meta.url));
const GH = join(AQUI, 'gh.mjs');

test('ghBinario devuelve gh o la ruta absoluta de Windows', () => {
  const b = ghBinario();
  assert.ok(b === 'gh' || b === 'C:/Program Files/GitHub CLI/gh.exe', b);
  if (process.platform !== 'win32') assert.equal(b, 'gh');
});

test('tokenGitHub usa GH_TOKEN si ya está en el entorno y no llama a git', () => {
  const previo = process.env.GH_TOKEN;
  process.env.GH_TOKEN = 'token-de-prueba-no-real';
  try {
    assert.equal(tokenGitHub(), 'token-de-prueba-no-real');
  } finally {
    if (previo === undefined) delete process.env.GH_TOKEN;
    else process.env.GH_TOKEN = previo;
  }
});

test('sin GH_TOKEN ni gestor de credenciales termina con error y un mensaje que no contiene credenciales', () => {
  const carpeta = mkdtempSync(join(tmpdir(), 'rally-gh-'));
  // Git sin configuración de sistema ni de usuario: ningún gestor de credenciales y sin preguntar por terminal.
  const vacio = join(carpeta, 'gitconfig');
  writeFileSync(vacio, '');
  const entorno = {
    ...process.env,
    GH_TOKEN: '',
    GIT_CONFIG_NOSYSTEM: '1',
    GIT_CONFIG_GLOBAL: vacio,
    GIT_TERMINAL_PROMPT: '0',
    GIT_ASKPASS: '',
    HOME: carpeta,
    USERPROFILE: carpeta,
  };
  let r;
  try {
    const entrada = join(carpeta, 'entrada.mjs');
    writeFileSync(entrada, `import { tokenGitHub } from ${JSON.stringify(pathToFileURL(GH).href)};
tokenGitHub();
`);
    r = spawnSync(process.execPath, [entrada], { encoding: 'utf8', env: entorno });
  } finally {
    rmSync(carpeta, { recursive: true, force: true });
  }
  assert.equal(r.status, 1);
  assert.match(r.stderr, /Git Credential Manager no devolvió credencial/);
  assert.doesNotMatch(`${r.stdout}${r.stderr}`, /password=|ghp_|gho_/);
});
