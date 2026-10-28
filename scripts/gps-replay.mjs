#!/usr/bin/env node
// Reproduce una traza GPX/KML en el emulador enviando `adb emu geo fix` (longitud primero) a ~1 Hz.
// Uso: node scripts/gps-replay.mjs <ruta.gpx|ruta.kml> [--kmh 60 | --velocidad x2] [--desde <índice|Nkm>]
//      [--serial emulator-5554] [--seco]
//   Sin --kmh: respeta los <time> del GPX (con --velocidad los acelera o frena). Sin tiempos, --kmh es obligatorio.
//   --desde 120 empieza en el punto 120; --desde 3.5km, a 3,5 km del inicio (p. ej. tras un túnel simulado).
//   --seco imprime las posiciones sin llamar a adb ni esperar (para revisar o probar).
// Solo funciona con el emulador: en un móvil físico hace falta una app de ubicación simulada.

import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { setTimeout as esperar } from 'node:timers/promises';
import { fileURLToPath } from 'node:url';

const RADIO_TIERRA_M = 6371008.8;
const NUDOS_POR_MS = 1.943844;

/** Distancia haversine en metros (misma fórmula que PuntoGeo.distanciaA en :motor). */
export function distancia(a, b) {
  const rad = Math.PI / 180;
  const dLat = (b.lat - a.lat) * rad;
  const dLon = (b.lon - a.lon) * rad;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(a.lat * rad) * Math.cos(b.lat * rad) * Math.sin(dLon / 2) ** 2;
  return 2 * RADIO_TIERRA_M * Math.asin(Math.sqrt(h));
}

/** Puntos {lat, lon, ele?, t?} (t en ms) de un GPX (trkpt/rtept) o un KML (LineString o gx:Track). */
export function parsear(texto) {
  if (/<gpx[\s>]/i.test(texto)) {
    const puntos = [];
    for (const m of texto.matchAll(/<(trkpt|rtept)\b([^>]*?)(\/>|>([\s\S]*?)<\/\1>)/g)) {
      const lat = Number(/lat="([^"]+)"/.exec(m[2])?.[1]);
      const lon = Number(/lon="([^"]+)"/.exec(m[2])?.[1]);
      const cuerpo = m[4] ?? '';
      const ele = /<ele>([^<]+)<\/ele>/.exec(cuerpo)?.[1];
      const time = /<time>([^<]+)<\/time>/.exec(cuerpo)?.[1];
      puntos.push({ lat, lon, ele: ele === undefined ? undefined : Number(ele), t: time ? Date.parse(time) : undefined });
    }
    return validar(puntos);
  }
  if (/<kml[\s>]/i.test(texto)) {
    const coords = [...texto.matchAll(/<gx:coord>([^<]+)<\/gx:coord>/g)].map((m) => m[1].trim().split(/\s+/));
    const whens = [...texto.matchAll(/<when>([^<]+)<\/when>/g)].map((m) => Date.parse(m[1]));
    if (coords.length > 0) {
      return validar(
        coords.map(([lon, lat, ele], i) => ({
          lat: Number(lat),
          lon: Number(lon),
          ele: ele === undefined ? undefined : Number(ele),
          t: whens.length === coords.length ? whens[i] : undefined,
        })),
      );
    }
    const linea = /<LineString>[\s\S]*?<coordinates>([\s\S]*?)<\/coordinates>/.exec(texto)?.[1] ?? '';
    return validar(
      linea
        .trim()
        .split(/\s+/)
        .filter(Boolean)
        .map((c) => {
          const [lon, lat, ele] = c.split(',').map(Number);
          return { lat, lon, ele };
        }),
    );
  }
  throw new Error('Formato no reconocido: se espera GPX o KML');
}

function validar(puntos) {
  if (puntos.length < 2) throw new Error(`La traza necesita al menos 2 puntos (tiene ${puntos.length})`);
  for (const [i, p] of puntos.entries()) {
    if (!(Math.abs(p.lat) <= 90 && Math.abs(p.lon) <= 180)) throw new Error(`Punto ${i} con coordenadas no válidas`);
  }
  return puntos;
}

/** Índice del primer punto a partir de `desde` («120» = índice, «3.5km» = distancia desde el inicio). */
export function indiceDesde(puntos, desde) {
  if (desde === undefined) return 0;
  const km = /^([\d.]+)\s*km$/i.exec(desde);
  if (!km) return Math.min(Math.max(0, Number.parseInt(desde, 10) || 0), puntos.length - 1);
  const objetivo = Number(km[1]) * 1000;
  let acumulado = 0;
  for (let i = 1; i < puntos.length; i++) {
    acumulado += distancia(puntos[i - 1], puntos[i]);
    if (acumulado >= objetivo) return i;
  }
  return puntos.length - 1;
}

function interpolar(a, b, f) {
  const ele = a.ele !== undefined && b.ele !== undefined ? a.ele + (b.ele - a.ele) * f : (a.ele ?? b.ele);
  return { lat: a.lat + (b.lat - a.lat) * f, lon: a.lon + (b.lon - a.lon) * f, ele };
}

/**
 * Plan de envíos: [{lat, lon, ele, esperaMs, ms}] con `esperaMs` desde el envío anterior y `ms` = velocidad (m/s).
 * Con `kmh`, recorre a velocidad constante con un envío por segundo; si no, usa los tiempos de la traza / factor.
 */
export function planificar(puntos, { kmh, factor = 1 } = {}) {
  if (kmh !== undefined) {
    const ms = kmh / 3.6;
    if (!(ms > 0)) throw new Error('--kmh debe ser mayor que 0');
    const plan = [{ ...interpolar(puntos[0], puntos[0], 0), esperaMs: 0, ms }];
    let resto = ms; // metros hasta el siguiente envío
    for (let i = 1; i < puntos.length; i++) {
      const tramo = distancia(puntos[i - 1], puntos[i]);
      let recorrido = 0;
      while (tramo - recorrido >= resto) {
        recorrido += resto;
        plan.push({ ...interpolar(puntos[i - 1], puntos[i], tramo === 0 ? 1 : recorrido / tramo), esperaMs: 1000, ms });
        resto = ms;
      }
      resto -= tramo - recorrido;
    }
    const ultimo = puntos.at(-1);
    if (distancia(plan.at(-1), ultimo) > 0.5) {
      plan.push({ ...interpolar(ultimo, ultimo, 0), esperaMs: Math.round(((ms - resto) / ms) * 1000), ms });
    }
    return plan;
  }
  if (puntos.some((p) => p.t === undefined || Number.isNaN(p.t))) {
    throw new Error('La traza no tiene <time> en todos los puntos: usa --kmh <velocidad>');
  }
  if (!(factor > 0)) throw new Error('--velocidad debe ser mayor que 0 (p. ej. x2)');
  return puntos.map((p, i) => {
    if (i === 0) return { ...interpolar(p, p, 0), esperaMs: 0, ms: 0 };
    const dt = (p.t - puntos[i - 1].t) / 1000;
    const ms = dt > 0 ? distancia(puntos[i - 1], p) / dt : 0;
    return { ...interpolar(p, p, 0), esperaMs: Math.max(0, Math.round((dt * 1000) / factor)), ms: ms * factor };
  });
}

/** Argumentos de `adb emu geo fix`: lon lat alt satélites velocidad(nudos). */
export function argumentosGeoFix(fix) {
  const alt = fix.ele ?? 0;
  return [
    'emu',
    'geo',
    'fix',
    fix.lon.toFixed(7),
    fix.lat.toFixed(7),
    alt.toFixed(1),
    '8',
    (fix.ms * NUDOS_POR_MS).toFixed(2),
  ];
}

function leerOpciones(argv) {
  const opciones = { archivo: undefined, kmh: undefined, factor: 1, desde: undefined, serial: undefined, seco: false };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--kmh') opciones.kmh = Number(argv[++i]);
    else if (a === '--velocidad') opciones.factor = Number(String(argv[++i]).replace(/^x/i, ''));
    else if (a === '--desde') opciones.desde = argv[++i];
    else if (a === '--serial') opciones.serial = argv[++i];
    else if (a === '--seco') opciones.seco = true;
    else if (!a.startsWith('--') && opciones.archivo === undefined) opciones.archivo = a;
    else throw new Error(`Opción desconocida: ${a}`);
  }
  if (!opciones.archivo) throw new Error('Falta la traza: node scripts/gps-replay.mjs <ruta.gpx|ruta.kml> [opciones]');
  return opciones;
}

async function main() {
  const op = leerOpciones(process.argv.slice(2));
  const todos = parsear(readFileSync(op.archivo, 'utf8'));
  const puntos = todos.slice(indiceDesde(todos, op.desde));
  const plan = planificar(puntos.length >= 2 ? puntos : todos.slice(-2), { kmh: op.kmh, factor: op.factor });
  const adb = process.env.ADB ?? 'adb';
  const serial = op.serial ? ['-s', op.serial] : [];
  let recorrido = 0;
  for (const [i, fix] of plan.entries()) {
    if (!op.seco && fix.esperaMs > 0) await esperar(fix.esperaMs);
    if (i > 0) recorrido += distancia(plan[i - 1], fix);
    const args = argumentosGeoFix(fix);
    if (op.seco) {
      console.log(`${i}\t+${fix.esperaMs} ms\t${args.slice(3).join(' ')}`);
      continue;
    }
    const r = spawnSync(adb, [...serial, ...args], { encoding: 'utf8' });
    if (r.status !== 0 || /KO|error/i.test(r.stdout + r.stderr)) {
      throw new Error(`adb emu geo fix falló: ${(r.stderr || r.stdout || r.error?.message || '').trim()}`);
    }
    if (i % 10 === 0 || i === plan.length - 1) {
      console.log(`gps-replay: ${i + 1}/${plan.length} · ${(recorrido / 1000).toFixed(2)} km · ${(fix.ms * 3.6).toFixed(0)} km/h`);
    }
  }
  console.log(`gps-replay: fin (${plan.length} posiciones, ${(recorrido / 1000).toFixed(2)} km)`);
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  main().catch((e) => {
    console.error(`gps-replay: ${e.message}`);
    process.exit(1);
  });
}
