#!/usr/bin/env node
// Humo del release minificado (QA-3): tras `:app:minifyReleaseWithR8` comprueba en mapping.txt y usage.txt que
// sobreviven, sin renombrar ni borrar, las clases que las reglas keep de app/proguard-rules.pro protegen (JNI de
// sherpa-onnx, Vosk, JNA y los serializadores de kotlinx.serialization). Un keep que falta rompe la voz o la navegación
// solo en producción y los tests (que corren sin minificar) no lo ven.
// Uso: node scripts/humo-release.mjs [directorio de mapping] (por defecto app/build/outputs/mapping/release)

import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

export const DIRECTORIO_POR_DEFECTO = 'app/build/outputs/mapping/release';

/** Clases que el JNI (sherpa-onnx), JNA y Vosk buscan por nombre: tienen que seguir con su nombre y sin borrarse. */
export const CLASES_OBLIGATORIAS = [
  'com.k2fsa.sherpa.onnx.OfflineTts',
  'com.k2fsa.sherpa.onnx.OfflineTtsConfig',
  'com.k2fsa.sherpa.onnx.OfflineTtsModelConfig',
  'com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig',
  'com.k2fsa.sherpa.onnx.GeneratedAudio',
  'org.vosk.LibVosk',
  'org.vosk.Model',
  'org.vosk.Recognizer',
  'com.sun.jna.Native',
  'com.sun.jna.Library',
  'com.sun.jna.Pointer',
  'com.sun.jna.Structure',
  'com.sun.jna.Callback',
  // Serializadores generados (rutas de navegación y modelos): -keep ...$$serializer.
  'app.trombito.rallycopilot.motor.datos.ManifestDatos$$serializer',
  'app.trombito.rallycopilot.motor.datos.ArchivoDatos$$serializer',
];

/** Paquetes enteros que las reglas keep no dejan renombrar (salvo lo que R8 sintetiza, como las lambdas). */
export const PAQUETES_SIN_RENOMBRAR = ['com.k2fsa.sherpa.onnx.', 'org.vosk.', 'com.sun.jna.'];

const SINTETICA = /\$\$ExternalSynthetic|\$\$Lambda/;

/** Líneas de clase de mapping.txt (`origen -> destino:`) como Map origen -> destino. */
export function clasesDelMapping(textoMapping) {
  const clases = new Map();
  for (const linea of textoMapping.split(/\r?\n/)) {
    if (linea.startsWith(' ') || linea.startsWith('#') || !linea.endsWith(':')) continue;
    const i = linea.indexOf(' -> ');
    if (i > 0) clases.set(linea.slice(0, i), linea.slice(i + 4, -1));
  }
  return clases;
}

/** Clases borradas por completo según usage.txt: líneas sin sangría y sin `:` final (las que acaban en `:` son miembros). */
export function clasesBorradas(textoUsage) {
  const borradas = new Set();
  for (const linea of textoUsage.split(/\r?\n/)) {
    if (linea === '' || linea.startsWith(' ') || linea.endsWith(':')) continue;
    borradas.add(linea.trim());
  }
  return borradas;
}

/** Devuelve la lista de problemas (vacía si todo sobrevive). */
export function comprobar(textoMapping, textoUsage) {
  const problemas = [];
  const clases = clasesDelMapping(textoMapping);
  const borradas = clasesBorradas(textoUsage);
  if (clases.size === 0) problemas.push('mapping.txt no trae ninguna clase');

  for (const nombre of CLASES_OBLIGATORIAS) {
    if (!clases.has(nombre)) {
      problemas.push(`Falta la clase ${nombre} (R8 la borró o no llegó al mapping)`);
    } else if (clases.get(nombre) !== nombre) {
      problemas.push(`R8 renombró ${nombre} -> ${clases.get(nombre)}: falta o falla su regla keep`);
    }
    if (borradas.has(nombre)) problemas.push(`R8 eliminó ${nombre} (aparece en usage.txt)`);
  }

  for (const [origen, destino] of clases) {
    if (SINTETICA.test(origen)) continue;
    if (PAQUETES_SIN_RENOMBRAR.some((p) => origen.startsWith(p)) && origen !== destino) {
      problemas.push(`R8 renombró ${origen} -> ${destino}: sus reglas keep ya no la cubren`);
    }
  }

  const serializadores = [...clases].filter(([o]) => o.startsWith('app.trombito.rallycopilot.') && o.endsWith('$$serializer'));
  if (serializadores.length === 0) problemas.push('No sobrevive ningún $$serializer de la app');
  for (const [origen, destino] of serializadores) {
    if (origen !== destino) problemas.push(`R8 renombró el serializador ${origen} -> ${destino}`);
  }
  return problemas;
}

function main() {
  const directorio = process.argv[2] ?? DIRECTORIO_POR_DEFECTO;
  const mapping = join(directorio, 'mapping.txt');
  const usage = join(directorio, 'usage.txt');
  for (const archivo of [mapping, usage]) {
    if (!existsSync(archivo)) {
      console.error(`humo-release: falta ${archivo}; ejecuta antes ./gradlew :app:minifyReleaseWithR8`);
      process.exit(1);
    }
  }
  const problemas = comprobar(readFileSync(mapping, 'utf8'), readFileSync(usage, 'utf8'));
  if (problemas.length > 0) {
    for (const p of problemas) console.error(`humo-release: ${p}`);
    console.error(`humo-release: ${problemas.length} problema(s): revisa app/proguard-rules.pro`);
    process.exit(1);
  }
  console.log(`humo-release: bien (${CLASES_OBLIGATORIAS.length} clases obligatorias con su nombre, paquetes JNI/Vosk/JNA sin renombrar)`);
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) main();
