#!/usr/bin/env node
// Ejecuta GitHub CLI con el token de Git Credential Manager (el mismo que usa git push), sin `gh auth login`.
// El token solo vive en memoria del proceso hijo (GH_TOKEN); nunca se imprime.
// Uso: node scripts/gh.mjs <argumentos de gh…>   p. ej. node scripts/gh.mjs run list

import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

const GH_WINDOWS = 'C:/Program Files/GitHub CLI/gh.exe';

export function ghBinario() {
  return process.platform === 'win32' && existsSync(GH_WINDOWS) ? GH_WINDOWS : 'gh';
}

export function tokenGitHub() {
  if (process.env.GH_TOKEN) return process.env.GH_TOKEN;
  const r = spawnSync('git', ['credential', 'fill'], {
    input: 'protocol=https\nhost=github.com\n\n',
    encoding: 'utf8',
    env: { ...process.env, GIT_TERMINAL_PROMPT: '0' },
  });
  const m = /^password=(.+)$/m.exec(r.stdout ?? '');
  if (r.status !== 0 || !m) {
    console.error('gh.mjs: Git Credential Manager no devolvió credencial para github.com');
    process.exit(1);
  }
  return m[1].trim();
}

/** Lanza gh con el token; `input` va por stdin (para `gh secret set`). */
export function gh(args, { input, silencioso = false } = {}) {
  return spawnSync(ghBinario(), args, {
    input,
    stdio: input === undefined ? (silencioso ? 'pipe' : 'inherit') : ['pipe', 'pipe', 'pipe'],
    encoding: 'utf8',
    env: { ...process.env, GH_TOKEN: tokenGitHub() },
  });
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const r = gh(process.argv.slice(2));
  process.exit(r.status ?? 1);
}
