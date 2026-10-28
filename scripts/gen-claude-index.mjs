#!/usr/bin/env node
// Genera docs/agentes.md y docs/skills.md desde .claude/agents, .claude/skills y docs/_meta/skills-catalog.md.
// Uso: node scripts/gen-claude-index.mjs [--check]
//   --check  no escribe; sale con 1 si el contenido generado difiere del existente (CI/qa).
// Los índices los escribe este script, nunca a mano. Salida determinista (sin fechas, LF).
// Parser de frontmatter copiado de scripts/check-claude-assets.mjs (sin dependencias, Node >= 22).

import { existsSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const AGENTS_DIR = join(ROOT, '.claude', 'agents');
const SKILLS_DIR = join(ROOT, '.claude', 'skills');
const CATALOG = join(ROOT, 'docs', '_meta', 'skills-catalog.md');
const OUT_AGENTS = join(ROOT, 'docs', 'agentes.md');
const OUT_SKILLS = join(ROOT, 'docs', 'skills.md');
const HEADER = '<!-- Generado por scripts/gen-claude-index.mjs. No editar a mano. -->';
// Mismo presupuesto que scripts/check-claude-assets.mjs.
const SKILL_BUDGET = 16000;

const checkMode = process.argv.includes('--check');
const errors = [];
const rel = (p) => relative(ROOT, p).split('\\').join('/');

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
  for (let i = 1; i < end; i++) {
    const line = lines[i];
    if (line.trim() === '' || line.trim().startsWith('#')) continue;
    if (/^\s/.test(line) || line.startsWith('- ')) continue; // continuación / lista / bloque
    const m = /^([A-Za-z_][\w-]*)\s*:\s*(.*)$/.exec(line);
    if (!m) return { error: `línea ${i + 1} del frontmatter no es \`clave: valor\`` };
    if (Object.hasOwn(fm, m[1])) return { error: `campo duplicado \`${m[1]}\` en el frontmatter` };
    fm[m[1]] = parseValue(m[2]);
  }
  return { fm };
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

/** Texto de una celda: una línea, `|` escapado (también dentro de código). */
const cell = (s) =>
  String(s ?? '')
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/\|/g, '\\|');
const code = (s) => (s ? `\`${s}\`` : '—');

/** Tabla GFM con columnas alineadas al ancho máximo (mismo formato que prettier). */
function table(headers, rows) {
  const all = [headers, ...rows].map((r) => r.map(cell));
  const widths = headers.map((_, i) => Math.max(3, ...all.map((r) => r[i].length)));
  const line = (r) => `| ${r.map((c, i) => c.padEnd(widths[i])).join(' | ')} |`;
  const sep = `| ${widths.map((w) => '-'.repeat(w)).join(' | ')} |`;
  return [line(all[0]), sep, ...all.slice(1).map(line)].join('\n');
}

function byName(a, b) {
  return a.name < b.name ? -1 : a.name > b.name ? 1 : 0; // orden de código, independiente del locale
}

// ---------- Agentes ----------
function loadAgents() {
  if (!existsSync(AGENTS_DIR)) return [];
  const agents = [];
  for (const f of readdirSync(AGENTS_DIR)
    .filter((x) => x.endsWith('.md'))
    .sort()) {
    const file = join(AGENTS_DIR, f);
    const base = f.slice(0, -3);
    const p = parseFile(file);
    if (p.error) {
      errors.push(`${rel(file)}: ${p.error}`);
      continue;
    }
    const { fm } = p;
    if (
      typeof fm.description !== 'string' ||
      !fm.description ||
      /^[|>][+-]?$/.test(fm.description)
    ) {
      errors.push(`${rel(file)}: falta \`description\` de 1 línea`);
      continue;
    }
    agents.push({
      name: fm.name ?? base,
      file: f,
      model: fm.model ?? '',
      tools: fm.tools ?? '',
      description: fm.description,
    });
  }
  return agents.sort(byName);
}

function buildAgents(agents) {
  const rows = agents.map((a) => [
    `[${a.name}](../.claude/agents/${a.file})`,
    a.model || '—',
    a.tools || '—',
    a.description,
  ]);
  return [
    HEADER,
    '',
    '# Agentes',
    '',
    'Índice generado desde `.claude/agents/*.md`.',
    '',
    table(['Agente', 'Modelo', 'Tools', 'Descripción'], rows),
    '',
    `Total: ${agents.length} agentes.`,
    '',
  ].join('\n');
}

// ---------- Skills ----------
function loadSkills() {
  const skills = new Map();
  if (!existsSync(SKILLS_DIR)) return skills;
  for (const d of readdirSync(SKILLS_DIR).sort()) {
    const dir = join(SKILLS_DIR, d);
    const file = join(dir, 'SKILL.md');
    if (!statSync(dir).isDirectory() || !existsSync(file)) continue;
    const p = parseFile(file);
    if (p.error) {
      errors.push(`${rel(file)}: ${p.error}`);
      continue;
    }
    const { fm } = p;
    if (fm.name !== undefined && fm.name !== d) {
      errors.push(`${rel(file)}: name "${fm.name}" debe coincidir con la carpeta "${d}"`);
      continue;
    }
    if (
      typeof fm.description !== 'string' ||
      !fm.description ||
      /^[|>][+-]?$/.test(fm.description)
    ) {
      errors.push(`${rel(file)}: falta \`description\` de 1 línea`);
      continue;
    }
    skills.set(d, {
      name: d,
      description: fm.description,
      argHint: typeof fm['argument-hint'] === 'string' ? fm['argument-hint'] : '',
      manual: fm['disable-model-invocation'] === true,
    });
  }
  return skills;
}

/** Categorías del catálogo en su orden: [{ title: "1. Arquitectura", names: [...] }]. */
function loadCatalog() {
  const groups = [];
  if (!existsSync(CATALOG)) return groups;
  let current = null;
  for (const line of readFileSync(CATALOG, 'utf8').split(/\r?\n/)) {
    const h = /^## (.+?)\s*$/.exec(line);
    if (h) {
      current = /^\d+\./.test(h[1]) ? { title: h[1], names: [] } : null;
      if (current) groups.push(current);
      continue;
    }
    const m = /^- ([a-z0-9][a-z0-9-]*) \|/.exec(line);
    if (m && current) current.names.push(m[1]);
  }
  return groups;
}

function buildSkills(skills) {
  const groups = loadCatalog();
  const catalogNames = new Set(groups.flatMap((g) => g.names));
  const placed = new Set();
  const sections = [];
  for (const g of groups) {
    const items = g.names.filter((n) => skills.has(n) && !placed.has(n));
    items.forEach((n) => placed.add(n));
    if (items.length) sections.push({ title: g.title, items: items.map((n) => skills.get(n)) });
  }
  const others = [...skills.keys()].filter((n) => !placed.has(n)).sort();
  if (others.length) sections.push({ title: 'Otras', items: others.map((n) => skills.get(n)) });

  const all = [...skills.values()];
  const manual = all.filter((s) => s.manual).length;
  const budget = all
    .filter((s) => !s.manual)
    .reduce((sum, s) => sum + s.name.length + s.description.length, 0);
  const pending = [...catalogNames].filter((n) => !skills.has(n));

  const out = [
    HEADER,
    '',
    '# Skills',
    '',
    'Índice generado desde `.claude/skills/*/SKILL.md`, agrupado según `docs/_meta/skills-catalog.md`.',
    '',
    `Total: ${all.length} skills (${manual} manuales).`,
    '',
    `Presupuesto de descripciones: ${budget} / ${SKILL_BUDGET} caracteres (suma de name + description de las skills no manuales).`,
  ];
  if (pending.length) {
    out.push('', `Pendientes del catálogo (sin SKILL.md): ${pending.join(', ')}.`);
  }
  for (const s of sections) {
    const rows = s.items.map((k) => [
      `[${k.name}](../.claude/skills/${k.name}/SKILL.md)`,
      code(k.argHint),
      k.manual ? 'sí' : 'no',
      k.description,
    ]);
    out.push(
      '',
      `## ${s.title}`,
      '',
      table(['Skill', 'Argumentos', 'Manual', 'Descripción'], rows),
    );
  }
  out.push('');
  return out.join('\n');
}

// ---------- Main ----------
const agents = loadAgents();
const skills = loadSkills();

if (errors.length) {
  for (const e of errors) console.error(e);
  console.error('No se generan índices: corrige los errores de frontmatter.');
  process.exitCode = 1;
} else {
  const outputs = [
    [OUT_AGENTS, buildAgents(agents)],
    [OUT_SKILLS, buildSkills(skills)],
  ];
  let differs = false;
  for (const [path, content] of outputs) {
    if (checkMode) {
      const current = existsSync(path) ? readFileSync(path, 'utf8').replace(/\r\n?/g, '\n') : null;
      if (current !== content) {
        console.error(`${rel(path)}: desactualizado (ejecuta node scripts/gen-claude-index.mjs)`);
        differs = true;
      }
    } else {
      writeFileSync(path, content, 'utf8');
      console.log(`escrito ${rel(path)}`);
    }
  }
  if (checkMode) {
    if (differs) process.exitCode = 1;
    else console.log('Índices al día.');
  }
  console.log(`Resumen: ${agents.length} agentes · ${skills.size} skills`);
}
