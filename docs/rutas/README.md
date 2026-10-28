# Rutas de prueba

> Propósito: trazas reales de Mallorca para simular el GPS en el emulador y como fixtures del motor · Audiencia: agentes y Joan · Actualizado: 2026-10-28 · Relacionado: skill `gps-replay`

| Archivo | Vía | Longitud | Origen | Tiempos / elevación |
|---|---|---|---|---|
| `ma-2141-sa-calobra.gpx` | Ma-2141, del cruce con la Ma-10 (Coll dels Reis) al puerto de Sa Calobra, con el Nus de sa Corbata | 12,8 km, 1116 puntos | OpenStreetMap vía Overpass API (2026-10-28), ways `ref=Ma-2141` encadenadas | No: reproducir con `--kmh` |

Uso: `node scripts/gps-replay.mjs docs/rutas/ma-2141-sa-calobra.gpx --kmh 50 [--desde 4km]`.

Atribución: © colaboradores de OpenStreetMap, datos bajo licencia ODbL 1.0
(<https://www.openstreetmap.org/copyright>). Cada traza nueva se añade a esta tabla con su origen y licencia.
