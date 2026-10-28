// Tests de scripts/gps-replay.mjs: node --test scripts/gps-replay.test.mjs
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { argumentosGeoFix, distancia, indiceDesde, parsear, planificar } from './gps-replay.mjs';

const GPX = `<?xml version="1.0"?><gpx version="1.1"><trk><trkseg>
<trkpt lat="39.8000" lon="2.8000"><ele>100</ele><time>2026-10-28T10:00:00Z</time></trkpt>
<trkpt lat="39.8009" lon="2.8000"><ele>110</ele><time>2026-10-28T10:00:10Z</time></trkpt>
<trkpt lat="39.8018" lon="2.8000"><ele>120</ele><time>2026-10-28T10:00:20Z</time></trkpt>
</trkseg></trk></gpx>`;

const KML = `<?xml version="1.0"?><kml><Placemark><LineString><coordinates>
2.8,39.8,100 2.8,39.8009,110 2.8,39.8018,120
</coordinates></LineString></Placemark></kml>`;

test('parsea un GPX con elevación y tiempos', () => {
  const p = parsear(GPX);
  assert.equal(p.length, 3);
  assert.deepEqual([p[0].lat, p[0].lon, p[0].ele], [39.8, 2.8, 100]);
  assert.equal(p[1].t - p[0].t, 10_000);
});

test('parsea un KML LineString (lon,lat,alt) sin tiempos', () => {
  const p = parsear(KML);
  assert.equal(p.length, 3);
  assert.deepEqual([p[2].lat, p[2].lon, p[2].ele], [39.8018, 2.8, 120]);
  assert.equal(p[0].t, undefined);
});

test('rechaza formatos desconocidos y trazas de un punto', () => {
  assert.throws(() => parsear('{}'), /Formato no reconocido/);
  assert.throws(() => parsear('<gpx><trkpt lat="39" lon="2"/></gpx>'), /al menos 2 puntos/);
});

test('con tiempos respeta la cadencia y el factor de velocidad', () => {
  const plan = planificar(parsear(GPX), { factor: 2 });
  assert.deepEqual(
    plan.map((f) => f.esperaMs),
    [0, 5000, 5000],
  );
  // 100 m en 10 s = 10 m/s, que con x2 se envía como 20 m/s.
  assert.ok(Math.abs(plan[1].ms - 2 * (distancia(plan[0], plan[1]) / 10)) < 1e-9);
});

test('sin tiempos exige --kmh', () => {
  assert.throws(() => planificar(parsear(KML)), /--kmh/);
});

test('con --kmh envía una posición por segundo a velocidad constante', () => {
  const puntos = parsear(KML);
  const total = distancia(puntos[0], puntos[1]) + distancia(puntos[1], puntos[2]);
  const plan = planificar(puntos, { kmh: 36 }); // 10 m/s
  // Salida + un envío cada 10 m + el final si queda a más de medio metro del último envío.
  assert.equal(plan.length, 1 + Math.floor(total / 10) + (total % 10 > 0.5 ? 1 : 0));
  for (let i = 1; i < plan.length - 1; i++) {
    assert.ok(Math.abs(distancia(plan[i - 1], plan[i]) - 10) < 0.01, `paso ${i}`);
    assert.equal(plan[i].esperaMs, 1000);
  }
  assert.ok(distancia(plan.at(-1), puntos.at(-1)) < 0.5);
});

test('--desde acepta índice o kilómetros', () => {
  const p = parsear(KML);
  assert.equal(indiceDesde(p, undefined), 0);
  assert.equal(indiceDesde(p, '1'), 1);
  assert.equal(indiceDesde(p, '99'), 2);
  assert.equal(indiceDesde(p, '0.15km'), 2);
});

test('geo fix lleva longitud primero y la velocidad en nudos', () => {
  const args = argumentosGeoFix({ lat: 39.8, lon: 2.8, ele: 100, ms: 10 });
  assert.deepEqual(args.slice(0, 6), ['emu', 'geo', 'fix', '2.8000000', '39.8000000', '100.0']);
  assert.equal(args[7], '19.44');
});
