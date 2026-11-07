// Tests de scripts/humo-release.mjs: node --test scripts/humo-release.test.mjs
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { CLASES_OBLIGATORIAS, clasesBorradas, clasesDelMapping, comprobar } from './humo-release.mjs';

const SERIALIZADOR = 'app.trombito.rallycopilot.motor.datos.ArchivoDatos$$serializer';

const mappingBueno = [
  ...CLASES_OBLIGATORIAS.map((c) => `${c} -> ${c}:`),
  '    int campo -> a',
  'org.vosk.android.SpeechService$RecognizerThread$$ExternalSyntheticLambda0 -> org.vosk.android.a:',
  'app.trombito.rallycopilot.Otra -> a.b:',
].join('\n');
const usageBueno = 'com.sun.jna.Pointer:\n    static void <clinit>()\ncom.k2fsa.sherpa.onnx.SpeakerKt\n';

test('lee las clases del mapping y salta los miembros', () => {
  const m = clasesDelMapping(mappingBueno);
  assert.equal(m.get('org.vosk.Model'), 'org.vosk.Model');
  assert.equal(m.get('app.trombito.rallycopilot.Otra'), 'a.b');
  assert.equal(m.has('    int campo'), false);
});

test('en usage solo cuentan las clases borradas enteras, no los miembros', () => {
  assert.deepEqual([...clasesBorradas(usageBueno)], ['com.k2fsa.sherpa.onnx.SpeakerKt']);
});

test('un release sano no da problemas', () => {
  assert.deepEqual(comprobar(mappingBueno, usageBueno), []);
});

test('detecta una clase renombrada, una ausente y una borrada', () => {
  const mapping = mappingBueno
    .replace('org.vosk.Model -> org.vosk.Model:', 'org.vosk.Model -> a.a:')
    .replace('com.sun.jna.Native -> com.sun.jna.Native:\n', '');
  const problemas = comprobar(mapping, `${usageBueno}org.vosk.Recognizer\n`);
  assert.ok(problemas.some((p) => p.includes('renombró org.vosk.Model')));
  assert.ok(problemas.some((p) => p.includes('Falta la clase com.sun.jna.Native')));
  assert.ok(problemas.some((p) => p.includes('eliminó org.vosk.Recognizer')));
});

test('detecta un serializador renombrado y un paquete JNI renombrado', () => {
  const mapping =
    mappingBueno.replace(`${SERIALIZADOR} -> ${SERIALIZADOR}:`, () => `${SERIALIZADOR} -> b.c:`) +
    '\ncom.k2fsa.sherpa.onnx.Nueva -> c.d:';
  const problemas = comprobar(mapping, usageBueno);
  assert.ok(problemas.some((p) => p.includes('serializador app.trombito')));
  assert.ok(problemas.some((p) => p.includes('com.k2fsa.sherpa.onnx.Nueva -> c.d')));
});

test('un mapping vacío falla', () => {
  assert.ok(comprobar('', '').length > 0);
});
