#!/usr/bin/env node
// Genera los gráficos de la ficha de Google Play con Chrome headless (sin dependencias):
//   distribution/listing/graphics/icon-512.png          icono 512x512, cuadrado y opaco (Play aplica su máscara)
//   distribution/listing/graphics/feature-1024x500.png  gráfico de funciones 1024x500
//   distribution/listing/graphics/phone/<archivo>.png   (solo con --capturas) capturas enmarcadas 1080x1920
// Uso: node scripts/ficha-play.mjs      (Chrome: variable CHROME, o ruta habitual de Windows/Linux)
//   node scripts/ficha-play.mjs --capturas [--raw <dir>] [--json <capturas.json>] [--salida <dir>]
//     --raw     capturas crudas del emulador          (por defecto build/ficha/raw)
//     --json    [{archivo, titular, subtitulo}, ...]  (por defecto distribution/listing/es-ES/capturas.json)
//     --salida  carpeta de gráficos                   (por defecto distribution/listing/graphics)
// Fuente: docs/assets/logo.svg. Colores: ui/theme/Color.kt (DarkColors y ColoresRally).
// Si hay Python con Pillow, aplana el icono a RGB (sin canal alfa); si no, se deja tal como lo saca Chrome (opaco).
// Las capturas exigen Pillow: se aplanan a RGB y se valida que midan 1080x1920 sin alfa.

import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const RAIZ = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const SALIDA_DEFECTO = join(RAIZ, 'distribution', 'listing', 'graphics');
const JSON_DEFECTO = join(RAIZ, 'distribution', 'listing', 'es-ES', 'capturas.json');
const RAW_DEFECTO = join(RAIZ, 'build', 'ficha', 'raw');
const ANCHO_PHONE = 1080;
const ALTO_PHONE = 1920;

const CARBON = '#0E0F12';
const TINTA = '#ECEDEF';
const TINTA2 = '#BFC3C9';
const LATON = '#E5AB46';
const FRANJA = '#D8232A';

function localizarChrome() {
  if (process.env.CHROME && existsSync(process.env.CHROME)) return process.env.CHROME;
  const candidatos =
    process.platform === 'win32'
      ? [
          'C:/Program Files/Google/Chrome/Application/chrome.exe',
          'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
        ]
      : ['/usr/bin/google-chrome', '/usr/bin/google-chrome-stable', '/usr/bin/chromium', '/usr/bin/chromium-browser'];
  const hallado = candidatos.find((c) => existsSync(c));
  if (!hallado) {
    console.error('ficha-play: no se encuentra Chrome; define la variable CHROME con la ruta del ejecutable');
    process.exit(1);
  }
  return hallado;
}

function leerSvg(nombre) {
  return readFileSync(join(RAIZ, 'docs', 'assets', nombre), 'utf8');
}

/** Devuelve el SVG con tamaño propio (y, opcionalmente, otro viewBox). */
function ajustarSvg(svg, { ancho, alto, viewBox }) {
  let s = svg.replace(/ width="\d+" height="\d+"/, ` width="${ancho}" height="${alto}"`);
  if (viewBox) s = s.replace(/viewBox="[^"]*"/, `viewBox="${viewBox}"`);
  return s;
}

function renderizar(chrome, html, ancho, alto, destino, tmp, nombre) {
  const archivoHtml = join(tmp, `${nombre}.html`);
  writeFileSync(archivoHtml, html, 'utf8');
  const r = spawnSync(
    chrome,
    [
      '--headless=new',
      '--disable-gpu',
      '--hide-scrollbars',
      '--force-device-scale-factor=1',
      `--window-size=${ancho},${alto}`,
      `--screenshot=${destino}`,
      pathToFileURL(archivoHtml).href,
    ],
    { encoding: 'utf8' },
  );
  if (r.status !== 0 || !existsSync(destino)) {
    console.error(`ficha-play: Chrome falló al generar ${destino}\n${r.stderr ?? ''}`);
    process.exit(1);
  }
}

function aplanarARgb(png) {
  const py = [
    'import sys',
    'from PIL import Image',
    'p = sys.argv[1]',
    "im = Image.open(p).convert('RGBA')",
    "bg = Image.new('RGB', im.size, (0x0E, 0x0F, 0x12))",
    'bg.paste(im, mask=im.split()[3])',
    'bg.save(p)',
  ].join('\n');
  for (const cmd of ['python', 'python3']) {
    const r = spawnSync(cmd, ['-c', py, png], { encoding: 'utf8' });
    if (r.status === 0) return true;
  }
  console.warn('ficha-play: Python/Pillow no disponible; el icono queda como lo generó Chrome (opaco)');
  return false;
}

function leerArgs(argv) {
  const a = { capturas: false, json: JSON_DEFECTO, salida: SALIDA_DEFECTO, raw: RAW_DEFECTO };
  for (let i = 0; i < argv.length; i++) {
    const k = argv[i];
    if (k === '--capturas') a.capturas = true;
    else if (k === '--json' || k === '--salida' || k === '--raw') {
      if (!argv[i + 1]) throw new Error(`falta el valor de ${k}`);
      a[k.slice(2)] = resolve(argv[++i]);
    } else throw new Error(`argumento desconocido: ${k}`);
  }
  return a;
}

function escaparHtml(t) {
  return String(t).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

/** Valida el contenido de capturas.json; devuelve la lista o lanza Error con un mensaje claro. */
export function validarCapturas(lista) {
  if (!Array.isArray(lista) || lista.length === 0) throw new Error('debe ser una lista no vacía');
  const vistos = new Set();
  lista.forEach((c, i) => {
    for (const campo of ['archivo', 'titular', 'subtitulo']) {
      if (typeof c?.[campo] !== 'string' || !c[campo].trim()) {
        throw new Error(`elemento ${i}: falta el campo "${campo}"`);
      }
    }
    if (!/^[\w.-]+\.png$/i.test(c.archivo)) throw new Error(`elemento ${i}: archivo no válido (${c.archivo})`);
    if (vistos.has(c.archivo)) throw new Error(`archivo repetido (${c.archivo})`);
    vistos.add(c.archivo);
  });
  return lista;
}

/** Ancho y alto de un PNG leyendo la cabecera IHDR. */
export function dimensionesPng(ruta) {
  const b = readFileSync(ruta);
  if (b.length < 24 || b.readUInt32BE(0) !== 0x89504e47) throw new Error(`${ruta} no es un PNG`);
  return { ancho: b.readUInt32BE(16), alto: b.readUInt32BE(20) };
}

/** HTML de una captura enmarcada: cabecera (barra roja, titular, subtítulo) y móvil dibujado con la captura. */
export function htmlCaptura({ titular, subtitulo, urlImagen, ancho, alto }) {
  const altoPantalla = 1372; // marco incluido, el móvil ocupa ~73 % del alto
  const anchoPantalla = Math.min(Math.round((altoPantalla * ancho) / alto), 900);
  const borde = 14;
  const tam = titular.length > 28 ? 88 : titular.length > 18 ? 100 : 112;
  return `<!doctype html><html><head><meta charset="utf-8"><style>
html,body{margin:0;padding:0;width:${ANCHO_PHONE}px;height:${ALTO_PHONE}px;overflow:hidden;background:${CARBON}}
body{position:relative;font-family:"Segoe UI",Roboto,"Helvetica Neue",Arial,sans-serif}
.fondo{position:absolute;inset:0;
 background:
  linear-gradient(112deg,transparent 0 56%,rgba(216,35,42,.17) 56% 63%,transparent 63% 66%,rgba(216,35,42,.07) 66% 68%,transparent 68%),
  repeating-linear-gradient(112deg,rgba(236,237,239,.028) 0 2px,transparent 2px 46px),
  radial-gradient(900px 700px at 80% 85%,rgba(216,35,42,.10),transparent 70%),${CARBON}}
.cabecera{position:absolute;left:72px;right:72px;top:70px;height:370px;overflow:hidden}
.raya{width:96px;height:8px;border-radius:4px;background:${FRANJA};margin:0 0 26px}
h1{margin:0;font-size:${tam}px;line-height:1.02;font-weight:900;font-style:italic;text-transform:uppercase;
 letter-spacing:-1px;color:${LATON};text-wrap:balance;overflow-wrap:break-word}
p{margin:22px 0 0;font-size:40px;line-height:1.3;font-weight:400;color:${TINTA2};text-wrap:balance;overflow-wrap:break-word}
.movil{position:absolute;left:50%;top:480px;transform:translateX(-50%);box-sizing:content-box;
 width:${anchoPantalla}px;height:${altoPantalla}px;padding:${borde}px;border-radius:92px;background:#16181C;
 border:3px solid #3C3F47;box-shadow:0 44px 90px rgba(0,0,0,.75),0 0 0 1px rgba(0,0,0,.9),0 0 120px rgba(216,35,42,.12)}
.pantalla{width:100%;height:100%;border-radius:78px;overflow:hidden;background:#000}
.pantalla img{display:block;width:100%;height:100%;object-fit:contain}
</style></head><body>
<div class="fondo"></div>
<div class="cabecera" id="cab"><div class="raya"></div><h1 id="t">${escaparHtml(titular)}</h1><p id="s">${escaparHtml(subtitulo)}</p></div>
<div class="movil"><div class="pantalla"><img src="${urlImagen}" alt=""></div></div>
<script>
var cab=document.getElementById('cab'),t=document.getElementById('t'),s=document.getElementById('s');
var tam=parseFloat(getComputedStyle(t).fontSize),sub=40;
while(cab.scrollHeight>cab.clientHeight&&tam>48){tam-=4;sub=Math.max(28,sub-1);t.style.fontSize=tam+'px';s.style.fontSize=sub+'px';}
</script>
</body></html>`;
}

/** Aplana a RGB y comprueba tamaño y ausencia de alfa; imprime el resultado. Termina con error si no cumple. */
function validarSalidas(archivos) {
  const py = [
    'import sys',
    'from PIL import Image',
    'bad = 0',
    'for p in sys.argv[1:]:',
    "    im = Image.open(p).convert('RGBA')",
    "    bg = Image.new('RGB', im.size, (0x0E, 0x0F, 0x12))",
    '    bg.paste(im, mask=im.split()[3])',
    '    bg.save(p)',
    '    chk = Image.open(p)',
    `    ok = chk.size == (${ANCHO_PHONE}, ${ALTO_PHONE}) and chk.mode == 'RGB'`,
    "    print(('OK ' if ok else 'ERROR ') + p + ' ' + str(chk.size[0]) + 'x' + str(chk.size[1]) + ' ' + chk.mode)",
    '    bad += 0 if ok else 1',
    'sys.exit(3 if bad else 0)',
  ].join('\n');
  for (const cmd of ['python', 'python3']) {
    const r = spawnSync(cmd, ['-I', '-c', py, ...archivos], { encoding: 'utf8' });
    if (r.stdout) process.stdout.write(r.stdout);
    if (r.status === 0) return;
    if (r.status === 3) {
      console.error(`ficha-play: alguna captura no mide ${ANCHO_PHONE}x${ALTO_PHONE} RGB sin alfa`);
      process.exit(1);
    }
  }
  console.error('ficha-play: las capturas necesitan Python con Pillow para aplanar y validar');
  process.exit(1);
}

function generarCapturas(chrome, args, tmp) {
  if (!existsSync(args.json)) {
    console.error(`ficha-play: no existe ${args.json}; crea el JSON [{"archivo","titular","subtitulo"}] o usa --json`);
    process.exit(1);
  }
  let lista;
  try {
    lista = validarCapturas(JSON.parse(readFileSync(args.json, 'utf8')));
  } catch (e) {
    console.error(`ficha-play: ${args.json}: ${e.message}`);
    process.exit(1);
  }
  const faltan = lista.filter((c) => !existsSync(join(args.raw, c.archivo))).map((c) => c.archivo);
  if (faltan.length) {
    console.error(`ficha-play: faltan capturas crudas en ${args.raw}: ${faltan.join(', ')}`);
    process.exit(1);
  }
  const citadas = new Set(lista.map((c) => c.archivo));
  for (const f of readdirSync(args.raw).filter((f) => /\.png$/i.test(f) && !citadas.has(f))) {
    console.warn(`ficha-play: aviso: ${f} está en ${args.raw} pero no en el JSON; se ignora`);
  }
  const destino = join(args.salida, 'phone');
  mkdirSync(destino, { recursive: true });
  const salidas = [];
  for (const c of lista) {
    const cruda = join(args.raw, c.archivo);
    const { ancho, alto } = dimensionesPng(cruda);
    const html = htmlCaptura({ ...c, urlImagen: pathToFileURL(cruda).href, ancho, alto });
    const png = join(destino, c.archivo);
    renderizar(chrome, html, ANCHO_PHONE, ALTO_PHONE, png, tmp, `captura-${c.archivo}`);
    salidas.push(png);
  }
  validarSalidas(salidas);
  console.log(`ficha-play: ${salidas.length} capturas enmarcadas en ${destino}`);
}

function generarBasicos(chrome, SALIDA, tmp) {
  // Icono: el SVG del repo a 512x512, sin esquinas redondeadas (rx=0).
  // En Play la mascota llena ~80 % del alto (el SVG la reduce al 75 % para el icono adaptativo de Android).
  const icono = ajustarSvg(leerSvg('logo.svg'), { ancho: 512, alto: 512 })
    .replace(/(<rect [^>]*?) rx="\d+"/, '$1 rx="0"')
    .replace(
      'transform="translate(0 8.4) translate(54 54) scale(0.75) translate(-54 -54)"',
      'transform="translate(2.2 11.4) translate(54 54) scale(1.12) translate(-54 -54)"',
    );
  const htmlIcono = `<!doctype html><html><head><meta charset="utf-8"><style>
html,body{margin:0;padding:0;background:${CARBON};overflow:hidden}svg{display:block}
</style></head><body>${icono}</body></html>`;
  const pngIcono = join(SALIDA, 'icon-512.png');
  renderizar(chrome, htmlIcono, 512, 512, pngIcono, tmp, 'icono');
  aplanarARgb(pngIcono);

  // Gráfico de funciones: la mascota del icono (con casco), sin su fondo, a la izquierda; texto a la derecha.
  const mascota = ajustarSvg(leerSvg('logo.svg'), { ancho: 368, alto: 400, viewBox: '24 18 57 68' }).replace(
    /<rect [^>]*\/>/,
    '',
  );
  const htmlFunciones = `<!doctype html><html><head><meta charset="utf-8"><style>
html,body{margin:0;padding:0;width:1024px;height:500px;overflow:hidden;background:${CARBON}}
body{display:flex;align-items:center;font-family:"Segoe UI",Roboto,"Helvetica Neue",Arial,sans-serif}
.mascota{margin-left:56px;flex:none;line-height:0}
.texto{margin-left:44px;width:410px}
h1{margin:0;font-size:80px;line-height:1;font-weight:900;font-style:italic;color:${LATON};letter-spacing:-1px}
.raya{width:72px;height:6px;border-radius:3px;background:${FRANJA};margin:26px 0 22px}
p{margin:0;font-size:28px;line-height:1.35;font-weight:400;color:${TINTA2}}
</style></head><body>
<div class="mascota">${mascota}</div>
<div class="texto"><h1>RALLY COPILOT</h1><div class="raya"></div>
<p>Tu copiloto en cada curva: notas de rally por voz, tiempos y tramos, sin conexión</p></div>
</body></html>`;
  renderizar(chrome, htmlFunciones, 1024, 500, join(SALIDA, 'feature-1024x500.png'), tmp, 'funciones');

  console.log(`ficha-play: generados icon-512.png y feature-1024x500.png en ${SALIDA}`);
}

function principal() {
  let args;
  try {
    args = leerArgs(process.argv.slice(2));
  } catch (e) {
    console.error(
      `ficha-play: ${e.message}\nUso: node scripts/ficha-play.mjs [--capturas] [--raw <dir>] [--json <ruta>] [--salida <dir>]`,
    );
    process.exit(2);
  }
  const chrome = localizarChrome();
  mkdirSync(args.salida, { recursive: true });
  const tmp = mkdtempSync(join(tmpdir(), 'ficha-play-'));
  generarBasicos(chrome, args.salida, tmp);
  if (args.capturas) generarCapturas(chrome, args, tmp);
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) principal();
