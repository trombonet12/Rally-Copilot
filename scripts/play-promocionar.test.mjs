// node --test scripts/play-promocionar.test.mjs
import assert from 'node:assert/strict';
import { generateKeyPairSync } from 'node:crypto';
import { test } from 'node:test';
import {
  buscarRelease,
  construirCuerpoTrack,
  firmarJwt,
  leerArgs,
  main,
} from './play-promocionar.mjs';

const { privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const CLAVE_PEM = privateKey.export({ type: 'pkcs8', format: 'pem' });
const CLAVE_CUERPO = CLAVE_PEM.split('\n').filter((l) => l && !l.startsWith('-----'))[0];
const TOKEN = 'ya29.TOKEN-SECRETO-DE-PRUEBA';
const CUENTA = JSON.stringify({
  client_email: 'ci@proyecto.iam.gserviceaccount.com',
  private_key: CLAVE_PEM,
  token_uri: 'https://oauth2.googleapis.com/token',
});
const ARGS = ['--paquete', 'app.trombito.rallycopilot', '--version-code', '221', '--estado', 'completed'];

const RELEASE_INTERNA = {
  name: '1.6.4',
  versionCodes: ['221'],
  status: 'completed',
  releaseNotes: [{ language: 'es-ES', text: 'Novedades' }],
  inAppUpdatePriority: 5,
  userFraction: 1,
};

/** fetch falso: registra las llamadas y responde según método y ruta. */
function falso({ track = { track: 'internal', releases: [RELEASE_INTERNA] }, falla = {} } = {}) {
  const llamadas = [];
  const fetch = async (url, init = {}) => {
    const u = new URL(url);
    const llamada = { metodo: init.method, ruta: u.pathname, cuerpo: init.body, cabeceras: init.headers };
    llamadas.push(llamada);
    const clave = `${init.method} ${u.pathname}`;
    const responder = (status, datos) => ({ ok: status < 400, status, json: async () => datos });
    for (const [patron, respuesta] of Object.entries(falla)) {
      if (clave.includes(patron)) return responder(respuesta.status, respuesta.datos);
    }
    if (u.host === 'oauth2.googleapis.com') return responder(200, { access_token: TOKEN });
    if (clave.endsWith('/edits') && init.method === 'POST') return responder(200, { id: 'ed1' });
    if (clave.includes('/tracks/internal') && init.method === 'GET') return responder(200, track);
    if (clave.includes('/tracks/') && init.method === 'PUT') return responder(200, JSON.parse(init.body));
    if (clave.endsWith(':commit')) return responder(200, { id: 'ed1' });
    if (init.method === 'DELETE') return responder(204, null);
    return responder(404, { error: { message: `ruta no prevista ${clave}` } });
  };
  return { fetch, llamadas };
}

function capturar() {
  const lineas = [];
  return { out: (l) => lineas.push(String(l)), err: (l) => lineas.push(String(l)), lineas };
}

test('buscarRelease encuentra la release por versionCode (cadena o número)', () => {
  assert.equal(buscarRelease({ releases: [RELEASE_INTERNA] }, 221), RELEASE_INTERNA);
  assert.equal(buscarRelease({ releases: [{ versionCodes: ['1'] }, RELEASE_INTERNA] }, '221'), RELEASE_INTERNA);
});

test('buscarRelease falla si el versionCode no está', () => {
  assert.throws(() => buscarRelease({ releases: [RELEASE_INTERNA] }, 999, 'internal'), /internal.*999/);
  assert.throws(() => buscarRelease({}, 1), /versionCode 1/);
});

test('construirCuerpoTrack copia la release y aplica el estado', () => {
  assert.deepEqual(construirCuerpoTrack(RELEASE_INTERNA, 'alpha', 'draft'), {
    track: 'alpha',
    releases: [
      {
        versionCodes: ['221'],
        status: 'draft',
        name: '1.6.4',
        releaseNotes: [{ language: 'es-ES', text: 'Novedades' }],
        inAppUpdatePriority: 5,
      },
    ],
  });
  const minima = construirCuerpoTrack({ versionCodes: [7] }, 'alpha', 'completed');
  assert.deepEqual(minima.releases[0], { versionCodes: ['7'], status: 'completed' });
});

test('leerArgs aplica valores por defecto y valida', () => {
  const o = leerArgs(['--paquete=a.b', '--version-code', '5']);
  assert.deepEqual(o, { de: 'internal', a: 'alpha', estado: 'draft', seco: false, paquete: 'a.b', versionCode: '5' });
  assert.equal(leerArgs([...ARGS, '--seco']).seco, true);
  assert.throws(() => leerArgs(['--version-code', '5']), /--paquete/);
  assert.throws(() => leerArgs(['--paquete', 'a', '--version-code', 'x']), /entero/);
  assert.throws(() => leerArgs([...ARGS, '--estado', 'otro']), /draft o completed/);
  assert.throws(() => leerArgs([...ARGS, '--a', 'internal']), /misma pista/);
  assert.throws(() => leerArgs([...ARGS, '--a', 'production']), /solo pistas de prueba/);
});

test('firmarJwt devuelve tres partes con la cabecera RS256', () => {
  const jwt = firmarJwt({ email: 'x@y', clave: CLAVE_PEM, tokenUri: 'https://t' }, 1000);
  const [cab, cuerpo, firma] = jwt.split('.');
  assert.deepEqual(JSON.parse(Buffer.from(cab, 'base64url')), { alg: 'RS256', typ: 'JWT' });
  const claims = JSON.parse(Buffer.from(cuerpo, 'base64url'));
  assert.equal(claims.scope, 'https://www.googleapis.com/auth/androidpublisher');
  assert.equal(claims.exp, 4600);
  assert.ok(firma.length > 100);
});

test('flujo completo: PUT con el cuerpo esperado y commit', async () => {
  const { fetch, llamadas } = falso();
  const salida = capturar();
  const codigo = await main(ARGS, { fetch, env: { PLAY_SERVICE_ACCOUNT_JSON: CUENTA }, ...salida });
  assert.equal(codigo, 0, salida.lineas.join('\n'));
  const put = llamadas.find((l) => l.metodo === 'PUT');
  assert.match(put.ruta, /\/applications\/app.trombito.rallycopilot\/edits\/ed1\/tracks\/alpha$/);
  assert.deepEqual(JSON.parse(put.cuerpo), construirCuerpoTrack(RELEASE_INTERNA, 'alpha', 'completed'));
  assert.equal(put.cabeceras.Authorization, `Bearer ${TOKEN}`);
  assert.ok(llamadas.some((l) => l.ruta.endsWith('/edits/ed1:commit') && l.metodo === 'POST'));
  assert.ok(!llamadas.some((l) => l.metodo === 'DELETE'));
});

test('falta el versionCode en la pista de origen: error y se borra la edición', async () => {
  const { fetch, llamadas } = falso();
  const salida = capturar();
  const codigo = await main(['--paquete', 'app.x', '--version-code', '999'], {
    fetch,
    env: { PLAY_SERVICE_ACCOUNT_JSON: CUENTA },
    ...salida,
  });
  assert.equal(codigo, 1);
  assert.match(salida.lineas.join('\n'), /::error::.*internal.*999/);
  assert.ok(!llamadas.some((l) => l.metodo === 'PUT' || l.ruta.endsWith(':commit')));
  assert.ok(llamadas.some((l) => l.metodo === 'DELETE' && l.ruta.endsWith('/edits/ed1')));
});

test('--seco borra la edición en vez de hacer commit', async () => {
  const { fetch, llamadas } = falso();
  const salida = capturar();
  const codigo = await main([...ARGS, '--seco'], { fetch, env: { PLAY_SERVICE_ACCOUNT_JSON: CUENTA }, ...salida });
  assert.equal(codigo, 0, salida.lineas.join('\n'));
  assert.ok(llamadas.some((l) => l.metodo === 'PUT'));
  assert.ok(!llamadas.some((l) => l.ruta.endsWith(':commit')));
  assert.equal(llamadas.filter((l) => l.metodo === 'DELETE').length, 1);
  assert.match(salida.lineas.join('\n'), /seco/);
});

test('error de Google: ::error:: con el código HTTP y el mensaje', async () => {
  const { fetch, llamadas } = falso({
    falla: { '/tracks/alpha': { status: 403, datos: { error: { message: 'The caller does not have permission' } } } },
  });
  const salida = capturar();
  const codigo = await main(ARGS, { fetch, env: { PLAY_SERVICE_ACCOUNT_JSON: CUENTA }, ...salida });
  assert.equal(codigo, 1);
  assert.match(salida.lineas.join('\n'), /::error::.*HTTP 403.*The caller does not have permission/);
  assert.ok(llamadas.some((l) => l.metodo === 'DELETE'));
});

test('la salida nunca contiene la clave, el JWT ni el token', async () => {
  const jwtsVistos = [];
  const escenarios = [
    falso(),
    falso({ track: { releases: [] } }),
    falso({ falla: { '/tracks/alpha': { status: 400, datos: { error: { message: 'mala peticion' } } } } }),
  ];
  for (const { fetch } of escenarios) {
    const envolver = async (url, init) => {
      if (String(init?.body).includes('assertion=')) jwtsVistos.push(new URLSearchParams(init.body).get('assertion'));
      return fetch(url, init);
    };
    for (const extra of [[], ['--seco']]) {
      const salida = capturar();
      await main([...ARGS, ...extra], { fetch: envolver, env: { PLAY_SERVICE_ACCOUNT_JSON: CUENTA }, ...salida });
      const texto = salida.lineas.join('\n');
      assert.ok(!texto.includes(CLAVE_CUERPO), 'clave privada en la salida');
      assert.ok(!texto.includes('BEGIN PRIVATE KEY'), 'cabecera PEM en la salida');
      assert.ok(!texto.includes(TOKEN), 'token en la salida');
      for (const jwt of jwtsVistos) assert.ok(!texto.includes(jwt), 'JWT en la salida');
    }
  }
  assert.ok(jwtsVistos.length > 0);
});

test('JSON de cuenta inválido: error sin volcar el contenido', async () => {
  const salida = capturar();
  const codigo = await main(ARGS, {
    fetch: falso().fetch,
    env: { PLAY_SERVICE_ACCOUNT_JSON: '{"private_key": "SECRETO-PARCIAL' },
    ...salida,
  });
  assert.equal(codigo, 1);
  assert.ok(!salida.lineas.join('\n').includes('SECRETO-PARCIAL'));
});
