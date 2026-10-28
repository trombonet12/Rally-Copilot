#!/usr/bin/env node
// Promociona una versión ya subida a una pista de Google Play a otra (por defecto, de internal a alpha) con la
// Android Publisher API v3 (edits). No se puede subir el mismo AAB dos veces, así que se copia la release de la pista
// de origen a la de destino. Node 20+ sin dependencias (crypto + fetch global). Lo usa el job `play` de android.yml.
// La cuenta de servicio llega por la variable de entorno PLAY_SERVICE_ACCOUNT_JSON; nunca se imprime ni ella, ni su
// clave, ni el JWT, ni el token.
// Uso: node scripts/play-promocionar.mjs --paquete app.x --version-code 221 [--de internal] [--a alpha]
//        [--estado draft|completed] [--seco]
// --seco hace todo salvo el commit: borra la edición, así que no cambia nada en Play.

import { createSign } from 'node:crypto';
import { pathToFileURL } from 'node:url';

const API = 'https://androidpublisher.googleapis.com/androidpublisher/v3/applications';
const SCOPE = 'https://www.googleapis.com/auth/androidpublisher';
const ESTADOS = ['draft', 'completed'];

export class ErrorPromocion extends Error {}

const b64url = (buf) => Buffer.from(buf).toString('base64url');

/** Lee los argumentos (`--clave valor` o `--clave=valor`; `--seco` es booleano). */
export function leerArgs(argv) {
  const opciones = { de: 'internal', a: 'alpha', estado: 'draft', seco: false };
  const claves = { paquete: 'paquete', 'version-code': 'versionCode', de: 'de', a: 'a', estado: 'estado' };
  for (let i = 0; i < argv.length; i++) {
    let arg = argv[i];
    let valor;
    const igual = arg.indexOf('=');
    if (arg.startsWith('--') && igual > 0) {
      valor = arg.slice(igual + 1);
      arg = arg.slice(0, igual);
    }
    const nombre = arg.replace(/^--/, '');
    if (!arg.startsWith('--')) throw new ErrorPromocion(`argumento inesperado: ${arg}`);
    if (nombre === 'seco') {
      opciones.seco = true;
      continue;
    }
    if (!(nombre in claves)) throw new ErrorPromocion(`opción desconocida: --${nombre}`);
    if (valor === undefined) valor = argv[++i];
    if (valor === undefined || valor.startsWith('--')) throw new ErrorPromocion(`falta el valor de --${nombre}`);
    opciones[claves[nombre]] = valor;
  }
  if (!opciones.paquete) throw new ErrorPromocion('falta --paquete');
  if (!/^[0-9]+$/.test(opciones.versionCode ?? '')) throw new ErrorPromocion('--version-code debe ser un entero');
  if (!ESTADOS.includes(opciones.estado)) throw new ErrorPromocion('--estado debe ser draft o completed');
  for (const pista of [opciones.de, opciones.a]) {
    if (!/^[A-Za-z0-9_.-]+$/.test(pista)) throw new ErrorPromocion(`nombre de pista inválido: ${pista}`);
  }
  if (opciones.de === opciones.a) throw new ErrorPromocion('--de y --a son la misma pista');
  // Producción se promociona a mano en Play Console: el CI solo mueve versiones entre pistas de prueba.
  if (opciones.a === 'production') throw new ErrorPromocion('--a production no está permitido: solo pistas de prueba');
  return opciones;
}

/** Devuelve la release de la pista que contiene el versionCode, o lanza ErrorPromocion. */
export function buscarRelease(track, versionCode, nombrePista = track?.track ?? '?') {
  const release = (track?.releases ?? []).find((r) => (r.versionCodes ?? []).map(String).includes(String(versionCode)));
  if (!release) {
    throw new ErrorPromocion(`la pista ${nombrePista} no tiene ninguna release con el versionCode ${versionCode}`);
  }
  return release;
}

/** Cuerpo del PUT a la pista de destino: la release de origen con el estado pedido. */
export function construirCuerpoTrack(release, pistaDestino, estado) {
  const nueva = {
    versionCodes: (release.versionCodes ?? []).map(String),
    status: estado,
  };
  if (release.name !== undefined) nueva.name = release.name;
  if (release.releaseNotes !== undefined) nueva.releaseNotes = release.releaseNotes;
  if (release.inAppUpdatePriority !== undefined) nueva.inAppUpdatePriority = release.inAppUpdatePriority;
  return { track: pistaDestino, releases: [nueva] };
}

/** Lee y valida la cuenta de servicio sin filtrar su contenido en los errores. */
export function leerCuenta(texto) {
  let cuenta;
  try {
    cuenta = JSON.parse(texto ?? '');
  } catch {
    throw new ErrorPromocion('PLAY_SERVICE_ACCOUNT_JSON no es un JSON válido');
  }
  if (typeof cuenta?.client_email !== 'string' || typeof cuenta?.private_key !== 'string') {
    throw new ErrorPromocion('PLAY_SERVICE_ACCOUNT_JSON no tiene client_email y private_key');
  }
  const tokenUri = cuenta.token_uri ?? 'https://oauth2.googleapis.com/token';
  if (!String(tokenUri).startsWith('https://')) throw new ErrorPromocion('token_uri debe ser https');
  return { email: cuenta.client_email, clave: cuenta.private_key, tokenUri };
}

/** JWT RS256 para el flujo de cuenta de servicio. */
export function firmarJwt({ email, clave, tokenUri }, ahora = Math.floor(Date.now() / 1000)) {
  const cabecera = b64url(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const cuerpo = b64url(JSON.stringify({ iss: email, scope: SCOPE, aud: tokenUri, iat: ahora, exp: ahora + 3600 }));
  const firmante = createSign('RSA-SHA256');
  firmante.update(`${cabecera}.${cuerpo}`);
  let firma;
  try {
    firma = firmante.sign(clave);
  } catch {
    throw new ErrorPromocion('no se pudo firmar el JWT: la private_key no es una clave RSA válida');
  }
  return `${cabecera}.${cuerpo}.${b64url(firma)}`;
}

async function leerJson(res) {
  try {
    return await res.json();
  } catch {
    return null;
  }
}

function mensajeGoogle(datos) {
  if (!datos) return 'sin cuerpo de error';
  return datos.error?.message ?? datos.error_description ?? (typeof datos.error === 'string' ? datos.error : 'sin mensaje');
}

/**
 * Promociona la release. `deps.fetch` y `deps.env` se pueden inyectar (tests); `deps.out`/`deps.err` reciben las
 * líneas de salida. Devuelve el código de salida.
 */
export async function promocionar(opciones, deps = {}) {
  const hacerFetch = deps.fetch ?? globalThis.fetch;
  const env = deps.env ?? process.env;
  const out = deps.out ?? ((l) => console.log(l));
  const err = deps.err ?? ((l) => console.error(l));
  let edicion = null;
  let token = null;

  async function llamar(metodo, url, { cuerpo, formulario, auth = true } = {}) {
    const cabeceras = {};
    if (auth) cabeceras.Authorization = `Bearer ${token}`;
    let body;
    if (formulario) {
      cabeceras['Content-Type'] = 'application/x-www-form-urlencoded';
      body = new URLSearchParams(formulario).toString();
    } else if (cuerpo !== undefined) {
      cabeceras['Content-Type'] = 'application/json';
      body = JSON.stringify(cuerpo);
    }
    let res;
    try {
      res = await hacerFetch(url, { method: metodo, headers: cabeceras, body });
    } catch {
      throw new ErrorPromocion(`fallo de red en ${metodo} ${new URL(url).host}`);
    }
    const datos = await leerJson(res);
    if (!res.ok) {
      throw new ErrorPromocion(`${metodo} ${new URL(url).pathname.replace(/^.*\/applications\//, '')}: HTTP ${res.status}: ${mensajeGoogle(datos)}`);
    }
    return datos;
  }

  try {
    const cuenta = leerCuenta(env.PLAY_SERVICE_ACCOUNT_JSON);
    const jwt = firmarJwt(cuenta);
    const respuestaToken = await llamar('POST', cuenta.tokenUri, {
      auth: false,
      formulario: { grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion: jwt },
    });
    token = respuestaToken?.access_token;
    if (!token) throw new ErrorPromocion('el servidor de tokens no devolvió access_token');

    const base = `${API}/${encodeURIComponent(opciones.paquete)}/edits`;
    const creada = await llamar('POST', base, { cuerpo: {} });
    edicion = creada?.id;
    if (!edicion) throw new ErrorPromocion('Google no devolvió el id de la edición');

    const origen = await llamar('GET', `${base}/${edicion}/tracks/${encodeURIComponent(opciones.de)}`);
    const release = buscarRelease(origen, opciones.versionCode, opciones.de);
    const cuerpo = construirCuerpoTrack(release, opciones.a, opciones.estado);
    await llamar('PUT', `${base}/${edicion}/tracks/${encodeURIComponent(opciones.a)}`, { cuerpo });

    if (opciones.seco) {
      await llamar('DELETE', `${base}/${edicion}`);
      edicion = null;
      out(`[seco] ${opciones.de} -> ${opciones.a}: versionCode ${opciones.versionCode} (${opciones.estado}) validado; edición borrada, sin cambios en Play.`);
    } else {
      const id = edicion;
      edicion = null; // si el commit falla, la edición ya no se puede borrar de forma útil
      await llamar('POST', `${base}/${id}:commit`);
      out(`Promocionado ${opciones.de} -> ${opciones.a}: versionCode ${opciones.versionCode} (${opciones.estado}).`);
    }
    return 0;
  } catch (e) {
    err(`::error::${e instanceof ErrorPromocion ? e.message : 'error inesperado en play-promocionar'}`);
    if (edicion && token) {
      try {
        await hacerFetch(`${API}/${encodeURIComponent(opciones.paquete)}/edits/${edicion}`, {
          method: 'DELETE',
          headers: { Authorization: `Bearer ${token}` },
        });
      } catch {
        // Limpieza best effort: la edición caduca sola.
      }
    }
    return 1;
  }
}

export async function main(argv, deps = {}) {
  let opciones;
  try {
    opciones = leerArgs(argv);
  } catch (e) {
    (deps.err ?? ((l) => console.error(l)))(`::error::play-promocionar: ${e.message}`);
    return 1;
  }
  return promocionar(opciones, deps);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  process.exitCode = await main(process.argv.slice(2));
}
