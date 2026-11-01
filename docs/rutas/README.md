# Rutas de prueba

> Propósito: trazas reales de Mallorca para simular el GPS en el emulador y como fixtures del motor · Audiencia: agentes y Joan · Actualizado: 2026-10-31 · Relacionado: skill `gps-replay`

| Archivo | Vía | Longitud | Origen | Tiempos / elevación |
|---|---|---|---|---|
| `ma-2141-sa-calobra.gpx` | Ma-2141, del cruce con la Ma-10 (Coll dels Reis) al puerto de Sa Calobra, con el Nus de sa Corbata | 12,8 km, 1116 puntos | OpenStreetMap vía Overpass API (2026-10-28), ways `ref=Ma-2141` encadenadas | No: reproducir con `--kmh` |
| `ma-10-soller-lluc.gpx` | Ma-10 de Sóller a Lluc por el Coll de Puig Major, Cúber y Gorg Blau | 33,7 km, 1762 puntos | `vias-mallorca.tsv.gz` de `datos-2026.10.31-6` (OSM), camino más corto por la Ma-10 | No: reproducir con `--kmh` |
| `ma-13-santa-maria-palma.gpx` | Ma-13 de Santa Maria del Camí hacia Palma, pasando por el radar fijo de la DGT (120 km/h, sentido Palma) | 12,7 km, 364 puntos | `vias-mallorca.tsv.gz` de `datos-2026.11.01-9` (OSM), con `scripts/traza-desde-vias.py` (respeta los sentidos únicos) | No: reproducir con `--kmh` |

Trazas nuevas desde las vías de OSM: `python -I scripts/traza-desde-vias.py` (ver su cabecera).

Uso: `node scripts/gps-replay.mjs docs/rutas/ma-2141-sa-calobra.gpx --kmh 50 [--desde 4km]`.

Atribución: © colaboradores de OpenStreetMap, datos bajo licencia ODbL 1.0
(<https://www.openstreetmap.org/copyright>). Cada traza nueva se añade a esta tabla con su origen y licencia.
