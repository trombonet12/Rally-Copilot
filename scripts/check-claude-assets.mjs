#!/usr/bin/env node
// Valida agentes y skills de Claude Code del repo (sin dependencias, Node >= 22).
// Uso: node scripts/check-claude-assets.mjs [--strict-catalog]
// Salida de errores: `ruta: problema` (exit 1). Referencias: docs/_templates/{SKILL,AGENT}.template.md
// y la documentación oficial de Claude Code (frontmatter de skills y subagentes).

import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const AGENTS_DIR = join(ROOT, '.claude', 'agents');
const SKILLS_DIR = join(ROOT, '.claude', 'skills');
const CATALOG = join(ROOT, 'docs', '_meta', 'skills-catalog.md');

// Presupuesto de caracteres (name + description + when_to_use) de las skills invocables por el modelo.
// Heredado de Sous Chef (docs/DECISIONES.md § Claude Code). Si se supera → fusionar skills.
const SKILL_BUDGET = 16000;

const SKILL_DESC_MAX = 200;
const SKILL_BODY_MAX = 100;
const AGENT_BODY_MAX = 50;

// Campos de frontmatter de skills admitidos por Claude Code.
const SKILL_FIELDS = new Set([
  'name',
  'description',
  'when_to_use',
  'argument-hint',
  'arguments',
  'disable-model-invocation',
  'user-invocable',
  'allowed-tools',
  'disallowed-tools',
  'model',
  'effort',
  'context',
  'agent',
  'background',
  'hooks',
  'paths',
  'shell',
  'metadata',
]);
// Campos de frontmatter de subagentes admitidos por Claude Code.
const AGENT_FIELDS = new Set([
  'name',
  'description',
  'tools',
  'disallowedTools',
  'model',
  'permissionMode',
  'maxTurns',
  'skills',
  'mcpServers',
  'hooks',
  'memory',
  'background',
  'effort',
  'isolation',
  'color',
  'omitClaudeMd',
  'initialPrompt',
]);
const AGENT_REQUIRED = ['name', 'description', 'tools', 'model'];
const AGENT_HEADINGS = [
  'Ámbito',
  'Puede',
  'No puede',
  'Skills que usa',
  'Validación',
  'Formato de respuesta',
];
const SKILL_HEADINGS = [
  'Cuándo usarla',
  'Cuándo NO usarla',
  'Precondiciones',
  'Pasos',
  'Validaciones / tests',
  'Definition of Done',
  'Docs a actualizar',
];
const MODEL_ALIASES = new Set(['haiku', 'sonnet', 'opus', 'fable', 'inherit']);

const strictCatalog = process.argv.includes('--strict-catalog');
const errors = [];
const warnings = [];
const rel = (p) => relative(ROOT, p).split('\\').join('/');
const err = (file, msg) => errors.push(`${rel(file)}: ${msg}`);
const warn = (file, msg) =>
  warnings.push(`${typeof file === 'string' && !file.startsWith(ROOT) ? file : rel(file)}: ${msg}`);

/** Parser mínimo de frontmatter: `clave: valor`, comillas, booleanos, comentarios ` # ...`. */
function parseFile(file) {
  const text = readFileSync(file, 'utf8')
    .replace(/^\uFEFF/, '')
    .replace(/\r\n?/g, '\n');
  const lines = text.split('\n');
  if (lines[0] !== '---') return { error: 'el frontmatter debe empezar con `---` en la línea 1' };
  const end = lines.indexOf('---', 1);
  if (end === -1) return { error: 'frontmatter sin cierre `---`' };
  const fm = {};
  const multiline = new Set();
  let current = null;
  for (let i = 1; i < end; i++) {
    const line = lines[i];
    if (line.trim() === '' || line.trim().startsWith('#')) continue;
    if (/^\s/.test(line) || line.startsWith('- ')) {
      if (current) multiline.add(current); // continuación / lista / bloque
      continue;
    }
    const m = /^([A-Za-z_][\w-]*)\s*:\s*(.*)$/.exec(line);
    if (!m) return { error: `línea ${i + 1} del frontmatter no es \`clave: valor\`` };
    const key = m[1];
    if (key in fm) return { error: `campo duplicado \`${key}\` en el frontmatter` };
    fm[key] = parseValue(m[2]);
    if (fm[key] === '|' || fm[key] === '>' || /^[|>][+-]?$/.test(String(fm[key])))
      multiline.add(key);
    current = key;
  }
  let bodyLines = lines.slice(end + 1);
  while (bodyLines.length && bodyLines[bodyLines.length - 1].trim() === '') bodyLines.pop();
  while (bodyLines.length && bodyLines[0].trim() === '') bodyLines.shift();
  return { fm, multiline, bodyLines };
}

function parseValue(raw) {
  let v = raw.trim();
  const q = v[0];
  if ((q === '"' || q === "'") && v.length > 1) {
    const close = v.indexOf(q, 1);
    if (close !== -1) return v.slice(1, close);
  }
  v = v.replace(/\s+#.*$/, '').trim();
  if (v === 'true') return true;
  if (v === 'false') return false;
  return v;
}

function headingsOf(bodyLines) {
  const set = new Set();
  let fence = false;
  for (const l of bodyLines) {
    if (/^\s*```/.test(l)) fence = !fence;
    if (fence) continue;
    const m = /^#{1,6}\s+(.+?)\s*#*\s*$/.exec(l);
    if (m) set.add(m[1].trim().toLowerCase());
  }
  return set;
}

function checkHeadings(file, bodyLines, required) {
  const have = headingsOf(bodyLines);
  for (const h of required)
    if (!have.has(h.toLowerCase())) err(file, `falta el heading obligatorio "${h}"`);
}

function checkUnknownFields(file, fm, allowed) {
  for (const k of Object.keys(fm))
    if (!allowed.has(k)) err(file, `campo de frontmatter no permitido \`${k}\``);
}

const names = new Map(); // name -> ruta
const descriptions = new Map(); // description -> ruta
function registerUnique(file, name, description) {
  if (typeof name === 'string' && name) {
    if (names.has(name)) err(file, `name duplicado "${name}" (también en ${names.get(name)})`);
    else names.set(name, rel(file));
  }
  if (typeof description === 'string' && description) {
    if (descriptions.has(description))
      err(file, `description duplicada (también en ${descriptions.get(description)})`);
    else descriptions.set(description, rel(file));
  }
}

// ---------- Agentes ----------
let agentCount = 0;
if (existsSync(AGENTS_DIR)) {
  for (const f of readdirSync(AGENTS_DIR)
    .filter((x) => x.endsWith('.md'))
    .sort()) {
    const file = join(AGENTS_DIR, f);
    agentCount++;
    const p = parseFile(file);
    if (p.error) {
      err(file, p.error);
      continue;
    }
    const { fm, multiline, bodyLines } = p;
    for (const k of AGENT_REQUIRED) {
      if (!(k in fm) || fm[k] === '') err(file, `falta el campo obligatorio \`${k}\``);
    }
    checkUnknownFields(file, fm, AGENT_FIELDS);
    const base = f.slice(0, -3);
    if (fm.name !== undefined && fm.name !== base)
      err(file, `name "${fm.name}" debe coincidir con el nombre de archivo "${base}"`);
    if (
      typeof fm.model === 'string' &&
      fm.model !== '' &&
      !MODEL_ALIASES.has(fm.model) &&
      !/^claude-[a-z0-9-]+$/.test(fm.model)
    ) {
      err(file, `model "${fm.model}" no válido (haiku|sonnet|opus|fable|inherit o ID claude-…)`);
    }
    if (multiline.has('description') || /^[|>]/.test(String(fm.description ?? '')))
      err(file, 'description debe ser de 1 línea');
    if (bodyLines.length > AGENT_BODY_MAX)
      err(file, `cuerpo de ${bodyLines.length} líneas (máx. ${AGENT_BODY_MAX})`);
    checkHeadings(file, bodyLines, AGENT_HEADINGS);
    registerUnique(file, fm.name, fm.description);
  }
}

// ---------- Skills ----------
let skillCount = 0;
let budget = 0;
const skillNames = [];
if (existsSync(SKILLS_DIR)) {
  for (const d of readdirSync(SKILLS_DIR).sort()) {
    const dir = join(SKILLS_DIR, d);
    if (!statSync(dir).isDirectory()) continue;
    const file = join(dir, 'SKILL.md');
    if (!existsSync(file)) {
      err(dir, 'carpeta de skill sin SKILL.md');
      continue;
    }
    skillCount++;
    skillNames.push(d);
    const p = parseFile(file);
    if (p.error) {
      err(file, p.error);
      continue;
    }
    const { fm, multiline, bodyLines } = p;
    if (!fm.name) err(file, 'falta el campo obligatorio `name`');
    else if (fm.name !== d) err(file, `name "${fm.name}" debe coincidir con la carpeta "${d}"`);
    if (typeof fm.description !== 'string' || fm.description === '')
      err(file, 'falta el campo obligatorio `description`');
    else {
      if (multiline.has('description')) err(file, 'description debe ser de 1 línea');
      if (fm.description.length > SKILL_DESC_MAX)
        err(file, `description de ${fm.description.length} caracteres (máx. ${SKILL_DESC_MAX})`);
    }
    checkUnknownFields(file, fm, SKILL_FIELDS);
    if ('disable-model-invocation' in fm && typeof fm['disable-model-invocation'] !== 'boolean') {
      err(file, '`disable-model-invocation` debe ser booleano (true|false)');
    }
    if (bodyLines.length > SKILL_BODY_MAX)
      err(file, `cuerpo de ${bodyLines.length} líneas (máx. ${SKILL_BODY_MAX})`);
    checkHeadings(file, bodyLines, SKILL_HEADINGS);
    registerUnique(file, fm.name, fm.description);
    if (fm['disable-model-invocation'] !== true) {
      for (const k of ['name', 'description', 'when_to_use'])
        if (typeof fm[k] === 'string') budget += fm[k].length;
    }
  }
}
if (budget > SKILL_BUDGET)
  err(
    'docs/DECISIONES.md',
    `presupuesto de skills excedido: ${budget} > ${SKILL_BUDGET} caracteres (fusionar skills)`,
  );

// ---------- Catálogo ----------
const catalogNames = new Set();
if (existsSync(CATALOG)) {
  for (const line of readFileSync(CATALOG, 'utf8').split(/\r?\n/)) {
    const m = /^- ([a-z0-9][a-z0-9-]*) \|/.exec(line);
    if (m) catalogNames.add(m[1]);
  }
  for (const s of skillNames) {
    if (!catalogNames.has(s))
      err(join(SKILLS_DIR, s, 'SKILL.md'), 'la skill no figura en docs/_meta/skills-catalog.md');
  }
  const missing = [...catalogNames].filter((n) => !skillNames.includes(n));
  if (strictCatalog) {
    for (const n of missing) err(CATALOG, `skill del catálogo sin implementar: ${n}`);
  } else if (missing.length) {
    warn(
      CATALOG,
      `${missing.length} skills del catálogo aún sin implementar (usa --strict-catalog para exigirlas)`,
    );
  }
} else if (skillCount > 0 || strictCatalog) {
  err(CATALOG, 'no existe el catálogo de skills');
} else {
  warn(CATALOG, 'no existe el catálogo de skills');
}

// ---------- Salida ----------
for (const e of errors) console.error(e);
for (const w of warnings) console.warn(`aviso: ${w}`);
console.log(`Presupuesto de skills: ${budget}/${SKILL_BUDGET} caracteres`);
console.log(
  `Resumen: ${agentCount} agentes · ${skillCount} skills · presupuesto ${budget}/${SKILL_BUDGET} · ${errors.length} errores · ${warnings.length} avisos`,
);
process.exit(errors.length ? 1 : 0);
