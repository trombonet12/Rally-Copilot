# Decisiones

> Propósito: fuente única de decisiones técnicas y versiones fijadas, con su porqué · Audiencia: Joan y agentes · Actualizado: 2026-10-28 · Relacionado: [HERENCIA.md](HERENCIA.md), [investigacion/](investigacion/), [../QUESTIONS.md](../QUESTIONS.md)

Formato: `- AAAA-MM-DD · tema · decisión · porqué`. Las entradas posteriores mandan sobre las anteriores (se indica
«sustituye a…»). `VERIFICAR` = dato no confirmado. Las decisiones que afectan al producto **no** se toman aquí: van a
[QUESTIONS.md](../QUESTIONS.md) con una recomendación, y se reflejan aquí cuando Joan responde. Se mantiene con la
skill `registrar-decision`.

## Decisiones del usuario (2026-10-28)
- 2026-10-28 · marca · Logo = mascota Trombito con casco de rally · elegido por Joan entre «mascota + casco» y «solo casco».
- 2026-10-28 · release · Como Sous Chef: un push a `main` que cambia `version.properties` publica (GitHub Release + Play interna → prueba cerrada); los parches, por `workflow_dispatch` con `publicar` · elegido por Joan.
- 2026-10-28 · identidad · `applicationId = app.trombito.rallycopilot` (debug `.dev`) · patrón de los hermanos; permanente en Play.
- 2026-10-28 · git · Push solo con permiso explícito, cada vez (`permissions.ask`); force-push prohibido · corrección de Joan.
- 2026-10-28 · delegación · Subagentes simultáneos ≤ 1 punto (opus 1, sonnet 0,5, haiku 0,25) · regla de Joan.
- 2026-10-28 · memoria · `docs/MEMORIA.md` importada desde CLAUDE.md + skill `recordar` · petición de Joan.

## Respuestas de Joan a QUESTIONS.md (2026-10-28)
Mandan sobre las recomendaciones de las investigaciones cuando difieren (marcado «≠ recomendación»).
- 2026-10-28 · datos · Repo **público** `trombonet12/rally-copilot-data` con GitHub Actions mensual y Releases.
- 2026-10-28 · datos · Actualización: al abrir la app comprueba si hay datos nuevos, los descarga con WiFi y avisa al terminar.
- 2026-10-28 · satélite · La base z0–16 va empaquetada (PMTiles) en el repo de datos; el detalle z17–19 se descarga del IGN.
- 2026-10-28 · caché · Por defecto, límite de 2 GB y solo WiFi (configurables).
- 2026-10-28 · límite · Si OSM no tiene `maxspeed`, **no se muestra límite** (≠ recomendación de mostrar uno estimado).
- 2026-10-28 · búsqueda · Lugares, calles y POIs de OSM **+ coordenadas y enlaces** de OSM/Google Maps pegados.
- 2026-10-28 · rutas · «Más rápida» por defecto + opción «Sinuosa» (penaliza autovías y prefiere carreteras con curvas).
- 2026-10-28 · giros · Indicaciones por voz con frases propias en estilo copiloto («cruce, derecha, Ma-10»); el texto de Valhalla solo como respaldo.
- 2026-10-28 · grabación · El tramo y la grabación empiezan al pulsar Conducir/Empezar, con notificación fija; sin ubicación en segundo plano.
- 2026-10-28 · tramos · Fin de tramo: pulsar Terminar o llegar al destino; los tramos < 1 km se descartan. **Sin** cierre automático por estar parado.
- 2026-10-28 · pruebas · Móvil de Joan: LineageOS **sin GMS**. Además del emulador con Google APIs, probar con una imagen sin Google APIs.
- 2026-10-28 · calibración · Las notas se calibran con **la velocidad real de Joan** (≠ recomendación: límite de la vía). Mientras no hay datos suyos se usa el límite de la vía; las pasadas ajustan un perfil global y otro por tramo.
- 2026-10-28 · notas · Orden dirección-grado («derecha cuatro»); se cantan **todos los grados, también el 6** (≠ recomendación); horquilla sin grado; distancia después de la nota.
- 2026-10-28 · notas · Avisos de precaución **generosos** (≠ recomendación): también en curvas cerradas sin visibilidad y en cambios de grado bruscos.
- 2026-10-28 · voz · Dos voces masculinas dentro del APK, con clips pregenerados para lo común y TTS para lo imprevisto.
- 2026-10-28 · licencia · La app se publica como **GPL-3.0** (`LICENSE`) · espeak-ng (fonemizador de Piper/sherpa-onnx) es GPL-3.0; así no hay conflicto.
- 2026-10-28 · alertas · Zonas probables de radar móvil en pantalla **y por voz** (≠ recomendación).
- 2026-10-28 · alertas · Se avisa también de incidencias cercanas fuera de la ruta.
- 2026-10-28 · alertas · **Sin** botón de refresco manual: solo el automático al abrir la app y al iniciar ruta.
- 2026-10-28 · escucha · Edición por voz y modo reconocimiento con botón (en pantalla o del volante por Bluetooth) **y** palabra de activación opcional en Ajustes.
- 2026-10-28 · voz de Moya · Solo material existente (sin sesión de grabación); el pipeline de H11 se diseña para audio con ruido.
- 2026-10-28 · repo · `trombonet12/Rally-Copilot` pasa a **público** (coherente con GPL-3.0; Obtainium sin token).
- 2026-10-28 · visual · Se mantiene la dirección de H0 (oscuro carbón, latón, rojo, rótulos en cursiva).
- 2026-10-28 · marca · Casco más cuadrado (cúpula más baja y ancha) · el anterior se veía alargado en vertical.
- 2026-10-28 · atribución · **Sin atribución en la pantalla del mapa**; OSM (ODbL), PNOA (CC BY 4.0), Protomaps, Noto y MapLibre solo en Ajustes > Acerca de · decisión de Joan tras avisarle de que las licencias piden atribuir donde se muestra el mapa (las guías de la OSMF admiten como mínimo un botón ⓘ); riesgo aceptado por él. Sustituye a la atribución visible del 2026-10-28 (§ Mapas).
- 2026-10-28 · mapa (H1) · «Conducir» abre el mapa en modo libre hasta H2; la primera descarga de mapas la inicia el usuario (las actualizaciones son automáticas con WiFi) · respuestas de Joan.
- 2026-10-28 · mapa (H1) · Orientación: rumbo arriba por defecto con botón para alternar a norte arriba; se mantiene el rótulo de la vía bajo la velocidad; al desactivar «Solo con WiFi» se confirma con un diálogo por el gasto de datos · respuestas de Joan (pendiente de implementar).
- 2026-10-28 · H2 · Un único H2 con navegación y los cambios del mapa; búsqueda en una barra «¿A dónde?» sobre el mapa; durante la navegación se ven hora de llegada, tiempo y km restantes y la maniobra siguiente; pantalla siempre encendida con la app delante · respuestas de Joan.
- 2026-10-28 · mapa (H2) · Menú de capas con iconos (sin textos) con base Mapa/Satélite y capa de relieve + curvas de nivel; zoom automático por velocidad con ajuste en Ajustes y pausa al pellizcar; icono de posición sin círculo, elegible entre flecha simple, flecha WRC, coche de rally y casco de Trombito; números de carretera en placas con los colores oficiales de su red; cartelería de maniobras estilo juegos de WRC (el agente elige variante y Joan decide al cierre) · peticiones de Joan al ver H1.
- 2026-10-28 · distribución · **Solo GitHub Release + Obtainium**; Play queda pospuesto (el job solo corre con `PLAY_HABILITADO=true`) · decisión de Joan.
- 2026-10-28 · firma · Keystore **nueva y propia** de Rally Copilot en `~/.rallycopilot/`; nunca se reutiliza la de otro proyecto · decisión de Joan.

## Base técnica (H0)
- 2026-10-28 · stack · Mismo stack y versiones que Sous Chef (ver «Versiones fijadas») · línea común entre proyectos (pedido explícito).
- 2026-10-28 · módulos · `:app` (Android, capas `domain/` `data/` `di/` `ui/`) + `:motor` (Kotlin/JVM puro) · la lógica crítica (notas, cronometraje, deltas, detección de tramos, parseo) se prueba en la JVM sin Android, como `:receta` en Sous Chef.
- 2026-10-28 · dependencias · Solo las usadas: Room, Coil y el resto entran con `dependency-add` en el hito que las use · sin código ni dependencias muertas.
- 2026-10-28 · navegación · Rutas type-safe (`@Serializable`) de Navigation Compose · evita las rutas de texto de Sous Chef.
- 2026-10-28 · tests · JVM: JUnit 4, coroutines-test, Robolectric + `BancoCompose`/`LimpiezaCompose`; sin Turbine, MockK ni `ui-test`; instrumentados solo para el E2E de H12 · práctica real y rápida de Sous Chef.
- 2026-10-28 · arquitectura · `DomainDependencyRuleTest`: `domain/` sin Android ni DI; `ui` no importa `data` ni al revés (lista blanca vacía) · fronteras vigiladas por test.
- 2026-10-28 · tema · Oscuro por defecto con independencia del sistema; claro opcional en Ajustes (H10). Latón `#E5AB46`/`#8C5A04`, carbón `#0E0F12`, rojo de marca `#D8232A` decorativo; `ColoresRally` (delta `#3DDC84`/`#FF6B5E`, aviso `#FFC94A`) con contraste AA vigilado por `ContrastTest` · estética de retransmisión WRC pedida y coherencia con la marca.
- 2026-10-28 · tipografía · Letra del sistema; titulares en negrita cursiva con tracking negativo · como Sous Chef (sin fuentes empaquetadas) con aire de rótulo de rally.
- 2026-10-28 · gate · Se añade `:app:minifyReleaseWithR8` al gate local y de CI · en Sous Chef los fallos de R8 solo aparecían al publicar.
- 2026-10-28 · CI · Sin la comprobación «manifest de release sin INTERNET» de Sous Chef · Rally Copilot necesita red para mapas e incidencias.
- 2026-10-28 · CI · Sin `inAppUpdatePriority` (no se fuerza la actualización desde Play) · no hay decisión de producto equivalente a la de Sous Chef.
- 2026-10-28 · iconos · Icono adaptativo en `mipmap-anydpi-v26` aunque minSdk sea 26 · con `mipmap-anydpi` aapt2 no resuelve `@mipmap/ic_launcher` en release (probado); el aviso `ObsoleteSdkInt` de lint se acepta.
- 2026-10-28 · hooks · `post-edit-lint` valida también JSON · útil para los datos que vendrán (glosario, configuraciones).
- 2026-10-28 · dependencias · PRs #1 (actions) y #2 (Gradle 9.8.2, BOM 2026.09.00, Kotlin 2.4.21, Navigation 2.10.2, serialization 1.12.0) fusionados con el gate en verde; Gradle 9.8.2 y serialization 1.12.0 tenían 9 días · excepción a la regla de ≥ 2 semanas autorizada por Joan.
- 2026-10-28 · pruebas · GPS simulado con `scripts/gps-replay.mjs` (`adb emu geo fix` a ~1 Hz, velocidad en nudos, `--kmh`, `--velocidad`, `--desde`) y trazas OSM reales en `docs/rutas/` · requisito de probar con rutas reales de Mallorca; verificado en el emulador (`gps` en la Ma-2141).

## Versiones fijadas
| Componente | Versión | Nota |
|---|---|---|
| Gradle | 9.8.2 | `distributionSha256Sum` fijado (Dependabot #2, 2026-10-28) |
| AGP | 9.4.1 | Kotlin integrado (sin plugin `kotlin-android`) |
| Kotlin | 2.4.21 | KSP 2.3.12 |
| JDK | 21 (toolchain y daemon) | bytecode JVM 17 |
| compileSdk / targetSdk / minSdk | 37 / 36 / 26 | igual que Sous Chef |
| Compose BOM | 2026.09.00 | Material 3 |
| Hilt | 2.60.1 | hilt-navigation-compose 1.4.0 |
| Navigation Compose | 2.10.2 | rutas type-safe |
| Lifecycle / Activity | 2.10.0 / 1.11.0 | |
| core-splashscreen | 1.2.0 | |
| kotlinx.serialization / coroutines | 1.12.0 / 1.11.0 | |
| ktlint (plugin / motor) | 14.2.0 / 1.8.0 | |
| detekt | 1.23.8 | `config/detekt/detekt.yml` |
| JUnit / Robolectric | 4.13.2 / 4.17 | androidx.test core 1.7.0, ext-junit 1.3.0 |

## Investigaciones previas (§8)
Resumen y recomendación de cada investigación. El detalle (alternativas, licencias, fuentes) está en
[investigacion/](investigacion/). Lo que afecta al producto está en [QUESTIONS.md](../QUESTIONS.md) y **no** se
considera decidido hasta que Joan responda.

### Enrutado offline
- 2026-10-28 · enrutado · **Recomendado: Valhalla vía `valhalla-mobile` 0.6.4 (MIT, Valhalla 3.9.1)** para rutas, recálculo, instrucciones en es-ES, map matching (`trace_route`/`trace_attributes`) y atributos de vía. **Ferrostar 0.59.0 (BSD-3)** solo como máquina de estados giro a giro, condicionado a un spike de 1–2 días en H2; si no encaja, seguimiento propio mínimo. Plan B: BRouter. Descartados: GraphHopper (sin soporte Android oficial desde la 2.0), OSRM (no apto para móvil), OsmAnd (GPL, VERIFICAR) · única opción con offline real, recálculo, español y map matching empaquetados para Android con licencia permisiva. Detalle: [investigacion/enrutado-offline.md](investigacion/enrutado-offline.md).

### Mapas y teselas
- 2026-10-28 · mapas · **Recomendado: MapLibre Native Android 13.7.1 (BSD-2)** con estilo Protomaps en oscuro. Vectorial: `mallorca.pmtiles` local (`pmtiles://file://`). Satélite PNOA en dos capas: base z0–16 en PMTiles + detalle z17–19 en un MBTiles que escribe la app; descargador propio (cola, reintentos, límite de espacio, solo WiFi), no `OfflineManager` · vectorial y ráster offline en una sola librería sin GMS; PMTiles es un archivo único de solo lectura, MBTiles admite escritura incremental. Detalle: [investigacion/mapas-y-teselas.md](investigacion/mapas-y-teselas.md).
- 2026-10-28 · satélite · Estimación medida con teselas PNOA reales: **≈25 000 teselas y ≈450 MB para z0–16** de Mallorca (el «~600 MB» del prompt es un techo razonable); corredor z17–18 ≈100 MB por cada 100 km; zona manual z18–19 ≈5,4 MB/km² · dimensiona el límite de espacio por defecto.
- 2026-10-28 · atribución · En el mapa: «© OpenStreetMap contributors» y, en satélite, «CC-BY 4.0 scne.es» («Obra derivada de…» si se recomprime). VERIFICAR texto y condiciones de descarga masiva con el CNIG (consulta@cnig.es).
- 2026-10-28 · spike (H1) · Resultado en el emulador: `pmtiles://file://` funciona para vectorial y ráster sin red; **`mbtiles://` funciona solo con ruta absoluta** (`mbtiles:///data/…/x.mbtiles`; con `file://` da «MBTilesFileSource only supports absolute path urls»), verificado con PNOA z17 de Palma · la capa de detalle z17–19 de H9 irá en MBTiles escribible sin servidor HTTP local. Glyphs y sprites con `file://` funcionan. Tamaños reales: vectorial 32,6 MB, vías 2,2 MB (33 327 vías, 8 576 con `maxspeed`), satélite z0–16 364 MB (37 631 teselas; 12 154 de mar sin cobertura).
- 2026-10-28 · CI datos · El `GITHUB_TOKEN` dio 403 al crear la release al final de un job de 1 h 12 min (en un job corto funciona): se publica desde un job aparte con token nuevo · VERIFICAR la causa exacta (caducidad del token de instalación).
- 2026-10-28 · mapas (H1) · **MapLibre Android 13.6.1** (2026-09-08; la 13.7.1 tiene < 2 semanas) envuelto en `AndroidView`, **sin `maplibre-compose`** · API estable y una dependencia menos; el mapa es una vista, el resto de la UI es Compose.
- 2026-10-28 · mapas (H1) · Estilos MapLibre (vectorial oscuro y satélite híbrido = PNOA + etiquetas de Protomaps), fuentes Noto Sans (rangos latinos) y sprites van **en la release de datos** (`estilo-mapa.zip`, marcador `{{DIR}}` que la app sustituye por su carpeta local), no en el APK · estilo y teselas siempre del mismo esquema y se actualizan sin publicar app. Protomaps basemaps fijado al commit `42ffaaa` (Tiles 4.15.2) y `@protomaps/basemaps` 5.7.2; basemaps-assets `028c18f`.
- 2026-10-28 · datos (H1) · Todo se genera en GitHub Actions del repo de datos (Planetiler/Protomaps, pyosmium 4.3.1, go-pmtiles 1.31.2), no en el PC · la política del modo desatendido no permite ejecutar en local código de terceros; además así es reproducible.
- 2026-10-28 · satélite (H1) · PNOA z0–16 del TMS del IGN dentro del bbox 2.30,39.26,3.50,39.97 (≈ 49 800 teselas), 4 peticiones simultáneas, User-Agent identificable, JPEG sin recomprimir; las teselas de mar (PNG transparente de ~660 B) no se guardan. Se regenera solo con `satelite=true`; si no, se reutiliza el de la release anterior · cortesía con el IGN y el satélite cambia cada pocos años.
- 2026-10-28 · límites (H1) · Archivo propio `vias-mallorca.tsv.gz` (vías circulables, sin `service` ni `track`, con `maxspeed[:forward|:backward]`, `oneway`, `ref`, `name`, coordenadas en 1e-6°) leído por `:motor` (`LectorVias`, `IndiceVias` en rejilla, `SeguidorVia` con histéresis) · el esquema Protomaps no lleva `maxspeed` y consultar lo renderizado es frágil; el índice se reutiliza en H2/H3.

### Implementación de H1 (2026-10-28)
- 2026-10-28 · vías · `IndiceVias`: rejilla de 100 m en proyección local, candidatas a ≤ 25 m, puntuación = distancia + 0,25 m por grado de diferencia axial de rumbo (tope 22,5 m); `SeguidorVia` mantiene la vía salvo que otra puntúe ≥ 5 m mejor · evita saltos en cruces y paralelas sin perder los giros.
- 2026-10-28 · límites · `LimiteVelocidad` solo acepta números («50», «50 km/h», «30 mph» → 48) en 5–150 km/h; valores simbólicos (`ES:urban`…), listas y vacíos → sin límite; `:forward/:backward` según el sentido de la marcha y, si faltan, `maxspeed` · decisión de Joan: sin dato no se muestra límite.
- 2026-10-28 · velocidad · `EstimadorVelocidad`: velocidad del GNSS si viene; si no, por posición con dt 0,5–5 s; descarta saltos > 70 m/s; caduca a los 5 s · el GPS de Android a veces no trae `speed`.
- 2026-10-28 · descargas · `HttpURLConnection` con `Range` (206 → continuar, 200 → reiniciar), `.part` en `mapa/.nuevo/`, verificado → `<nombre>.<sha256>.ok` (se reutiliza al reintentar), instalación atómica solo con todo verificado, zip-slip rechazado; un `.part` de otra versión con hash malo se reintenta una vez desde cero · reanudable tras cortes y sin dejar datos a medias.
- 2026-10-28 · descargas · Un único trabajo `datos-mapa` (WorkManager, `setForeground` dataSync, backoff exponencial 30 s, 10 intentos → `SIN_RED`); `UNMETERED` con «solo WiFi», `CONNECTED` si no; cambiar el ajuste reencola. La primera descarga la pide el usuario; las actualizaciones se comprueban al abrir la app · WorkManager sobrevive a la muerte del proceso y respeta la red.
- 2026-10-28 · seguridad · Revisión opus de H1: 0 BLOCKER, 1 HIGH, 3 MEDIUM y 5 LOW. Se corrigen el HIGH (workflow en dos jobs: el build sin token de escritura), el estilo validado como solo local + `MapLibre.setConnected(false)`, el tope de bytes en la descarga y los LOW (hosts de GitHub, nombres, cleartext, hash del satélite reutilizado, lockfile npm).
- 2026-10-28 · seguridad · **Manifest sin firma** (se acepta el riesgo): la integridad depende de TLS y de la cuenta de GitHub, la misma que publica el APK; firmar solo protegería si la clave estuviera fuera de CI · reevaluar en H12 si cambia la distribución. Las fuentes que Planetiler descarga con `--download` no se verifican por hash (riesgo conocido, anotado en el README del repo de datos).
- 2026-10-28 · Room · `RallyDatabase` v1 con `exportSchema` (`app/schemas/`), `DatosMapaDao` y `AjustesMapasDao` separados · detekt `TooManyFunctions`.
- 2026-10-28 · ubicación · `FuenteUbicacionGps`: `requestLocationUpdates(GPS_PROVIDER, 1000 ms, 0 m)` en todas las API; última posición solo si tiene < 10 s; sin permiso o sin proveedor → flujo vacío; los logs nunca llevan coordenadas.

### Datos de Mallorca
- 2026-10-28 · datos · **Recomendado (pendiente de Joan):** repo público `rally-copilot-data` con GitHub Actions mensual: PBF de Geofabrik (≈45 MB) → Planetiler → `mallorca.pmtiles`; `valhalla_build_tiles` (versión fijada) → teselas de ruta; MDT05 del IGN recortado y remuestreado → elevación; `manifest.json` con SHA-256; publicación en Releases (< 2 GiB por asset). La app descarga solo lo que cambia y verifica el hash · sin backend propio, reproducible y gratuito. Detalle: [investigacion/datos-mallorca.md](investigacion/datos-mallorca.md). Ver QUESTIONS.md § Alojamiento de los datos de Mallorca.
- 2026-10-28 · límites · `maxspeed` en OSM de Mallorca (recuento de ways): autovía 95 %, primary 56 %, secondary 45 %, tertiary 34 %, unclassified 8 %, residential 16 % · hace falta un límite por defecto por clase de vía (Reglamento General de Circulación) y marcarlo como estimado (ver QUESTIONS.md).
- VERIFICAR: cobertura del MDT02 en Baleares y descarga automática desde CI en el CNIG; tamaños reales del PMTiles y de las teselas Valhalla de Mallorca (se miden en H1).

### Notas de rally
- 2026-10-28 · notas · **Recomendado:** pipeline propio y determinista en `:motor`: proyección UTM 31N, remuestreo a 5 m con suavizado (σ≈10 m), radio por circunradio a ±15 m (usar la mediana de la zona central, no el mínimo), segmentación con histéresis (entra R<250 m, sale R>400 m), grado 1–6 desde `v = √(μ·g·R)` con μ = 0,6 en una tabla calibrable, horquilla = ángulo ≥ 140° con R < 30 m, modificadores (cierra/abre por ΔR ≥ 25 %, larga ≥ 80 m, muy larga ≥ 150 m, «y» si el hueco < 25 m o < 1,5 s), distancias redondeadas, rasantes por curvatura vertical del MDT05, badenes/puentes/túneles/cruces/estrechamientos desde etiquetas OSM; anticipación `d = v·(T_nivel + t_voz)` con T_nivel 8 / 6,5 / 5 / 3,5 / 2,5 s; simplificadas 1–2 cerrada, 3–4 media, 5–6 suave · testeable con fixtures de Mallorca y calibrable sin recompilar. Un prototipo sobre la Ma-2141 dio 125 curvas y ~20 horquillas y localizó el Nus de sa Corbata. Detalle y glosario borrador: [investigacion/notas-de-rally.md](investigacion/notas-de-rally.md).
- VERIFICAR: μ, umbrales y tabla radio→grado se calibran en carretera y con las notas reales de §5.2.

### Incidencias y radares
- 2026-10-28 · incidencias · **Recomendado:** XML DATEX II v3.7 del NAP de la DGT (≈200 KB con gzip, sin autenticación), descargado al abrir la app y al iniciar ruta, filtrado en el móvil por Mallorca, con parser en streaming y caché offline con antigüedad. Medido hoy: 18 situaciones en Mallorca · cobertura real verificada, sin backend. Detalle: [investigacion/incidencias-dgt.md](investigacion/incidencias-dgt.md).
- 2026-10-28 · radares · Fijos: XML de radares de la DGT (13 en Mallorca) fusionado con OSM (79 elementos), deduplicado y empaquetado con los mapas. Móviles: tramos de cinemómetros del PDF de la DGT (24 en Mallorca) como «zona probable» (pendiente de Joan). De tramo: no hay oficiales en Mallorca hoy.
- VERIFICAR: condiciones de reutilización del NAP (la ficha dice CC BY; consultar nap@dgt.es) y si el XML de radares (fechado 2025-12-18) se sigue regenerando.

### Voz, TTS y clonado
- 2026-10-28 · voz · **Recomendado para H4:** sherpa-onnx con dos voces Piper `es_ES` masculinas (davefx medium y sharvard speaker 0; ≈21–36 MB cada una en int8/fp16), **clips pregenerados** (Opus) para el vocabulario cerrado de notas y TTS solo para frases imprevistas; ducking con `AudioFocusRequest` `GAIN_TRANSIENT_MAY_DUCK` y uso de guía de navegación · latencia nula en lo común, todo offline, mismo formato para la voz clonada. Detalle: [investigacion/voz-tts-y-clonado.md](investigacion/voz-tts-y-clonado.md).
- 2026-10-28 · voz de Moya · **Recomendado para H11:** fine-tune de Piper reutilizando el entrenador dockerizado de Trombito (`tools/voice-train`), idealmente con una sesión de lectura de ~300 frases del glosario; vídeos onboard para vocabulario (separación de voz + faster-whisper large-v3). Zero-shot (OpenVoice, Chatterbox; MIT) solo como plan B; XTTS (CPML) y F5-TTS (CC-BY-NC) descartados por licencia.
- **Riesgo de licencia:** espeak-ng (fonemizador de Piper/sherpa) es **GPL-3.0** (ver QUESTIONS.md § Licencia de la app y espeak-ng). VERIFICAR: género de cada voz, RTF de Piper en móvil medio, límites de asset packs de Play.

### Ubicación sin Google Play Services
- 2026-10-28 · ubicación · **Decidido (técnico):** interfaz de dominio `LocationSource` con implementación `LocationManager.GPS_PROVIDER` a 1 Hz y otra de reproducción GPX para debug/tests; `FUSED_PROVIDER` de AOSP solo como primera posición rápida y opcional. Servicio en primer plano tipo `location` con notificación persistente, iniciado por una acción del usuario; **sin `ACCESS_BACKGROUND_LOCATION`**; declarar «Navegación» en Play Console · funciona en ROMs sin GMS (LineageOS) y evita la revisión de ubicación en segundo plano de Play. Detalle: [investigacion/ubicacion-sin-gms.md](investigacion/ubicacion-sin-gms.md).
- VERIFICAR en el móvil real: 30+ min con pantalla apagada sin cortes; comportamiento si se deniega `POST_NOTIFICATIONS`; imagen de emulador sin Google APIs para probar sin GMS.
