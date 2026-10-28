#!/usr/bin/env node
// Valida la documentación del repo (sin dependencias, Node >= 22, Windows + Linux).
// Uso: node scripts/check-docs.mjs [--quiet]
// Comprueba: enlaces markdown internos (ruta y ancla), estructura de los hitos de ROADMAP.md
// (docs/_templates/HITO.template.md), forma de las investigaciones (docs/investigacion/,
// docs/_templates/INVESTIGACION.template.md) y bloques ``` sin cerrar.
// Salida de errores: `ruta:línea: problema` (exit 1). `--quiet` solo imprime el resumen.

import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const DOCS = join(ROOT, 'docs');
const ROADMAP = join(ROOT, 'ROADMAP.md');
const HITO_TEMPLATE = join(DOCS, '_templates', 'HITO.template.md');
const INV_TEMPLATE = join(DOCS, '_templates', 'INVESTIGACION.template.md');
const INV_DIR = join(DOCS, 'investigacion');
const ROOT_DOCS = ['CLAUDE.md', 'README.md', 'ROADMAP.md', 'QUESTIONS.md', 'voice-lab/README.md'];

const HITO_HEADING = /^H\d+[A-Z]?\b/;

const quiet = process.argv.includes('--quiet');
const errors = [];
const rel = (p) => relative(ROOT, p).split('\\').join('/');
const err = (file, line, msg) => errors.push(`${rel(file)}:${line}: ${msg}`);

// ---------- Utilidades ----------

/** Excluidos: plantillas (contienen marcadores `<...>`) y el prompt original. */
function isExcluded(file) {
  const r = rel(file);
  return r.startsWith('docs/_templates/') || r === 'docs/_meta/prompt-inicial.md';
}

function walk(dir, out = []) {
  if (!existsSync(dir)) return out;
  for (const name of readdirSync(dir).sort()) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) walk(p, out);
    else if (name.endsWith('.md')) out.push(p);
  }
  return out;
}

/**
 * Analiza un markdown: líneas fuera de bloques ``` (con su nº de línea 1-based),
 * headings (fuera de bloques) y bloques sin cerrar.
 */
function scan(file) {
  const text = readFileSync(file, 'utf8')
    .replace(/^\uFEFF/, '')
    .replace(/\r\n?/g, '\n');
  const lines = text.split('\n');
  const body = []; // { n, text } fuera de fences
  const headings = []; // { n, level, text }
  const unclosed = []; // líneas de apertura sin cierre
  let fence = null; // { ch, len, n }
  lines.forEach((text, i) => {
    const n = i + 1;
    const m = /^ {0,3}(`{3,}|~{3,})/.exec(text);
    if (fence) {
      if (m && m[1][0] === fence.ch && m[1].length >= fence.len && text.trim() === m[1])
        fence = null;
      return;
    }
    if (m && m[1][0] === '`') {
      fence = { ch: '`', len: m[1].length, n };
      return;
    }
    if (m && m[1][0] === '~') {
      fence = { ch: '~', len: m[1].length, n };
      return;
    }
    body.push({ n, text });
    const h = /^ {0,3}(#{1,6})\s+(.+?)\s*#*\s*$/.exec(text);
    if (h) headings.push({ n, level: h[1].length, text: h[2].trim() });
  });
  if (fence) unclosed.push(fence.n);
  return { body, headings, unclosed };
}

/** Slug estilo GitHub: minúsculas, sin puntuación salvo `-` (y `_`), espacios → `-`, conserva acentos. */
function slugify(heading) {
  return heading
    .replace(/!?\[([^\]]*)\]\([^)]*\)/g, '$1') // enlaces → texto
    .replace(/<[^>]+>/g, '') // html inline
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\p{M}_\- ]/gu, '')
    .replace(/ /g, '-');
}

/** Conjunto de anclas de un archivo, con sufijos -1, -2… para headings duplicados. */
function anchorsOf(headings) {
  const seen = new Map();
  const set = new Set();
  for (const h of headings) {
    const base = slugify(h.text);
    const count = seen.get(base) ?? 0;
    seen.set(base, count + 1);
    set.add(count === 0 ? base : `${base}-${count}`);
  }
  return set;
}

/** Texto de heading sin lo que va tras ` (`. */
const headingKey = (t) => t.replace(/\s+\(.*$/, '').trim();

function safeDecode(s) {
  try {
    return decodeURIComponent(s);
  } catch {
    return s;
  }
}

// ---------- Archivos a revisar ----------

const files = [
  ...walk(DOCS).filter((f) => !isExcluded(f)),
  ...ROOT_DOCS.map((f) => join(ROOT, f)).filter((f) => existsSync(f)),
];

const scans = new Map(files.map((f) => [f, scan(f)]));
let linkCount = 0;

// ---------- Fences sin cerrar y enlaces ----------

// `[texto](destino "título")` ; el texto admite un nivel de corchetes (p. ej. imágenes dentro de enlaces).
const LINK_RE =
  /\[(?:[^\[\]]|\[[^\]]*\])*\]\(\s*(<[^>]*>|[^)\s]*)(?:\s+(?:"[^"]*"|'[^']*'))?\s*\)/g;

for (const file of files) {
  const { body, headings, unclosed } = scans.get(file);
  for (const n of unclosed) err(file, n, 'bloque de código ``` sin cerrar');
  const anchors = anchorsOf(headings);
  for (const { n, text } of body) {
    const line = text.replace(/(`+)[^`]*?\1/g, (s) => ' '.repeat(s.length)); // ignora code spans
    for (const m of line.matchAll(LINK_RE)) {
      let target = m[1].trim();
      if (target.startsWith('<') && target.endsWith('>')) target = target.slice(1, -1);
      if (target === '') continue;
      if (/^[a-z][a-z0-9+.-]*:/i.test(target) || target.startsWith('//')) continue; // http(s):, mailto:, …
      linkCount++;
      const hash = target.indexOf('#');
      const path = hash === -1 ? target : target.slice(0, hash);
      const anchor = hash === -1 ? null : safeDecode(target.slice(hash + 1));
      if (path === '') {
        if (anchor && !anchors.has(anchor.toLowerCase()))
          err(file, n, `ancla inexistente "#${anchor}"`);
        continue;
      }
      const clean = safeDecode(path.split('?')[0]);
      const abs = clean.startsWith('/') ? join(ROOT, clean) : resolve(dirname(file), clean);
      if (!existsSync(abs)) {
        err(file, n, `enlace roto: "${target}" (no existe ${rel(abs)})`);
        continue;
      }
      if (anchor && abs.endsWith('.md') && statSync(abs).isFile()) {
        const target_ = scans.get(abs) ?? scan(abs);
        if (!anchorsOf(target_.headings).has(anchor.toLowerCase()))
          err(file, n, `ancla inexistente "#${anchor}" en ${rel(abs)}`);
      }
    }
  }
}

// ---------- Hitos de ROADMAP.md ----------

let hitoCount = 0;
if (!existsSync(HITO_TEMPLATE)) {
  err(HITO_TEMPLATE, 1, 'no existe la plantilla de hitos');
} else if (!existsSync(ROADMAP)) {
  err(ROADMAP, 1, 'no existe ROADMAP.md');
} else {
  const tpl = scan(HITO_TEMPLATE);
  const required = tpl.headings.filter((h) => h.level === 3).map((h) => headingKey(h.text));
  const { headings } = scans.get(ROADMAP) ?? scan(ROADMAP);
  let current = null;
  const check = () => {
    if (!current) return;
    for (const h of required)
      if (!current.have.has(h)) err(ROADMAP, current.n, `${current.id}: falta "### ${h}"`);
  };
  for (const h of headings) {
    if (h.level <= 2) {
      check();
      current = null;
      if (h.level === 2 && HITO_HEADING.test(h.text)) {
        hitoCount++;
        current = { id: h.text.split(/\s/)[0], n: h.n, have: new Set() };
      }
    } else if (h.level === 3 && current) {
      current.have.add(headingKey(h.text));
    }
  }
  check();
  if (hitoCount === 0) err(ROADMAP, 1, 'ningún hito "## Hn — título"');
}

// ---------- Investigaciones ----------

let invCount = 0;
if (existsSync(INV_DIR)) {
  if (!existsSync(INV_TEMPLATE)) err(INV_TEMPLATE, 1, 'no existe la plantilla de investigación');
  else {
    const tpl = scan(INV_TEMPLATE);
    const required = tpl.headings.filter((h) => h.level === 2).map((h) => headingKey(h.text));
    for (const file of walk(INV_DIR)) {
      if (file.endsWith('README.md')) continue;
      invCount++;
      const s = scans.get(file) ?? scan(file);
      const have = new Set(s.headings.filter((h) => h.level === 2).map((h) => headingKey(h.text)));
      for (const h of required)
        if (!have.has(h)) err(file, 1, `falta el heading obligatorio "## ${h}"`);
    }
  }
}

// ---------- Salida ----------

if (!quiet) for (const e of errors) console.error(e);
console.log(
  `Resumen: ${files.length} archivos · ${linkCount} enlaces internos · ${hitoCount} hitos · ${invCount} investigaciones · ${errors.length} errores`,
);
process.exit(errors.length ? 1 : 0);
