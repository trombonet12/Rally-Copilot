#!/usr/bin/env node
// Regenera el vídeo promocional de Rally Copilot (Remotion, carpeta video/). Único comando del flujo.
//
// Uso: node scripts/video/promo.mjs [--grabar] [--solo-grabar] [--control] [--sin-render]
//   --grabar        graba antes los clips con el emulador (bash scripts/video/grabar-clips.sh)
//   --solo-grabar   graba los clips y termina (implica --grabar)
//   --control       saca 6 stills repartidos (build/promo/control-NN.png) y una hoja de contacto 3x2
//                   build/promo/control-hoja.png (necesita Python + Pillow). Renderiza antes salvo con --sin-render.
//   --sin-render    solo prepara y comprueba (clips, música, node_modules); sin --control no renderiza nada
// Los clips y la música que se esperan salen de video/src/guion.ts (editar solo ese archivo).
// Salida: build/promo/rally-copilot-promo-1920x1080.mp4 (ignorado por git).
// Sale con código distinto de 0 si cualquier paso falla.

import { spawnSync } from 'node:child_process';
import { copyFileSync, existsSync, mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const RAIZ = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const VIDEO = join(RAIZ, 'video');
const CLIPS_ORIGEN = join(RAIZ, 'build', 'promo', 'clips');
const CLIPS_DESTINO = join(VIDEO, 'public', 'clips');
const SALIDA_DIR = join(RAIZ, 'build', 'promo');
const NOMBRE_MP4 = 'rally-copilot-promo-1920x1080.mp4';
const MP4 = join(SALIDA_DIR, NOMBRE_MP4);
const MP4_ARG = `../build/promo/${NOMBRE_MP4}`;
const PROPS = join(SALIDA_DIR, 'props.json');
const PROPS_ARG = '../build/promo/props.json';
const WIN = process.platform === 'win32';
const USO = 'Uso: node scripts/video/promo.mjs [--grabar] [--solo-grabar] [--control] [--sin-render]';

const args = new Set(process.argv.slice(2));
const conocidos = new Set(['--grabar', '--solo-grabar', '--control', '--sin-render']);
for (const a of args) {
  if (!conocidos.has(a)) fallar(`argumento desconocido: ${a}\n${USO}`);
}
const soloGrabar = args.has('--solo-grabar');
const grabar = args.has('--grabar') || soloGrabar;
const control = args.has('--control');
const sinRender = args.has('--sin-render');

function fallar(msg) {
  console.error(`promo.mjs: ${msg}`);
  process.exit(1);
}

function paso(msg) {
  console.log(`\n== ${msg}`);
}

/** Ejecuta un comando heredando la salida (o capturándola). En Windows, npm/npx son .cmd y necesitan shell. */
function ejecutar(cmd, argv, { cwd = RAIZ, shell = false, capturar = false } = {}) {
  const r = spawnSync(cmd, argv, { cwd, shell, stdio: capturar ? 'pipe' : 'inherit', encoding: 'utf8' });
  if (r.error) fallar(`no se pudo ejecutar ${cmd}: ${r.error.message}`);
  return r;
}

function ejecutarOFallar(cmd, argv, opts) {
  const r = ejecutar(cmd, argv, opts);
  if (r.status !== 0) fallar(`falló «${cmd} ${argv.join(' ')}» (código ${r.status})`);
  return r;
}

/** npx con cwd en video/. Los argumentos no pueden llevar espacios (en Windows va con shell). */
function npx(argv, opts = {}) {
  return ejecutar(WIN ? 'npx.cmd' : 'npx', argv, { cwd: VIDEO, shell: WIN, ...opts });
}

function encontrarBash() {
  const candidatos = WIN ? ['C:\\Program Files\\Git\\bin\\bash.exe', 'C:\\Program Files (x86)\\Git\\bin\\bash.exe'] : [];
  for (const c of candidatos) if (existsSync(c)) return c;
  // En Windows, el bash de System32 es WSL: se evita a favor de Git Bash.
  const r = spawnSync(WIN ? 'where' : 'which', ['bash'], { encoding: 'utf8' });
  if (r.status === 0) {
    const lineas = r.stdout.split(/\r?\n/).filter(Boolean);
    const buena = lineas.find((l) => !/system32/i.test(l)) ?? lineas[0];
    if (buena) return buena;
  }
  return null;
}

function tieneComando(cmd, versionArg = '-version') {
  const r = spawnSync(cmd, [versionArg], { encoding: 'utf8' });
  return !r.error && r.status === 0;
}

/** Lee del guion los clips (clips/<nombre>.mp4) y la música (archivo) sin ejecutar TypeScript. */
function leerGuion() {
  const texto = readFileSync(join(VIDEO, 'src', 'guion.ts'), 'utf8');
  const clips = [...texto.matchAll(/clip:\s*"clips\/([^"]+\.mp4)"/g)].map((m) => m[1]);
  if (clips.length === 0) fallar('no se encontró ningún `clip: "clips/…mp4"` en video/src/guion.ts');
  const musica = /musica:\s*\{\s*archivo:\s*"([^"]+)"/.exec(texto)?.[1] ?? null;
  return { clips, musica };
}

const { clips: CLIPS_ESPERADOS, musica: MUSICA } = leerGuion();

// --- 1. Grabación opcional -----------------------------------------------------------------
if (grabar) {
  paso('Grabando clips (bash scripts/video/grabar-clips.sh)');
  const bash = encontrarBash();
  if (!bash) fallar('no se encontró bash (instala Git for Windows o añade bash al PATH)');
  ejecutarOFallar(bash, ['scripts/video/grabar-clips.sh']);
  if (soloGrabar) {
    console.log(`\nClips grabados en ${CLIPS_ORIGEN}`);
    process.exit(0);
  }
}

// --- 2. Copiar clips nuevos a video/public/clips -----------------------------------------------
paso('Sincronizando clips en video/public/clips');
mkdirSync(CLIPS_DESTINO, { recursive: true });
if (existsSync(CLIPS_ORIGEN)) {
  for (const f of readdirSync(CLIPS_ORIGEN).filter((n) => /^c.*\.mp4$/.test(n))) {
    const origen = join(CLIPS_ORIGEN, f);
    const destino = join(CLIPS_DESTINO, f);
    if (!existsSync(destino) || statSync(origen).mtimeMs > statSync(destino).mtimeMs) {
      copyFileSync(origen, destino);
      console.log(`  copiado ${f}`);
    }
  }
}

// --- 3. Comprobar clips y música -----------------------------------------------------------
const faltan = CLIPS_ESPERADOS.filter((c) => !existsSync(join(CLIPS_DESTINO, c)));
if (faltan.length > 0) {
  fallar(
    `faltan clips en video/public/clips: ${faltan.join(', ')}.\n` +
      'Remedio: arranca el emulador con la app debug y ejecuta  node scripts/video/promo.mjs --grabar',
  );
}
console.log(`  clips OK: ${CLIPS_ESPERADOS.join(', ')}`);

mkdirSync(SALIDA_DIR, { recursive: true });
let conMusica = false;
if (MUSICA === null) {
  console.log('  AVISO: guion.musica es null: el vídeo saldrá sin música.');
} else if (existsSync(join(VIDEO, 'public', MUSICA))) {
  conMusica = true;
  console.log(`  música OK: ${MUSICA}`);
} else {
  console.warn(`  AVISO: no existe video/public/${MUSICA}: se renderiza SIN música (ponla en video/public/musica/ y repite).`);
}
// Remotion lee las props de un archivo (en Windows no se puede pasar JSON inline).
writeFileSync(PROPS, JSON.stringify({ conMusica }));

// --- 4. Dependencias de Remotion -----------------------------------------------------------
if (!existsSync(join(VIDEO, 'node_modules'))) {
  paso('Instalando dependencias (npm ci en video/)');
  ejecutarOFallar(WIN ? 'npm.cmd' : 'npm', ['ci'], { cwd: VIDEO, shell: WIN });
} else {
  console.log('  video/node_modules OK');
}

if (sinRender && !control) {
  console.log('\n--sin-render: comprobaciones correctas, no se renderiza.');
  process.exit(0);
}

// --- 5. Render -----------------------------------------------------------------------------
if (!sinRender) {
  paso('Renderizando composición Promo');
  const r = npx(['remotion', 'render', 'src/index.ts', 'Promo', MP4_ARG, `--props=${PROPS_ARG}`]);
  if (r.status !== 0) fallar(`falló el render de Remotion (código ${r.status})`);
  if (!existsSync(MP4)) fallar(`el render terminó sin generar ${MP4}`);
}

// --- 6. Control visual (opcional) ----------------------------------------------------------
const hayFfprobe = tieneComando('ffprobe');

function ffprobe(argv) {
  if (!hayFfprobe) return null;
  const r = spawnSync('ffprobe', ['-v', 'error', ...argv, MP4], { encoding: 'utf8' });
  return r.status === 0 ? r.stdout.trim() : null;
}

function duracionSegundos() {
  const d = parseFloat(ffprobe(['-show_entries', 'format=duration', '-of', 'csv=p=0']) ?? '');
  return Number.isFinite(d) ? d : null;
}

if (control) {
  paso('Control visual: 6 stills y hoja de contacto');
  let frames = null;
  const comp = npx(['remotion', 'compositions', 'src/index.ts', `--props=${PROPS_ARG}`], { capturar: true });
  if (comp.status === 0) {
    const m = /^Promo\s+(\d+)\s+\S+\s+(\d+)/m.exec(comp.stdout.replace(/\x1b\[[0-9;]*m/g, ''));
    if (m) frames = parseInt(m[2], 10);
  }
  if (frames === null && existsSync(MP4)) {
    const seg = duracionSegundos();
    if (seg !== null) frames = Math.round(seg * 30);
  }
  if (frames === null || frames < 12) fallar('no se pudo determinar la duración (frames) de la composición Promo');
  console.log(`  composición Promo: ${frames} frames`);

  const stills = [];
  for (let i = 0; i < 6; i++) {
    const frame = Math.min(frames - 1, Math.round(((i + 0.5) / 6) * frames));
    const nombre = `control-${String(i + 1).padStart(2, '0')}.png`;
    const r = npx(['remotion', 'still', 'src/index.ts', 'Promo', `../build/promo/${nombre}`, `--frame=${frame}`, `--props=${PROPS_ARG}`]);
    if (r.status !== 0) fallar(`falló el still del frame ${frame} (código ${r.status})`);
    stills.push(join(SALIDA_DIR, nombre));
  }

  const py = `
import sys
from PIL import Image
out, files = sys.argv[1], sys.argv[2:]
ims = [Image.open(f).convert('RGB') for f in files]
w = 640; h = round(ims[0].height * w / ims[0].width)
sheet = Image.new('RGB', (w * 3, h * 2), 'white')
for i, im in enumerate(ims):
    sheet.paste(im.resize((w, h)), ((i % 3) * w, (i // 3) * h))
sheet.save(out)
`;
  const hoja = join(SALIDA_DIR, 'control-hoja.png');
  let hecho = false;
  for (const pyCmd of WIN ? ['python', 'py'] : ['python3', 'python']) {
    const r = spawnSync(pyCmd, ['-c', py, hoja, ...stills], { encoding: 'utf8' });
    if (!r.error && r.status === 0) {
      hecho = true;
      break;
    }
  }
  console.log(hecho ? `  hoja de contacto: ${hoja}` : '  Python+Pillow no disponible: stills sueltos en build/promo/control-NN.png');
}

// --- 7. Resumen ----------------------------------------------------------------------------
if (sinRender) process.exit(0);
paso('Listo');
const tamMb = (statSync(MP4).size / (1024 * 1024)).toFixed(1);
const dur = duracionSegundos();
console.log(`MP4: ${MP4}`);
console.log(`Tamaño: ${tamMb} MB${dur !== null ? ` · Duración: ${dur.toFixed(1)} s` : hayFfprobe ? '' : ' · (ffprobe no disponible: sin duración)'}`);
if (hayFfprobe) {
  const v = ffprobe(['-select_streams', 'v:0', '-show_entries', 'stream=width,height,r_frame_rate,pix_fmt', '-of', 'csv=p=0']);
  const a = ffprobe(['-select_streams', 'a:0', '-show_entries', 'stream=codec_name', '-of', 'csv=p=0']);
  console.log(`Vídeo (ancho,alto,fps,pix_fmt): ${v ?? '?'} · Audio: ${a ? a : 'SIN pista de audio'}`);
}
console.log(`
Subir a YouTube y enlazar en Play:
  1. YouTube Studio > Crear > Subir vídeo: selecciona el MP4 anterior.
  2. Visibilidad: «No listado» (oculto); no lo marques como contenido para niños.
  3. Copia la URL de la forma https://www.youtube.com/watch?v=XXXXXXXXXXX
  4. Play Console > Ficha de Play Store > Vídeo promocional: pega esa URL watch?v=.`);
