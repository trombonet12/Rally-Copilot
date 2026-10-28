# Librería de mapas, satélite PNOA y formato de teselas

> Propósito: elegir librería de mapas (vectorial OSM + ráster satélite offline), formato de almacenamiento y estrategia de descarga del satélite · Audiencia: agente principal y usuario · Actualizado: 2026-10-28 · Relacionado: docs/DECISIONES.md § Librería de mapas y formato de teselas, ROADMAP.md § H1

## Contexto
- Requisitos (prompt-inicial §4.2): vista vectorial (OSM) y satélite (PNOA) de Mallorca, offline; los mapas se descargan desde la app, no van en el APK; sin GMS; atribuciones respetadas.
- **Satélite PNOA (IGN)**: WMTS `https://www.ign.es/wmts/pnoa-ma` (capa `OI.OrthoimageCoverage`, matriz `GoogleMapsCompatible` EPSG:3857, 256x256, JPEG/PNG; el resumen dice teselas pregeneradas hasta z19) y TMS/XYZ `https://tms-pnoa-ma.idee.es/1.0.0/pnoa-ma/{z}/{x}/{-y}.jpeg` (eje y invertido). Comprobado 2026-10-28: 16 teselas de tierra (Palma, Inca, Manacor, Sóller...) respondieron en z10, 13, 16, 17, 18 y 19, con imagen real a z19. Capacidades: solo KVP/GET; no he visto límites de peticiones.
- **Licencia**: CC BY 4.0 (`AccessConstraints` = "CC BY 4.0 scne.es"; licencia IGN, Orden FOM/2807/2015). Fórmula: `<producto> <fecha> CC-BY 4.0 <productores>`, visible "a pie de mapa"; abreviada `CC-BY 4.0 scne.es <fecha>`; para obra derivada se antepone "Obra derivada de" (ejemplo de la licencia: "Obra derivada de PNOA 2010-2013 CC-BY scne.es"). La licencia no dice nada sobre descarga masiva ni caché local: VERIFICAR con consulta@cnig.es antes de publicar.
- **Tamaño medido** (JPEG medio sobre 16 teselas de tierra por nivel, 2026-10-28; sesgo hacia núcleos urbanos): z10 11,4 KB, z13 12,6, z16 19,2, z17 19,4, z18 18,4, z19 14,4.
- **Cálculo propio** (3640 km², lado de tesela a lat 39,6° = 30 878 km/2^z; margen de costa x1,15, supuesto): z16 ≈ 18,9 k teselas ≈ 354 MB; z15 ≈ 4,7 k ≈ 76 MB; z≤14 ≈ 1,2 k ≈ 20 MB. **Total z0–16 ≈ 25 k teselas ≈ 450 MB** (rango razonable 350–600 MB; el ~600 MB del prompt es el techo, no un error grave). Corredor de 300 m a cada lado (600 m): 100 km de ruta = 60 km² → z17 ≈ 1,1 k teselas ≈ 21 MB; z18 ≈ 4,3 k ≈ 78 MB (**≈ 100 MB por 100 km**). Zona manual: z18 ≈ 1,3 MB/km² y z19 ≈ 4,1 MB/km² (**≈ 5,4 MB/km²**; 25 km² ≈ 135 MB). Isla entera: z17 ≈ 1,4 GB, z18 ≈ 5,4 GB, z19 ≈ 17 GB → inviable, justifica la estrategia por corredor/zona.
- **MapLibre Native Android** 13.7.1 (2026-10-15), BSD-2; minSdk 23 (changelog 12.0.0). PMTiles desde 11.8.0 según el changelog (la página de ejemplos dice 11.7.0): `pmtiles://file:///ruta/absoluta.pmtiles`, `pmtiles://asset://x.pmtiles` (13.7.1), también ráster y `raster-dem`. La doc de ejemplos dice que PMTiles no admite descargas offline ni caché, pero el changelog 13.3.0 añade "ambient cache for PMTiles" (contradicción: VERIFICAR). Fuente de archivo MBTiles desde 9.3.0 (`MBTilesFileSource`; esquema `mbtiles://` y soporte ráster: VERIFICAR). `maplibre-compose` 0.20.0 (2026-10-10) para Compose.
- **osmdroid** archivado (Apache-2.0; último push 2024-11-20). **Mapsforge** 0.30.0 (2026-08-20): mapas vectoriales propios `.map`, activo; licencia LGPL (VERIFICAR).
- **Estilos y datos**: Protomaps basemaps (tiles generados con Planetiler): código BSD-3, diseño CC0, teselas ODbL (atribuir OSM), fuentes Noto Sans con OFL y sprites de origen MIT; planeta z0–15, sobre-zoom después; glyphs/sprites descargables para uso offline. OpenMapTiles: código BSD-3, diseño CC BY 4.0 → exige mostrar "© OpenMapTiles" además de "© OpenStreetMap contributors".

## Alternativas
| Alternativa | Pros | Contras | Licencia | Madurez / versión verificada |
|---|---|---|---|---|
| MapLibre Native Android (+ maplibre-compose) | Vectorial y ráster, estilos, PMTiles local, rotación/inclinación para conducción, sin GMS; Ferrostar tiene UI MapLibre | Dudas abiertas en cachés/MBTiles (arriba); API de Compose joven | BSD-2 | 13.7.1 (2026-10-15); compose 0.20.0 |
| osmdroid | Sencillo, ráster y MBTiles | Archivado; sin vectorial moderno | Apache-2.0 | Archivado 2024-11 |
| Mapsforge | Vectorial offline muy compacto | Estilo propio (no OpenMapTiles/Protomaps), poca inclinación/rotación 3D, datos `.map` aparte | LGPL (VERIFICAR) | 0.30.0 (2026-08-20) |
| Formato PMTiles | Un solo archivo, lectura directa en MapLibre, `pmtiles extract` por bbox, ideal para hosting estático | Solo lectura: para añadir teselas hay que reconstruirlo | BSD-3 (CLI go-pmtiles 1.31.2, 2026-07-22) | Soportado en MapLibre Android |
| Formato MBTiles (SQLite) | Escribible por la app (corredor, zona manual), estándar | Soporte `mbtiles://` en MapLibre a confirmar; si no, servidor HTTP local en 127.0.0.1 | Estándar abierto | Fuente de archivo desde 9.3.0 |
| OfflineManager / caché de MapLibre | Integrado | Orientado a estilo+región, no a 25 k teselas ni corredor; límites de teselas (VERIFICAR); base SQLite opaca | BSD-2 | Cambios 2024–26 en changelog |

## Recomendación
**MapLibre Native Android** con estilo Protomaps (tema oscuro por defecto). **Vectorial**: un `mallorca.pmtiles` generado en CI (ver `datos-mallorca.md`), cargado con `pmtiles://file://`. **Satélite en dos capas ráster**: (1) base z0–16 como PMTiles (hospedado por nosotros, o construido en el móvil si se decide descargar de IGN) con `maxzoom: 16`; (2) capa de detalle z17–19 en un MBTiles escribible por la app, **mínimo z17**, servida por `mbtiles://` si el spike lo confirma o por un servidor HTTP local si no. Descarga propia con cola, reintentos, límite de espacio, solo WiFi (opcional) y User-Agent identificable, no con `OfflineManager`. Atribución visible en el mapa: "© OpenStreetMap contributors" y, en satélite, "CC-BY 4.0 scne.es" (texto exacto a confirmar con el CNIG; "Obra derivada de" si se recomprime).

## Consecuencias
- Positivas: cero dependencia de GMS; actualizar el vectorial es sustituir un archivo; el corredor crece por incrementos sin reescribir el base.
- Negativas / costes: dos fuentes ráster y dos formatos; hay que escribir un descargador y un gestor de caché (LRU por límite de espacio); sobre-zoom visible en z17+ sin corredor.
- Seguimiento: spike con (a) `pmtiles://file://` de un extracto, (b) `mbtiles://` ráster y vector, (c) fuentes `glyphs` en `asset://`/`file://`, (d) tamaño real del PMTiles de Mallorca (no medido), (e) cobertura PNOA real a z19 en toda la isla, (f) consulta al CNIG sobre descarga masiva/caché.

## ¿Afecta al producto?
Sí → QUESTIONS.md § Satélite: ¿la base z≤16 (~450 MB) la descarga el móvil directamente del IGN (sin hosting nuestro, ~25 k peticiones) o la servimos empaquetada desde nuestro hosting? ¿Qué tope de espacio por defecto (propuesta 2 GB) y "solo WiFi" activado por defecto?

## Fuentes
- https://www.ign.es/wmts/pnoa-ma?request=GetCapabilities&service=WMTS (2026-10-28)
- https://www.ign.es/web/ign/portal/ide-area-nodo-ide-ign (URL TMS, 2026-10-28)
- https://www.ign.es/resources/licencia/Condiciones_licenciaUso_IGN.pdf (texto leído, 2026-10-28)
- Muestreo propio de teselas en https://tms-pnoa-ma.idee.es/1.0.0/pnoa-ma/{z}/{x}/{-y}.jpeg (2026-10-28, 96 peticiones)
- https://maplibre.org/maplibre-native/android/examples/data/PMTiles/ ; https://raw.githubusercontent.com/maplibre/maplibre-native/main/platform/android/CHANGELOG.md ; https://api.github.com/repos/maplibre/maplibre-native (BSD-2) ; https://api.github.com/repos/maplibre/maplibre-compose/releases (2026-10-28)
- https://api.github.com/repos/osmdroid/osmdroid ; https://api.github.com/repos/mapsforge/mapsforge/releases (2026-10-28)
- https://docs.protomaps.com/basemaps/downloads ; https://docs.protomaps.com/basemaps/maplibre ; https://docs.protomaps.com/pmtiles/cli ; https://github.com/protomaps/basemaps-assets ; https://raw.githubusercontent.com/protomaps/basemaps/main/LICENSE.md (2026-10-28)
- https://github.com/openmaptiles/openmaptiles/blob/master/LICENSE.md ; https://raw.githubusercontent.com/onthegomap/planetiler/main/README.md (2026-10-28)
