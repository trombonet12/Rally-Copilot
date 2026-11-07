# Roadmap

> Propósito: plan de Rally Copilot por hitos, con alcance, criterios de aceptación, tests y estado · Audiencia: Joan y agentes · Actualizado: 2026-10-31 · Relacionado: [docs/DECISIONES.md](docs/DECISIONES.md), [QUESTIONS.md](QUESTIONS.md), [docs/ESTADO.md](docs/ESTADO.md)

Hitos en orden de dependencia, basados en la propuesta del prompt (§9) y refinados. Cada hito termina con el gate en
verde, ROADMAP y ESTADO actualizados, y las dudas acumuladas presentadas a Joan. Skill `hito` para arrancar, planificar
y cerrar. Los cambios respecto a la propuesta original están al final.

## H0 — Preparación

### Objetivo
Dejar el proyecto listo para programar: herencia, gobierno, esqueleto Android, CI de release y Play, logo, plan e
investigaciones previas.

### Alcance
- `docs/HERENCIA.md`, `CLAUDE.md`, `docs/MEMORIA.md` + skill `recordar`, skills/agentes/hooks adaptados.
- Esqueleto `:app` + `:motor` con tema oscuro por defecto e inicio provisional; CI (gate, release, Play); logo.
- `ROADMAP.md`, `docs/DECISIONES.md`, `QUESTIONS.md`, investigaciones de §8 en `docs/investigacion/`.
- Herramientas de prueba: `scripts/gps-replay.mjs` + traza real de Mallorca; carpeta `voice-lab/`.
- Fuera: cualquier funcionalidad de producto.

### Criterios de aceptación
- [x] Gate en verde: `./gradlew ktlintCheck detekt lintDebug testDebugUnitTest :motor:test assembleDebug :app:minifyReleaseWithR8`.
- [x] `check-claude-assets`, `gen-claude-index --check`, `check-docs`, `test-hooks`, tests de scripts en verde; `actionlint` sin errores.
- [x] APK debug instalado en el emulador `trombito-36`: icono adaptativo, splash y tema oscuro correctos (captura).
- [x] `gps-replay` mueve la posición del emulador por una traza real de Mallorca.
- [x] Commits locales en Conventional Commits en español, sin coautoría y sin push.

### Tests
- `PuntoGeoTest` (motor), `DomainDependencyRuleTest`, `ContrastTest`, `InicioScreenTest`, `LimpiezaComposeTest`.
- `scripts/gps-replay.test.mjs`, `scripts/play-promocionar.test.mjs`, `scripts/test-hooks.sh`.

### Estado
completado (2026-10-28) · Depende de: — · Dudas: todas las de [QUESTIONS.md](QUESTIONS.md)
<!-- Hecho: ver docs/ESTADO.md § Completado. Pendiente: pasos de Joan en docs/pasos-manuales.md (keystore, secretos, Play Console). -->

## H1 — Mapa base offline

### Objetivo
Ver Mallorca en vectorial y satélite sin conexión, con la posición GPS, la velocidad actual y el límite de la vía.

### Alcance
- Librería de mapas y formatos según [DECISIONES § Mapas](docs/DECISIONES.md#mapas-y-teselas): vista vectorial (OSM) y satélite (PNOA del IGN), cambio entre ambas, atribuciones en Acerca de (no sobre el mapa, decisión de Joan).
- Paquete de datos de Mallorca según [DECISIONES § Datos](docs/DECISIONES.md#datos-de-mallorca), desde el repo público `rally-copilot-data` (Actions mensual): descarga en el primer uso con progreso, reanudable, verificación por hash; al abrir la app comprueba si hay datos nuevos, los baja con WiFi y avisa.
- Satélite por niveles: base z≤16 de toda la isla empaquetada en el repo de datos; caché con límite de espacio (2 GB por defecto), «solo WiFi» (activado por defecto) y borrado (pantalla mínima de Mapas offline; el pulido va en H10). El corredor z17–18 y la zona manual z18–19 quedan para H9, cuando existan rutas y tramos.
- Ubicación (sin Google Play Services) con permisos y explicación; velocidad actual siempre visible y límite (`maxspeed` de OSM sobre la vía más cercana) cuando OSM lo tiene; **sin dato, no se muestra límite**.
- Orientación vertical y horizontal.
- Room entra aquí (estado de descargas y ajustes) con su skill.
- Fuera: rutas, navegación, notas.

### Criterios de aceptación
- [x] En modo avión, con el paquete descargado, el mapa vectorial y el satélite z≤16 se ven en toda Mallorca (capturas en `docs/capturas/h1/`: vista de la isla, Palma, Serra de Tramuntana).
- [x] Con `gps-replay` por la Ma-2141 a 50 km/h, la app muestra ~50 km/h y el límite de la vía en cada tramo con `maxspeed`; en los que no lo tienen, no muestra ninguno (90 y 30 en el Nus de sa Corbata, sin señal en Cals Reis; contrastado con el archivo de vías).
- [x] Funciona en un emulador sin Google APIs (el móvil de Joan es LineageOS sin GMS): AVD `rally-sin-gms-36` (`android-36/default`), sin paquetes de GMS ni Play Store; descarga, mapa, velocidad y vía.
- [x] Atribuciones de OSM e IGN en Ajustes > Acerca de (sin atribución sobre el mapa: decisión de Joan 2026-10-28).
- [x] La descarga se reanuda tras cortar la red y respeta «solo WiFi» y el límite de espacio (emulador: WiFi cortada a 118 MB → «Esperando WiFi» con 5G → reanuda con Range desde 118 MB; límite de espacio con tests Robolectric).

### Tests
- Motor: búsqueda de la vía más cercana a un punto y del límite aplicable (fixtures OSM pequeñas).
- Robolectric: gestor de descargas con un servidor falso (reanudación, hash, límite de espacio).
- Emulador: `gps-replay` de la Ma-2141 y captura de velocidad y límite.

### Estado
completado (2026-10-28) · Depende de: H0 · Dudas: 4 nuevas en QUESTIONS.md § Abiertas en H1
<!-- Hecho: repo público rally-copilot-data con workflow mensual (decidir/build sin token/publicar) y release datos-2026.10.28-2 (vectorial 32,6 MB, estilo 1 MB, vías 2,2 MB, satélite PNOA z0–16 364 MB); MapLibre 13.6.1 con pmtiles://file:// (vectorial y ráster) y spike de mbtiles:// (solo ruta absoluta); motor: manifest, plan de descarga, vías, límites y velocidad (82 tests); app: Room, WorkManager, descarga reanudable verificada, GPS sin GMS, vía actual, mapa, Mapas offline, Ajustes/Acerca de (174 tests); revisión de seguridad opus aplicada. Pendiente: corredor z17–19 en H9 (mbtiles), aviso de datos nuevos solo probado con tests (no hay segunda release real), confirmar en el móvil de Joan con GPS real. -->

## H2 — Navegación offline y mapa a tu gusto

### Objetivo
Navegación giro a giro completa sin conexión (búsqueda, rutas, recálculo, pantalla apagada) y el mapa ajustado a lo que
pidió Joan al ver H1: capas con iconos, relieve, zoom automático, icono de posición, placas de carretera reales y
cartelería de maniobras estilo WRC.

### Alcance
- **Mapa**:
  - Menú de capas con iconos (sin textos): base Mapa/Satélite e interruptor de **relieve + curvas de nivel**.
  - Botón rumbo arriba / norte arriba.
  - **Zoom automático según la velocidad**, con ajuste más cerca / más lejos en Ajustes; pellizcar lo pausa unos segundos.
  - **Icono de posición sin círculo**, elegible entre flecha simple, flecha WRC, coche de rally y casco de Trombito.
  - **Números de carretera en placas con los colores oficiales** de su red.
  - Pantalla siempre encendida con el mapa delante.
  - Diálogo de gasto de datos al desactivar «Solo con WiFi».
- **Datos** (`rally-copilot-data`): teselas Valhalla 3.9.1, índice de búsqueda (SQLite FTS), relieve (raster-dem) y curvas de nivel; estilos con capas de relieve y placas.
- **Enrutado** según [DECISIONES § Enrutado](docs/DECISIONES.md#enrutado-offline): valhalla-mobile 0.6.4; Ferrostar solo si el spike lo justifica.
- **Búsqueda**: barra «¿A dónde?» sobre el mapa con lugares, calles y POIs de OSM, y además coordenadas y enlaces de OSM/Google Maps pegados.
- **Rutas**: «Más rápida» y «Sinuosa» (penaliza autovías); vista previa con tiempo y distancia; Empezar/Terminar; recálculo al salirse.
- **Pantalla de navegación**:
  - Banner de maniobras con **estética de los juegos de WRC** (familia de iconos propia, reutilizable para las notas de H3/H4), con la siguiente maniobra.
  - Pie con hora de llegada, tiempo y km restantes.
  - Frases de copiloto («cruce, derecha, Ma-10») y el texto de Valhalla como respaldo.
- **Modo libre** sin destino: seguir la vía y grabar el recorrido.
- **Servicio en primer plano** de ubicación con notificación (`ic_stat_rally`), arrancado al pulsar Empezar/Conducir; sigue con la pantalla apagada. Sin ubicación en segundo plano.
- Fuera: voz (H4), notas (H3), cronometraje (H6).

### Criterios de aceptación
- [x] Ruta Palma → Sa Calobra calculada en modo avión en < 3 s en el emulador.
- [x] Con `gps-replay` saliéndose de la ruta, recalcula en < 5 s.
- [x] Con la pantalla apagada 5 min durante una navegación simulada, el servicio sigue y la posición avanza (logcat).
- [x] Buscar «Sóller» o «Cala Figuera» devuelve resultados sin conexión; pegar `39.83,2.81` o un enlace de Google Maps lleva a ese punto.
- [x] La ruta «Sinuosa» Palma → Sóller sube por el Coll de Sóller en lugar de ir por el túnel.
- [x] Menú de capas solo con iconos; relieve y curvas visibles en la Serra en modo avión.
- [x] El zoom se aleja al pasar de 30 a 90 km/h en `gps-replay` y respeta el ajuste.
- [x] Los 4 iconos de posición, sin círculo, elegibles en Ajustes.
- [x] Placas Ma-/AP-/A- con los colores oficiales de su red (según `docs/investigacion/carteleria.md`).
- [x] Banner de maniobras estilo WRC; capturas de las variantes para que Joan elija.
- [x] Todo funciona también en `rally-sin-gms-36`.

### Tests
- Motor (TDD): zoom por velocidad, enlaces y coordenadas, polyline6 y restantes/ETA, salida de ruta (umbral, tiempo y sentido contrario), frases de maniobra, simplificación del recorrido.
- Robolectric: ViewModels de búsqueda y navegación con enrutador y buscador falsos; migración Room v1→v2.
- Escenarios `qa-scenario`: `screen-off`, `background`, `process-death`, `gps-loss`.

### Estado
completado (2026-10-31) · Depende de: H1 · Dudas: resueltas (2026-10-31, en el chat)
<!-- Hecho: datos (Valhalla solo coche 25,9 MB, búsqueda 4,9 MB, relieve MDT05 50,8 MB, curvas 18,5 MB; placas por matrícula en datos-2026.10.31-5); valhalla-mobile 0.6.4 (Palma→Sa Calobra 1034 ms la 1.ª vez, 81 ms después); Sinuosa por el Coll; búsqueda offline, coordenadas y enlaces (los cortos con red); vista previa Más rápida/Sinuosa; banner WRC en 3 variantes (B por defecto, elegible); pie con llegada/tiempo/km; recálculo con 2 s fuera de ruta (ruta nueva a 1,5–3,1 s en el emulador) y sentido contrario a 1 s (3,6 s); modo libre automático que graba el recorrido; servicio en primer plano probado 6 min con pantalla apagada; mapa con menú de capas, relieve y curvas, zoom por velocidad, brújula, 4 iconos de posición, placas con color en mapa y panel, pantalla encendida; Room v3; revisión opus de arquitectura y seguridad aplicada (0 BLOCKER, HIGH arreglado). 398 tests JVM de app y 233 del motor. Capturas en docs/capturas/h2/ (43). Pendiente → H3: distancia del recorrido en una sola función del motor y grabación sin puntos parados; recortes de texto largos en el banner (mostrar la placa en lugar del ref); probar un enlace corto real de Joan; confirmar en el móvil con GPS real. -->

## H3 — Motor de notas

### Objetivo
Generar automáticamente notas de rally completas a partir de la geometría y la elevación de la ruta o de la vía actual.

### Alcance
- Algoritmo según [DECISIONES § Notas](docs/DECISIONES.md#notas-de-rally) en `:motor`: remuestreo, radio, segmentación, grados 1–6 con horquillas, modificadores (abre, cierra, larga, «y»/encadenadas), distancias entre notas, rasantes y cambios de rasante, badenes, cruces, estrechamientos, puentes y avisos de precaución.
- Calibración de grados con **la velocidad real de Joan**: mientras no hay datos suyos, con el límite de la vía; sus pasadas ajustan un perfil global y otro por tramo. Tabla calibrable.
- Se cantan todos los grados (también el 6), horquilla sin grado, orden «derecha cuatro», distancia después de la nota; avisos de precaución generosos (también curvas cerradas sin visibilidad y cambios de grado bruscos).
- Glosario de copiloto español (borrador en la investigación; se refina en H11 con transcripciones).
- Anticipación de lectura adaptativa a la velocidad + ajuste de usuario en 5 niveles; notas simplificadas (suave/media/cerrada).
- Presentación provisional en pantalla (texto) para validar, en una **franja bajo el banner** con las 2–3 notas siguientes (la primera grande); la voz llega en H4.
- **Modo libre** con notas: predicción de la vía (mismo ref o la más recta) que se corta en un cruce dudoso.
- Calibración **global** con los recorridos grabados (por tramo en H6); anticipación (5 niveles) y notas simplificadas en Ajustes.
- Datos: artefacto opcional `vias-notas` (etiquetas OSM de puentes, túneles, badenes, cruces, carriles y perfil de altura MDT05 cada 5 m).
- Arrastrado de H2: distancia del recorrido en una sola función del motor y grabación sin puntos parados; placa en el banner en lugar del ref; «0 m» tras recálculo; botón «Seguir a <destino>» tras la muerte del proceso.
- Skill `notas-calibrar` y agente `copiloto-reviewer` para revisar notas sobre trazas reales.
- Fuera: voz, edición (H8), perfil por tramo (H6).

### Criterios de aceptación
- [x] Notas de la Ma-2141 (Sa Calobra) y de la Ma-10 (Sóller–Lluc) revisadas por `copiloto-reviewer`: la «corbata» de Sa Calobra sale como horquilla y las rectas largas no generan notas.
- [x] El mismo tramo genera las mismas notas siempre (determinista) y en < 1 s para 15 km.
- [x] La anticipación coloca cada nota a una distancia proporcional a la velocidad, desplazada por el nivel de usuario.
- [x] La franja de notas se ve en el emulador con `gps-replay` por la Ma-2141 (vertical y horizontal); en modo libre salen notas y se cortan en un cruce dudoso (el corte, verificado con tests; en el emulador no apareció ningún cruce dudoso en la traza).
- [x] Tras matar el proceso a mitad de un guiado, al reabrir el mapa aparece «Seguir a <destino>» y retoma la ruta.

### Tests
- Motor (TDD): radio de arcos sintéticos, grado por radio y por velocidad de la vía, modificadores, encadenado, rasantes en perfiles sintéticos, distancias redondeadas, anticipación, simplificación.
- Regresión: notas de las trazas de `docs/rutas/` guardadas como golden files.

### Estado
completado (2026-10-31) · Depende de: H1 (datos de vía y elevación), H2 (ruta) · Dudas: 4 abiertas en QUESTIONS.md § Abiertas en H3; el glosario se refina en H11
<!-- Hecho: datos `vias-notas` (etiquetas OSM + perfil MDT05 cada 5 m, 1 MB) en datos-2026.10.31-6; motor de notas (curvas por circunradio con histéresis, grados relativos al límite o a la velocidad calibrada, horquillas, larga/muy larga por ángulo, abre/cierra, «y», distancias, rasantes convexas, avisos OSM, cruces por rango, precaución, anticipación en 5 niveles, simplificadas, calibración global, predicción de vía en modo libre), revisado por copiloto-reviewer (opus) y calibrado; goldens de la Ma-2141 y la Ma-10 (8 ms para 15 km); notas en vivo en el servicio (guiado y libre); franja de notas bajo el banner con iconos roadbook; ajustes de anticipación y simplificadas (Room v4); «Seguir a <destino>» tras la muerte del proceso; pantalla de conducción rediseñada a petición de Joan (mapa inclinado 50° con el marcador abajo, banner a todo el ancho con placa, botones en el panel inferior, sin flecha de volver, horizontal compacto); distancia del recorrido con una sola regla y sin puntos parados; arreglo del «0 m» tras recálculo; glosario `docs/glosario.md`, skill `notas-calibrar` y `scripts/traza-desde-vias.py`. 519 tests JVM de app y 463 del motor. Capturas en docs/capturas/h3/. Pendiente: corte en cruce dudoso visto solo en tests; notas con ángulo < 10° de grado 5 algo ruidosas; «cruce, stop» al arrancar en el cruce de la Ma-2141; confirmar en el móvil con GPS real. -->

## H4 — Voz y modos del copiloto

### Objetivo
El copiloto habla: voz sintética offline, cuatro modos, bajada de la música mientras habla y avisos visuales.

### Alcance
- TTS en el dispositivo según [DECISIONES § Voz](docs/DECISIONES.md#voz-tts-y-clonado), con un par de voces masculinas a elegir.
- Modos: Silencio · Solo alertas · Notas · Copiloto completo (delta y parciales por voz cuando exista H6).
- Audio ducking con AudioFocus; cola de frases con prioridades (una nota nunca llega tarde: se descarta si ya pasó).
- Avisos visuales: icono de nota grande tipo roadbook y banners animados de radar/incidencia que entran y salen.
- Fuera: voz de Moya (H11).

### Criterios de aceptación
- [x] En modo avión, la nota suena antes de su punto con la anticipación configurada (medido con `gps-replay`): Ma-2141 a 60 km/h, nivel normal, se empieza a decir a 83–100 m (≈ 5–6 s); alguna a 40–65 m cuando se encadenan.
- [x] Con música sonando, baja durante la voz y vuelve después: foco `GAIN_TRANSIENT_MAY_DUCK` con uso de guía de navegación pedido y soltado (`dumpsys audio` en el emulador + `GestorFocoAudioTest`); falta oírlo con música real en el móvil.
- [x] Cada modo solo dice lo que le toca (tabla de casos): `ModoCopilotoTest` (4 × 4) y `CopilotoVozTest`.

### Tests
- Motor: planificador de frases (prioridades, caducidad, modo).
- Robolectric: gestor de foco de audio con un AudioManager falso.

### Estado
completado (2026-11-01) · Depende de: H3 · Dudas: resueltas (2026-10-28): dos voces dentro, clips + TTS, app GPL-3.0; (2026-11-01): modo inicial Completo, Solo alertas con maniobras, voz elegida en el primer uso
<!-- Hecho: respuestas del cierre de H3 (compresión, muy cerrada, sin curvas ≤ 30, barra oculta en libre, distancia en la franja);
sherpa-onnx 1.13.8 + voces Piper int8 davefx/sharvard dentro del APK, frases sintetizadas por adelantado y cacheadas en el móvil
(sustituye a los clips en Actions), PlanificadorFrases con caducidad y modos, AvisosManiobra (preaviso + inminente), Locutor con
AudioTrack y ducking, Room v5 (modo y voz), selector de modo en el panel, voz elegida en el primer uso, sección Copiloto en Ajustes,
ids de nota estables entre hojas. Peticiones de Joan durante el hito: ubicación bajo techo (fused/red), todo menos el satélite
dentro del APK (siembra en el primer arranque, Mapas offline solo satélite, límite 4 GB), satélite z17 (manifest-extra),
marcador y rumbo pegados a la ruta/vía, rumbo del receptor solo si es preciso.
Pendiente: banner animado de radar/incidencia → H5 (aún no hay alertas); oír el ducking con música y medir el RTF en el móvil de
Joan; revisión `copiloto-reviewer` de las frases de maniobra habladas (las de notas son las revisadas en H3). -->

## H5 — Alertas: radares e incidencias

### Objetivo
Avisar de radares e incidencias de tráfico de Mallorca con datos abiertos.

### Alcance
- Fuentes según [DECISIONES § Incidencias](docs/DECISIONES.md#incidencias-y-radares).
- Radares fijos y de tramo guardados offline con los mapas; incidencias (obras, accidentes, retenciones, cortes) refrescadas al abrir la app y al iniciar una ruta.
- Avisos por voz (modos Solo alertas en adelante) y banners animados que entran y salen (pasado de H4); límite de velocidad excedido.
- Zonas probables de radar móvil en pantalla y por voz; incidencias cercanas aunque estén fuera de la ruta. Sin botón de refresco manual.
- Fuera: Waze y Google (sin API pública).

### Criterios de aceptación
- [x] Un radar fijo de la Ma-13 avisa a la distancia configurada en una reproducción `gps-replay`: `ma-13-santa-maria-palma.gpx` en libre, radar `dgt-120643` a 381 m yendo a 100 km/h (ventana 400 m) y a 586 m a 140 km/h (ventana 600 m); exceso dicho una sola vez.
- [x] Sin red, la app usa la última copia de incidencias e indica su antigüedad: `RepositorioIncidenciasDgtTest` (copia y antigüedad sin red, caducadas fuera) y UI con «hace 2 h» (`MapaScreenAlertasTest`); sin probar con el feed real en el móvil.

### Tests
- Motor: parseo de DATEX II (fixtures reales anonimizadas), proximidad a radar en la ruta y en sentido de la marcha.
- Robolectric: refresco con fuente falsa (éxito, error, sin red).

### Estado
completado (2026-11-01) · Depende de: H1, H4 · Dudas: resueltas (2026-10-28); (2026-11-01): exceso +10 %, radar según velocidad, media en tramo, incidencias a ≤ 3 km
<!-- Hecho: artefacto `radares` (27 fijos, 5 tramos OSM, 9 zonas de radar móvil; datos-2026.11.01-9, dentro del APK); motor con
DATEX II en streaming (interfaz pull propia: StAX solo en tests, XmlPullParser en Android), AlertasRadar, media de tramo,
exceso +10 % y filtro de incidencias; incidencias de la DGT al abrir y al empezar ruta con caché y antigüedad; alertas por voz
(modo Solo alertas en adelante) en guiado y en libre sobre la carretera predicha; banner animado de radar/incidencia, exceso
como pastilla roja, media de tramo, incidencias en el mapa. Revisión de seguridad [opus]: 0 BLOCKER/HIGH, 2 MEDIUM y 3 LOW
arreglados. Traza Ma-13 nueva (el script de trazas respeta los sentidos únicos).
Pendiente: media de tramo solo en guiado; incidencias reales de la DGT y zonas móviles por PK sin probar en el móvil;
VERIFICAR condiciones de reutilización del NAP con nap@dgt.es. -->

## H5B — Navegación: buscador, paradas y gestos

### Objetivo
Arreglar lo que Joan echa en falta al usar la app a diario (2026-11-01): mapa que se puede girar e inclinar, buscador que
encuentra calles, monumentos y negocios, ir a un punto manteniéndolo pulsado, rutas con varias paradas y un panel
inferior que no estorba mientras se planifica.

### Alcance
- Gestos: girar e inclinar el mapa con dos dedos (hoy desactivados); al hacerlo se deja de seguir al coche y el botón de
  recentrar vuelve al modo elegido (rumbo arriba e inclinado, o norte).
- Buscador por palabras: todas las palabras de la consulta, en cualquier orden y como prefijo de alguna palabra del
  nombre o del municipio («colom palma»); sinónimos castellano → catalán (calle/carrer, plaza/plaça, castillo/castell,
  playa/platja, iglesia/església…); palabras vacías fuera (de, del, la, el, d'); una categoría sola («hospital»,
  «gasolinera», «farmacia») da los más cercanos de ese tipo; orden por coincidencia, importancia y cercanía. En el repo
  de datos: también POIs dibujados como multipolígono (relaciones) y negocios con nombre.
- Mantener pulsado en el mapa: marcador y tarjeta con el nombre o la calle del punto y «Ir aquí» / «Añadir parada».
- Rutas con paradas: en el orden en que se añaden, reordenables arrastrando; al llegar o pasar por una se activa la
  siguiente; banner y pie (llegada, tiempo, km) hasta la próxima parada y hasta el final.
- Panel inferior compacto mientras se está parado planificando (búsqueda, tarjeta o vista previa de ruta, v < 5 km/h);
  se amplía al empezar a moverse (> 10 km/h) o al empezar la ruta.
- Fuera: optimizar el orden de las paradas.

### Criterios de aceptación
- [x] El mapa gira e inclina con dos dedos y «recentrar» vuelve al seguimiento: gestos activados en `VistaMapa` (el gesto de dos dedos no se puede simular por adb; lo prueba Joan en el móvil).
- [x] «hospital son espases», «castillo bellver», «calle colom palma» y «hospital» (desde Palma) encuentran lo esperado en
  los primeros resultados: `BuscadorIndiceRealTest` con el índice real de `datos-2026.11.01-10` (4/4).
- [x] Mantener pulsado → Ir aquí calcula la ruta; con dos paradas añadidas el guiado pasa por la primera y sigue a la
  segunda: `SesionNavegacionParadasTest` (replay de dos paradas) y capturas en el emulador.
- [x] Parado en la vista previa el panel es compacto y se amplía al moverse: `PanelCompactoTest` y [capturas](docs/capturas/h5b/).

### Tests
- Motor: lógica de paradas (llegada/paso, siguiente), normalización y sinónimos de la consulta.
- App: buscador con base SQLite de prueba e índice real; Robolectric de la tarjeta y del panel compacto.

### Estado
completado (2026-11-01) · Depende de: H2, H5 · Dudas: resueltas (2026-11-01): tarjeta Ir aquí / Añadir parada, paradas
en el orden añadido, buscar todo por palabras
<!-- Hecho: gestos de girar/inclinar; buscador por palabras (tabla `palabras` en el repo de datos, sinónimos castellano↔catalán,
categorías, calles primero si se pide calle, POIs de multipolígono); rutas con paradas (Valhalla con varias locations,
seguimiento por tramos, voz «parada alcanzada», Room v6); mantener pulsado con tarjeta; lista de paradas (flechas en vez de
arrastre); pie con próxima parada y final; panel compacto parado (< 5 km/h, se amplía a > 10 km/h).
Pendiente: dudas de producto en QUESTIONS (cambiar destino o buscar durante el guiado, paradas al cambiar de destino);
horizontal solo por tests. -->

## H6 — Tramos y cronometraje

### Objetivo
Convertir cada trayecto en un tramo cronometrado con sectores, detección de tramos conocidos y delta en vivo.

### Alcance
- Tramo = de pulsar Conducir a pulsar Terminar o llegar al destino; los tramos < 1 km se descartan; sin cierre automático por estar parado. Grabación automática activable en Ajustes.
- 4 sectores grandes y microsectores equitativos, configurables (número y posición).
- Detección de tramo conocido aunque no se arranque desde él (map matching contra tramos guardados).
- Delta en vivo contra el mejor tiempo (verde/rojo) y parciales; voz en modo copiloto completo.
- HUD de retransmisión: cronómetro, barra de progreso, parciales y delta.
- Fuera: análisis post-tramo (H7), edición de sectores arrastrando sobre el mapa (H8).

### Criterios de aceptación
- [x] Repetir la misma traza a dos velocidades da delta negativo en la rápida, sector a sector: `TramosCriteriosTest` (Ma-2141) y `SesionCronoTest`; en el emulador, 3 km de la Ma-2141 a 50 y a 70 km/h (S1 −31,0 · S2 −13,5 · S3 −16,0, meta −1:16,9, [capturas](docs/capturas/h6/)).
- [x] Una traza que entra a mitad de un tramo guardado y lo recorre entero se detecta y se compara: `TramosCriteriosTest` (empieza 2 km antes del tramo).

### Tests
- Motor (TDD): cronometraje con reloj inyectado, cortes de sector, delta interpolado por distancia, detección de tramo (inicio, fin, desvío).
- Room: migraciones con tests.

### Estado
completado (2026-11-01) · Depende de: H2 · Dudas: resueltas (2026-10-28); (2026-11-01): todo trayecto ≥ 1 km es tramo, delta por voz en sectores y meta, micro por km, HUD con tramo conocido
<!-- Hecho: motor tramos/ (Cronometro con cortes interpolados, Delta, DetectorTramo, TramoDesdeRecorrido, meta 10 m antes del
final); Room v7 (tramo, pasada); cada conducción ≥ 1 km crea tramo salvo que repita uno completado; delta por voz en modo
Completo; HUD de retransmisión con delta y parciales solo con tramo conocido, cronómetro pequeño en uno nuevo, tarjeta de meta.
Pendiente: grabación automática activable en Ajustes (hoy siempre activa; va a H10 con Ajustes rehechos); nombrar tramos y
configurar sectores (H7/H8); probar en el coche. -->

## H7 — Análisis post-tramo y perfil del piloto

### Objetivo
Analizar cada tramo y mostrar la evolución del piloto.

### Alcance
- Tiempo total y por sector/microsector, delta contra el mejor, velocidad media y máxima por sector.
- Gráfico velocidad/distancia, mapa coloreado por velocidad, repetición del recorrido sobre el mapa.
- Perfil: kilómetros, número de tramos, mejores tiempos, evolución, velocidades medias e historial de análisis.
- Fuera: exportar o compartir.

### Criterios de aceptación
- [x] El análisis de un tramo grabado con `gps-replay` coincide con los tiempos del cronometraje en vivo (±0,1 s): `AnalisisPasadaTest` contra los `EventoCrono` reales de la Ma-2141; pantalla probada en el emulador con pasadas de `gps-replay` ([capturas](docs/capturas/h7/)).
- [x] El perfil agrega correctamente varios tramos de prueba: `PerfilPilotoTest` y `PerfilViewModelTest`.

### Tests
- Motor: estadísticas por sector, agregados del perfil.
- Robolectric: pantallas con datos falsos (BancoCompose).

### Estado
completado (2026-11-02) · Depende de: H6 · Dudas: resueltas (2026-11-01): análisis desde Mis tramos y la meta, comparar con la mejor o elegir, nombres automáticos editables, lista con filtros
<!-- Hecho: motor analisis/ (AnalisisPasada, EscalaVelocidad, PerfilPiloto, NombreTramo); Room v8 (nombre automático, favorito,
último uso, pasada ↔ recorrido); Mis tramos (filtros, favoritos, renombrar, borrar con deshacer), detalle del tramo, perfil del
piloto con evolución e historial; pantalla de análisis (sectores y micros, gráficos de velocidad y delta, mapa coloreado,
repetición x1/x4/x16, comparada elegible); «Ver análisis» en la tarjeta de meta (la pasada se guarda al cruzar la meta).
Pendiente: los tramos creados antes de v8 salen «Tramo sin nombre» (renombrables); fuera de alcance exportar/compartir. -->

## H8 — Edición de notas y reconocimiento

### Objetivo
Corregir notas a mano o por voz y hacer pasadas de reconocimiento.

### Alcance
- Edición tocando la nota sobre el mapa (parado o desde el detalle del tramo); edición por voz (reconocimiento offline) activada con un botón en la app que pausa la música mientras escucha, y palabra de activación «Copiloto» opcional (desactivada por defecto).
- Órdenes de voz: corregir notas, repite, más pronto / más tarde, silencio / habla, marca aquí.
- Notas editadas ligadas al tramo; siempre prevalecen sobre las automáticas.
- Modo reconocimiento: pasada para dictar o corregir notas; recalibra el perfil de velocidad de Joan en ese tramo.
- Edición de sectores arrastrando sobre el mapa.
- Fuera: compartir notas.

### Criterios de aceptación
- [x] Una nota editada sobrevive a regenerar las automáticas del tramo: `FusionNotasTest`, `NotasSesionEdicionesTest`.
- [x] Dictar «derecha tres larga» en reconocimiento crea esa nota en la posición actual: test de reconocimiento (JVM); Vosk carga con la gramática en el emulador (`VoskDebugReceiver`).

### Tests
- Motor: fusión de notas editadas con automáticas; intérprete de órdenes de voz (gramática del glosario).

### Estado
completado (2026-11-02) · Depende de: H3, H6 · Dudas: resueltas (2026-10-28); (2026-11-02): botón en la app que pausa la música, «Copiloto» desactivada, cuatro órdenes, editar parado
<!-- Hecho: motor edicion/ (intérprete de órdenes, fusión); Vosk 0.3.75 + modelo small-es con gramática cerrada (Room v9:
marca, «Copiloto»); órdenes repite, más pronto/tarde, silencio/habla, marca aquí; ediciones en Room v10; edición en el mapa
parado y desde el tramo; reconocimiento; sectores arrastrables y micros por km.
Pendiente: probar la UI nueva y el reconocimiento de voz real en el móvil (el emulador no tiene micrófono); liberar el modelo
Vosk al quedar inactivo; duda de aviso sonoro tras «Copiloto». -->

## H9 — Precarga de rutas y satélite de alta calidad

### Objetivo
Preparar tramos antes de conducirlos y descargar satélite de más detalle donde importa.

### Alcance
- Importar GPX/KML, dibujar rutas en el mapa y reutilizar rutas recorridas.
- Satélite z18 automático con WiFi en un corredor de ~300 m de rutas planificadas y tramos guardados (z17 ya cubre la isla).
- Descarga manual de una zona a z18–19.
- Fuera: exportar.

### Criterios de aceptación
- [x] Un GPX importado genera tramo, notas y descarga de corredor: `ImportadorTramosTest` y encolado del corredor al crear tramo (`PrecargaDetalleTest`).
- [x] El corredor descargado se ve sin red a z18 alrededor de la ruta y no fuera de ella: zona z18 real del IGN bajada y vista en el emulador ([captura](docs/capturas/h9/)); corredor por tests.

### Tests
- Motor: parseo GPX/KML (variantes reales), cálculo de teselas de un corredor.

### Estado
completado (2026-11-02) · Depende de: H1, H6 · Dudas: resueltas (2026-11-02): z18 automático con WiFi, importar por compartir y botón, dibujar tocando puntos, borrar satélite incluye detalle
<!-- Hecho: lectores GPX/KML (motor/importar), importar por Compartir/Abrir con y botón, dibujar rutas tocando puntos, tic tras
«Copiloto», teselas de corredor/rectángulo (motor/precarga), descargador IGN a MBTiles con WorkManager, capa z18–19, sección
Detalle y zona manual en Mapas offline.
Pendiente: probar «Abrir con» desde gestores de archivos reales en el móvil; a z19 fuera de zonas z19 se ve el z17 ampliado. -->

## H10 — Interfaz completa estilo WRC

### Objetivo
Pulir toda la app con estética de videojuego WRC y retransmisión de rally.

### Alcance
- Menú de inicio tipo juego (Conducir · Mis tramos · Reconocimiento · Mapas offline · Perfil del piloto · Ajustes).
- Pantalla de conducción con el mapa como protagonista y modo alternativo centrado en notas grandes.
- Iconos de notas tipo roadbook, banners animados, HUD.
- Ajustes completos: voz, modo del copiloto, anticipación, notas simplificadas, grabación automática, mapas, tema (oscuro por defecto, claro opcional).
- Ajustes rehechos (Joan, 2026-11-01: «tétrica y poco intuitiva»): cada sección con una **vista previa en vivo** arriba que cambia al tocar la opción (p. ej. la tarjeta de nota, «te la canta 5 s antes», ▶ escuchar ejemplo).
- Ajustes rápidos en el mapa, además de capas, orientación y modo: **anticipación de las notas, notas simplificadas y volumen de la voz** (volumen propio del copiloto, aparte del del móvil).
- Fuera: Android Auto.

### Criterios de aceptación
- [ ] Revisión de `ux-reviewer` sin hallazgos altos (pendiente por límite de uso); `ContrastTest` en verde en ambos temas: hecho.
- [x] Capturas de cada pantalla en vertical y horizontal: 48 en [capturas/h10](docs/capturas/h10/) (oscuro y claro).

### Tests
- Robolectric: render de pantallas con BancoCompose; contraste.

### Estado
completado (2026-11-02) · Depende de: H1–H9 · Dudas: resueltas (2026-11-02)
<!-- Hecho: Ajustes con vista previa en vivo, hoja única de copiloto (modo + ajustes rápidos), volumen de voz, tema
oscuro/claro/sistema con mapa claro (datos-2026.11.02-11), Cronometrar trayectos, salida al moverse, notas grandes, menú de
inicio simplificado (Reconocer en el tramo, Mapas offline en Ajustes), pulido WRC (Room v11–v12).
Pendiente: revisión ux-reviewer; probar notas grandes conduciendo. -->

## H11 — Voz de Luis Moya

### Objetivo
El copiloto habla con la voz clonada de Luis Moya (con autorización), sin internet.

### Alcance
- Solo material existente de Moya (sin sesión de grabación): el pipeline tiene que sacar audio limpio de entrevistas y onboards con ruido.
- Pipeline en `voice-lab/` (PC, RTX 4080): separación de voz, transcripción local, extracción de vocabulario, entrenamiento/clonado, pregeneración de frases comunes. Documentado en `docs/VOZ.md`.
- Glosario refinado con las transcripciones reales.
- En la app: audios pregenerados para lo común y motor TTS con la voz clonada para lo imprevisto; todo dentro del APK.
- Fuera: otras voces clonadas.

### Criterios de aceptación
- [ ] `docs/VOZ.md` permite repetir el pipeline desde cero.
- [ ] Una reproducción de la Ma-2141 suena entera con la voz de Moya sin red.

### Tests
- Cobertura de frases: toda nota que puede generar el motor tiene audio pregenerado o pasa por TTS.

### Estado
pendiente hasta nueva orden de Joan (2026-11-02) · sin material en `voice-lab/data/raw/`

## H12 — Pruebas end-to-end y Play

### Objetivo
Validar la app completa de extremo a extremo. Distribución actual: GitHub Release + Obtainium; Play cuando Joan lo active.

### Alcance
- Batería E2E (instrumentada en emulador con trazas reales y GPS simulado).
- Pruebas en el móvil de Joan por USB.
- Release estable por GitHub + Obtainium.
- Más adelante (pospuesto por Joan): ficha de Play (textos, capturas, política de privacidad, seguridad de datos) y subida al track de pruebas con `PLAY_HABILITADO=true`.

### Criterios de aceptación
- [ ] Batería E2E en verde en el emulador.
- [ ] Prueba real en coche validada por Joan.
- [x] Versión publicada en GitHub (v1.0.0); instalación con Obtainium en el móvil: Joan.
- [ ] (Pospuesto) Versión en la pista interna de Play y promocionada a la prueba cerrada.

### Tests
- E2E: navegación + notas + voz + cronometraje + análisis sobre las trazas de `docs/rutas/`.

### Estado
completado en versión recortada (2026-11-02, v1.0.0) por el límite de uso semanal · Depende de: H1–H10 (H11 pendiente)
<!-- Hecho: gate del CI en verde (v0.11.0), release v1.0.0. Pendiente: batería E2E instrumentada en emulador, revisión
ux-reviewer de H10, prueba real en coche y en el móvil (Joan), H11 (voz de Moya) cuando haya material. -->

## H13 · Notas por grados (en curso, 2026-11-03)
### Objetivo
Petición de Joan tras sus primeras pruebas con la v1.0: «canta muy poco las curvas». La escala pasa a depender solo del giro total de la curva:
- 6 = 10–30°, 5 = 30–60°, 4 = 60–90°, 3 = 90–120°, 2 = 120–150°, 1 = 150–180°;
- «escuadra» para unos 90° cerrados y lentos;
- «horquilla» por encima de 180°;
- por debajo de 10° no se canta.

TDD con los límites de cada rango; los goldens de la Ma-2141 y la Ma-10 se regeneran y se revisan a mano.

### Alcance
Lo descrito en Objetivo.

### Criterios de aceptación
- Cada rango de grados da su número; escuadra y horquilla se detectan; menos de 10° no se canta.
- Goldens Ma-2141 y Ma-10 revisados a mano.

### Tests
- Motor: tabla de límites (9, 10, 30, 60, 90, 120, 150, 180, 181°) y escuadra frente a 4 abierta.

### Estado
Completado (2026-11-03, v1.1.0). Pendiente: «escuadra» en las órdenes de voz (InterpreteOrdenes) y prueba en coche.

## H14 · Interfaz 1.0 de verdad (pendiente)
### Objetivo
Feedback de Joan del 2026-11-02 y 2026-11-03: «se nota que está en pruebas».

**Crono**
- Salida real: más de 15 km/h durante 3 s, o más de `max(50 m, 2×precisión)` con precisión ≤ 20 m. La deriva del GPS en parado no debe arrancar el crono.
- Mientras no hay salida, el crono muestra LISTO.
- Ajuste para elegir salida Automática (por defecto) o Manual. La manual es una cuenta atrás WRC (5 luces y voz).

**Estilo**
- WRC premium, sin «pegotes» de fondo rojo: el rojo solo como acento.
- Los paneles (por ejemplo el aviso rojo) van con fondo negro vacío, como las tarjetas de notas, que es el diseño que más le gusta.
- Componentes `FormaBiselada`, `TarjetaWrc` y `FondoWrc`, con animaciones cortas.

**Ajustes**
- Rejilla de tarjetas con icono y valor actual.
- Cada sección tiene su subpantalla, con una vista previa fija en vivo y textos de una línea.
- Las voces se muestran con su nombre real.

**Mapa conduciendo**
- Panel flotante translúcido, más pequeño.
- El icono propio va más abajo, para ver más carretera.
- Botones más grandes, que se esconden en marcha (igual que la barra «¿A dónde?»).
- El aviso de mapas actualizados pasa a ser un toast.
- Inclinar el mapa en satélite cuesta: revisar.

**Conducción libre**
- Estela roja del recorrido.
- La ruta, roja en vez de dorada.

**Radares e incidencias**
- Siempre visibles en el mapa, con un bocadillo al acercarse.
- Aviso con más antelación, también de los del sentido contrario.
- En los estáticos, la velocidad a la que se pasó.
- El tramo de un radar de tramo, dibujado en el mapa.

**Indicaciones**
- Bug: la 3.ª salida de las rotondas se canta como 2.ª.
- Sobran cruces en las entradas y salidas de autopista: distinguir los cruces de las salidas.
- Ajustes para activar o desactivar cruces, salidas de autopista y rotondas.
- Futuro: indicaciones con el formato de las notas, en carrete (la actual en el centro y la siguiente, aún sin cantar, a la derecha).

**Inicio**
- CONDUCIR como protagonista.
- Último tramo con su mini traza.
- Accesos con icono grande.

**Datos**
- Copia de seguridad: «Ajustes > Exportar copia» (zip de la base Room con CreateDocument) e «Importar copia» para restaurarla, para poder guardar los datos y sacarlos del móvil (hoy la release no es depurable y tiene `allowBackup=false`).

**Mis tramos, detalle y perfil**
- El detalle no muestra el mapa, solo la línea: poner la traza sobre el mapa real.
- El editor de microsectores está roto: las flechas +/− por km van al revés y no se entiende qué hace.
- Desde las estadísticas se debe poder volver a abrir el análisis de la pasada.
- Mini traza en cada fila.

**Verificación al cerrar:** capturas contrastadas punto por punto con esta lista, más el ux-reviewer.

**Sub-hitos (2026-11-03), cada uno con su release:**
- **H14a, bugs (v1.2):**
  - salida del crono y estado LISTO;
  - la 3.ª salida de las rotondas;
  - sin cruces en autovía;
  - «escuadra» por voz;
  - Mis tramos: microsectores, detalle con mapa y análisis desde Perfil;
  - inclinar en satélite.
- **H14b, funciones (v1.3):**
  - exportar/importar copia;
  - filtros de indicaciones;
  - ruta roja y estela gris;
  - coche más abajo y botones que se esconden;
  - salida manual con cuenta atrás;
  - radares.
- **H14c, rediseño (v1.4):**
  - estilo WRC premium;
  - panel flotante;
  - Ajustes;
  - Inicio;
  - Mis tramos/Perfil.

**Respuestas:**
- **Autovía:** sin cruces ni incorporaciones; con ruta, solo la salida que se toma.
- **Radares:**
  - aviso a unos 15 s y a 100 m;
  - los de sentido contrario también («radar, sentido contrario»);
  - bocadillo con la velocidad de paso en verde o rojo.
- **Colores:** ruta roja y estela gris apagada.

**Ampliación (2026-11-06):**
- **H14a (bugs):** suma la sensibilidad de las curvas (cantar casi todo giro de 10° o más; feedback «no lo pilla nada bien») y los puentes y pasos a distinto nivel.
- **H14c:** empieza por una revisión extensa de toda la interfaz con capturas y ux-reviewer. La distribución del mapa sigue la referencia de Google Maps con estética WRC; incluye el coche más abajo y los botones.
- **H14d:** voz ampliada (preguntas de ruta, control del copiloto, navegación, mapa y crono), con «Copiloto» activado por defecto y una chuleta de voz.
- **H14e:** tutorial (bienvenida, pistas en contexto y Ajustes > Ayuda).
- **Cierre único:** revisión en el emulador con el dump de Joan y capturas; después, push y release v1.2.0.

### Alcance
Lo descrito en Objetivo.

### Criterios de aceptación
- Cada punto de la lista, verificado con capturas en `docs/capturas/h14/` y revisión ux-reviewer.

### Tests
- Detector de salida con deriva simulada; numeración de salidas de rotonda; filtros de indicaciones; tests de Compose de los componentes nuevos.

### Estado
Completado (2026-11-07, v1.2.0), con todos los sub-hitos H14a–e y una revisión final en el emulador con los datos de Joan (capturas en `docs/capturas/h14/`).
**Pendiente:**
- Probarlo en el coche: curvas por rumbo, rotondas, puentes, radares y voz con micrófono real.
- Lugares con palabras que Vosk no conoce, como Pollença o Llucmajor, que no se pueden pedir por voz.
- Transcripción parcial en la burbuja de escucha.
- «Dónde estoy» sin punto kilométrico.
- `NotificacionNavegacion` con su propio formateo.

## Cambios respecto a la propuesta inicial
- **H1** deja el corredor z17–18 y la zona manual z18–19 para **H9**, porque dependen de rutas y tramos guardados.
- **H3** incluye una presentación provisional en texto para validar las notas antes de la voz.
- **H8** incorpora la edición de sectores arrastrando sobre el mapa (pedida en §4.7).
- **H11** puede empezar en paralelo en el PC (pipeline de `voice-lab/`) en cuanto Joan aporte material, aunque la integración en la app espere a H4.
