import { strict as assert } from 'node:assert';
import { spawnSync } from 'node:child_process';
import { existsSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { dimensionesPng, htmlCaptura, validarCapturas } from './ficha-play.mjs';

const RAIZ = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const buena = { archivo: '01-notas.png', titular: 'Notas', subtitulo: 'Por voz' };

test('validarCapturas acepta una lista correcta', () => {
  assert.deepEqual(validarCapturas([buena]), [buena]);
});

test('validarCapturas rechaza lista vacía, campos vacíos, archivos no png o repetidos', () => {
  assert.throws(() => validarCapturas([]), /lista no vacía/);
  assert.throws(() => validarCapturas([{ ...buena, titular: ' ' }]), /titular/);
  assert.throws(() => validarCapturas([{ ...buena, subtitulo: undefined }]), /subtitulo/);
  assert.throws(() => validarCapturas([{ ...buena, archivo: '../x.jpg' }]), /archivo no válido/);
  assert.throws(() => validarCapturas([buena, buena]), /repetido/);
});

test('dimensionesPng lee la cabecera de una captura real', () => {
  const ruta = join(RAIZ, 'docs', 'capturas', 'h26-h29', '06-inicio.png');
  if (!existsSync(ruta)) return;
  const d = dimensionesPng(ruta);
  assert.ok(d.ancho > 0 && d.alto > d.ancho);
});

test('htmlCaptura escapa el texto y usa TINTA2/LATON/FRANJA y ajuste de línea', () => {
  const html = htmlCaptura({ titular: 'A <b> & "B"', subtitulo: 's', urlImagen: 'file:///x.png', ancho: 1080, alto: 1920 });
  assert.match(html, /A &lt;b&gt; &amp; &quot;B&quot;/);
  assert.match(html, /text-wrap:balance/);
  assert.match(html, /#E5AB46/);
  assert.match(html, /#D8232A/);
  assert.match(html, /height:1372px/);
});

test('falla con mensaje claro si falta una captura citada en el JSON', (t) => {
  const chrome = process.env.CHROME ?? 'C:/Program Files/Google/Chrome/Application/chrome.exe';
  if (!existsSync(chrome) && !existsSync('/usr/bin/google-chrome') && !existsSync('/usr/bin/chromium')) {
    t.skip('Chrome no disponible');
    return;
  }
  const dir = mkdtempSync(join(tmpdir(), 'ficha-test-'));
  const json = join(dir, 'c.json');
  writeFileSync(json, JSON.stringify([buena]));
  const r = spawnSync(
    process.execPath,
    [join(RAIZ, 'scripts', 'ficha-play.mjs'), '--capturas', '--json', json, '--raw', join(dir, 'raw'), '--salida', join(dir, 'out')],
    { encoding: 'utf8' },
  );
  assert.equal(r.status, 1);
  assert.match(r.stderr, /faltan capturas crudas.*01-notas\.png/);
});
