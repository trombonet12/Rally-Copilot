# Paquetes de datos de Mallorca: origen, pipeline y distribución

> Propósito: decidir cómo se generan y llegan al móvil, sin backend propio, las teselas vectoriales, el grafo de enrutado, la elevación y los límites de velocidad · Audiencia: agente principal y usuario · Actualizado: 2026-10-28 · Relacionado: docs/DECISIONES.md § Datos y distribución, ROADMAP.md § H1, H2, H3

## Contexto
- Sin backend propio ni cuentas; internet solo para descargar mapas y refrescar incidencias. Un solo usuario administra el proyecto; la app es gratuita.
- **Fuente OSM**: Geofabrik `islas-baleares-261027.osm.pbf`, 45 MB, publicada 2026-10-28 (histórico diario; 30 MB en 2022-01). Datos ODbL: atribuir "© OpenStreetMap contributors".
- **Teselas vectoriales**: Planetiler 0.10.2 (2026-03-29; hay compilado `latest-main` de 2026-10-17), Apache-2.0, genera `.pmtiles`/`.mbtiles` con `--area=...`; el README pide Java 25 en modo JAR (el lanzador de un solo archivo trae su JVM) y heap ≈ la mitad del PBF. El perfil de Protomaps basemaps (BSD-3) se construye con Maven y Java 21+; planeta z0–15. `pmtiles extract --bbox` permite además recortar de una compilación diaria remota por rangos HTTP (Protomaps desaconseja enlazar sus builds directamente y pide copiarlas a almacenamiento propio).
- **Grafo de enrutado**: teselas Valhalla (`valhalla_build_tiles`, misma versión que valhalla-mobile, hoy 3.9.1) o `.rd5` de BRouter (`E0_N35.rd5` = 16,9 MB, cubre mucho más que la isla). Tamaño de las teselas Valhalla de Mallorca: no medido, VERIFICAR en el spike.
- **Elevación**: MDT05 (5 m) de la 1ª cobertura PNOA-LiDAR, Cloud Optimized GeoTIFF, ETRS89 UTM, alturas ortométricas, unidad de descarga = hoja MTN50 (1525 ficheros en España; 0,6–85 MB por hoja en las visibles). Licencia "compatible con CC-BY 4.0" (Orden FOM/2807/2015), con atribución. MDT25 (25 m, 0,04–3,5 MB por hoja) es la alternativa pequeña; MDT02 (2 m, 2ª cobertura 2015–2020/21) existe pero **no pude confirmar su cobertura en Baleares ni su ficha** (la página del CNIG dio error): VERIFICAR. Descarga masiva: filtro por comunidad "Illes Balears" y "Añadir todos" a la cesta; si es automatizable desde CI, VERIFICAR. Alternativa: servicio WCS de MDT del IGN (registro INSPIRE `spaignwcs_mdt`), VERIFICAR.
- Cálculo propio del volumen de MDT05: 3640 km² / 25 m² = 145,6 M de celdas; en int16 (decímetros) ≈ 291 MB sin comprimir; a 10 m ≈ 73 MB; a 25 m ≈ 12 MB.
- **maxspeed en OSM Mallorca** (Overpass, 2026-10-28, bbox lat 39,26–39,97, lon 2,30–3,50, recuento de *ways*, no de longitud): autovía/motorway 463 de 486 (95 %); primary 1594 de 2849 (56 %); secondary 1162 de 2567 (45 %); tertiary 1184 de 3453 (34 %); unclassified 346 de 4393 (8 %); residential 2733 de 17 366 (16 %); trunk 403 con maxspeed (total no obtenido). Conclusión: cobertura buena en vías rápidas y floja en secundarias/locales → hace falta un valor por defecto por clase de vía (límites genéricos del Reglamento General de Circulación: VERIFICAR) y marcar el límite como "estimado".
- **Alojamiento**: GitHub Releases, cada asset < 2 GiB, hasta 1000 assets por release, sin límite de tamaño total ni de ancho de banda (docs de GitHub, 2026-10-28); jobs de Actions hasta 6 h. Cloudflare R2: 10 GB-mes, 1 M operaciones clase A, 10 M clase B y sin coste de salida en el nivel gratuito (VERIFICAR si exige tarjeta).

## Alternativas
| Alternativa | Pros | Contras | Licencia | Madurez / versión verificada |
|---|---|---|---|---|
| Hosting: GitHub Releases de repo público generado por Actions | Gratis, sin límite de ancho de banda declarado, versionado por etiqueta, reproducible, sin cuentas extra; URL estable `.../releases/latest/download/` | Repo (y datos) públicos; sin garantía contractual; soporte de rangos HTTP no verificado (no se necesita: se descarga entero) | Datos ODbL/CC BY | Documentado por GitHub |
| Hosting: Cloudflare R2 | Salida gratuita, rangos HTTP, dominio propio | Cuenta y posible tarjeta; otro punto de fallo; cuotas | Condiciones de Cloudflare | Precios consultados 2026-10-28 |
| Descarga directa de fuentes oficiales en el móvil (IGN, Geofabrik) | Sin hosting nuestro | no generaría teselas ni grafo (necesitan cómputo); MDT del CNIG sin descarga directa estable | CC BY 4.0 / ODbL | Solo viable para satélite PNOA (ver `mapas-y-teselas.md`) |
| Elevación: tabla compacta propia (MDT05 → HGT 1″ para Valhalla + perfil por nodo de vía) | `height`/`weighted_grade` de Valhalla (necesita directorio de teselas de elevación); sin DEM en runtime para notas | HGT 1″ pierde detalle (~24–31 m); Valhalla/valhalla-mobile con HGT: VERIFICAR | CC BY 4.0 (derivado) | Por probar |
| Elevación: ráster-dem en PMTiles (Terrarium) | Una sola infraestructura (PMTiles), sirve también para sombreado | Peso mayor; codificación a elegir | CC BY 4.0 | Por probar |

## Recomendación
Pipeline reproducible en un **repo público de datos** (`rally-copilot-data`) con GitHub Actions: (1) descargar el PBF de Geofabrik; (2) Planetiler + perfil Protomaps → `mallorca.pmtiles`; (3) `valhalla_build_tiles` con la versión fijada → `valhalla-mallorca.tar`; (4) elevación: recortar MDT05 de Baleares, remuestrear (HGT 1″ para Valhalla; perfil de altura por nodo de vía para las notas) y comprobar tamaño; (5) `manifest.json` con versión, fecha, tamaño, SHA-256, fuente y atribuciones; (6) publicar en una Release `datos-AAAA.MM` con assets < 2 GiB. La app lee `releases/latest/download/manifest.json`, descarga solo lo que cambió y verifica el hash.
**Frecuencia**: mensual (cron de Actions, primer domingo) + disparo manual; el MDT se regenera solo si cambia la fuente (anual o menos). Las incidencias DGT y los radares quedan fuera del paquete mensual (H5). El satélite PNOA se descarga aparte (ver `mapas-y-teselas.md`).

## Consecuencias
- Positivas: cero servidores; todo reproducible y versionado; las notas usan datos medidos (MDT05, `maxspeed`).
- Negativas / costes: dependencia de GitHub; repo público con ~100–300 MB por release (estimación sin medir, VERIFICAR) → política de retención (borrar releases antiguas); el MDT es un paso manual o frágil si el CNIG no admite automatización.
- Seguimiento: medir tamaños reales de PMTiles, teselas Valhalla y elevación; confirmar MDT02 en Baleares; validar que valhalla-mobile usa teselas de elevación HGT; revisar términos de uso de GitHub para distribuir binarios de datos de forma recurrente.

## ¿Afecta al producto?
Sí → QUESTIONS.md § Datos y actualizaciones: (1) ¿Aceptas un repo público de datos en GitHub (o prefieres R2)? (2) ¿Qué descarga inicial es aceptable (vectorial + grafo + elevación, estimado de decenas a pocos cientos de MB, más ~450 MB de satélite base)? (3) ¿Actualización mensual automática con aviso, o solo manual desde "Mapas offline"? (4) ¿Se muestra el límite "estimado" cuando no hay `maxspeed` en OSM?

## Fuentes
- https://download.geofabrik.de/europe/spain/ (islas-baleares-261027.osm.pbf, 45 MB; 2026-10-28)
- https://raw.githubusercontent.com/onthegomap/planetiler/main/README.md ; https://api.github.com/repos/onthegomap/planetiler/releases (2026-10-28)
- https://raw.githubusercontent.com/protomaps/basemaps/main/README.md ; https://docs.protomaps.com/basemaps/downloads ; https://docs.protomaps.com/pmtiles/cli (2026-10-28)
- https://centrodedescargas.cnig.es/CentroDescargas/modelo-digital-terreno-mdt05-primera-cobertura ; .../modelo-digital-terreno-mdt25-primera-cobertura (2026-10-28)
- https://inspire-geoportal.ec.europa.eu/srv/api/records/spaignMDT05 (resultado de búsqueda, no abierto: redirige; 2026-10-28)
- Consultas Overpass API (https://overpass-api.de/api/interpreter) de 2026-10-28, scripts no versionados
- https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases ; https://docs.github.com/en/actions/reference/limits ; https://developers.cloudflare.com/r2/pricing/ (2026-10-28)
- https://github.com/Rallista/valhalla-mobile (elevación: directorio de teselas en la config; 2026-10-28)
