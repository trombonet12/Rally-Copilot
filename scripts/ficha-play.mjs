#!/usr/bin/env node
// Genera los gráficos de la ficha de Google Play con Chrome headless (sin dependencias):
//   distribution/listing/graphics/icon-512.png          icono 512x512, cuadrado y opaco (Play aplica su máscara)
//   distribution/listing/graphics/feature-1024x500.png  gráfico de funciones 1024x500
// Uso: node scripts/ficha-play.mjs      (Chrome: variable CHROME, o ruta habitual de Windows/Linux)
// Fuente: docs/assets/logo.svg. Colores: ui/theme/Color.kt (DarkColors y ColoresRally).
// Si hay Python con Pillow, aplana el icono a RGB (sin canal alfa); si no, se deja tal como lo saca Chrome (opaco).

import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const RAIZ = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const SALIDA = join(RAIZ, 'distribution', 'listing', 'graphics');

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

const chrome = localizarChrome();
mkdirSync(SALIDA, { recursive: true });
const tmp = mkdtempSync(join(tmpdir(), 'ficha-play-'));

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
