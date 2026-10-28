# Roadmap

> Propósito: plan de Rally Copilot por hitos, con alcance, criterios de aceptación, tests y estado · Audiencia: Joan y agentes · Actualizado: 2026-10-28 · Relacionado: [docs/DECISIONES.md](docs/DECISIONES.md), [QUESTIONS.md](QUESTIONS.md), [docs/ESTADO.md](docs/ESTADO.md)

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
- [ ] Ruta Palma → Sa Calobra calculada en modo avión en < 3 s en el emulador.
- [ ] Con `gps-replay` saliéndose de la ruta, recalcula en < 5 s.
- [ ] Con la pantalla apagada 5 min durante una navegación simulada, el servicio sigue y la posición avanza (logcat).
- [ ] Buscar «Sóller» o «Cala Figuera» devuelve resultados sin conexión; pegar `39.83,2.81` o un enlace de Google Maps lleva a ese punto.
- [ ] La ruta «Sinuosa» Palma → Sóller sube por el Coll de Sóller en lugar de ir por el túnel.
- [ ] Menú de capas solo con iconos; relieve y curvas visibles en la Serra en modo avión.
- [ ] El zoom se aleja al pasar de 30 a 90 km/h en `gps-replay` y respeta el ajuste.
- [ ] Los 4 iconos de posición, sin círculo, elegibles en Ajustes.
- [ ] Placas Ma-/AP-/A- con los colores oficiales de su red (según `docs/investigacion/carteleria.md`).
- [ ] Banner de maniobras estilo WRC; capturas de las variantes para que Joan elija.
- [ ] Todo funciona también en `rally-sin-gms-36`.

### Tests
- Motor (TDD): zoom por velocidad, enlaces y coordenadas, polyline6 y restantes/ETA, salida de ruta (umbral y tiempo), frases de maniobra, simplificación del recorrido.
- Robolectric: ViewModels de búsqueda y navegación con enrutador y buscador falsos; migración Room v1→v2.
- Escenarios `qa-scenario`: `screen-off`, `background`, `process-death`, `gps-loss`.

### Estado
en curso (2026-10-28) · Depende de: H1 · Dudas: resueltas (2026-10-28, en el chat)

## H3 — Motor de notas

### Objetivo
Generar automáticamente notas de rally completas a partir de la geometría y la elevación de la ruta o de la vía actual.

### Alcance
- Algoritmo según [DECISIONES § Notas](docs/DECISIONES.md#notas-de-rally) en `:motor`: remuestreo, radio, segmentación, grados 1–6 con horquillas, modificadores (abre, cierra, larga, «y»/encadenadas), distancias entre notas, rasantes y cambios de rasante, badenes, cruces, estrechamientos, puentes y avisos de precaución.
- Calibración de grados con **la velocidad real de Joan**: mientras no hay datos suyos, con el límite de la vía; sus pasadas ajustan un perfil global y otro por tramo. Tabla calibrable.
- Se cantan todos los grados (también el 6), horquilla sin grado, orden «derecha cuatro», distancia después de la nota; avisos de precaución generosos (también curvas cerradas sin visibilidad y cambios de grado bruscos).
- Glosario de copiloto español (borrador en la investigación; se refina en H11 con transcripciones).
- Anticipación de lectura adaptativa a la velocidad + ajuste de usuario en 5 niveles; notas simplificadas (suave/media/cerrada).
- Presentación provisional en pantalla (texto) para validar; la voz llega en H4.
- Skill `notas-calibrar` y agente `copiloto-reviewer` para revisar notas sobre trazas reales.
- Fuera: voz, edición (H8).

### Criterios de aceptación
- [ ] Notas de la Ma-2141 (Sa Calobra) y de la Ma-10 (Sóller–Lluc) revisadas por `copiloto-reviewer`: la «corbata» de Sa Calobra sale como horquilla y las rectas largas no generan notas.
- [ ] El mismo tramo genera las mismas notas siempre (determinista) y en < 1 s para 15 km.
- [ ] La anticipación coloca cada nota a una distancia proporcional a la velocidad, desplazada por el nivel de usuario.

### Tests
- Motor (TDD): radio de arcos sintéticos, grado por radio y por velocidad de la vía, modificadores, encadenado, rasantes en perfiles sintéticos, distancias redondeadas, anticipación, simplificación.
- Regresión: notas de las trazas de `docs/rutas/` guardadas como golden files.

### Estado
pendiente · Depende de: H1 (datos de vía y elevación), H2 (ruta) · Dudas: resueltas (2026-10-28); el glosario se refina en H11

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
- [ ] En modo avión, la nota suena antes de su punto con la anticipación configurada (medido con `gps-replay`).
- [ ] Con música sonando, baja durante la voz y vuelve después.
- [ ] Cada modo solo dice lo que le toca (tabla de casos).

### Tests
- Motor: planificador de frases (prioridades, caducidad, modo).
- Robolectric: gestor de foco de audio con un AudioManager falso.

### Estado
pendiente · Depende de: H3 · Dudas: resueltas (2026-10-28): dos voces dentro, clips + TTS, app GPL-3.0

## H5 — Alertas: radares e incidencias

### Objetivo
Avisar de radares e incidencias de tráfico de Mallorca con datos abiertos.

### Alcance
- Fuentes según [DECISIONES § Incidencias](docs/DECISIONES.md#incidencias-y-radares).
- Radares fijos y de tramo guardados offline con los mapas; incidencias (obras, accidentes, retenciones, cortes) refrescadas al abrir la app y al iniciar una ruta.
- Avisos por voz (modos Solo alertas en adelante) y banners; límite de velocidad excedido.
- Zonas probables de radar móvil en pantalla y por voz; incidencias cercanas aunque estén fuera de la ruta. Sin botón de refresco manual.
- Fuera: Waze y Google (sin API pública).

### Criterios de aceptación
- [ ] Un radar fijo de la Ma-13 avisa a la distancia configurada en una reproducción `gps-replay`.
- [ ] Sin red, la app usa la última copia de incidencias e indica su antigüedad.

### Tests
- Motor: parseo de DATEX II (fixtures reales anonimizadas), proximidad a radar en la ruta y en sentido de la marcha.
- Robolectric: refresco con fuente falsa (éxito, error, sin red).

### Estado
pendiente · Depende de: H1, H4 · Dudas: resueltas (2026-10-28)

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
- [ ] Repetir la misma traza a dos velocidades da delta negativo en la rápida, sector a sector.
- [ ] Una traza que entra a mitad de un tramo guardado y lo recorre entero se detecta y se compara.

### Tests
- Motor (TDD): cronometraje con reloj inyectado, cortes de sector, delta interpolado por distancia, detección de tramo (inicio, fin, desvío).
- Room: migraciones con tests.

### Estado
pendiente · Depende de: H2 · Dudas: resueltas (2026-10-28)

## H7 — Análisis post-tramo y perfil del piloto

### Objetivo
Analizar cada tramo y mostrar la evolución del piloto.

### Alcance
- Tiempo total y por sector/microsector, delta contra el mejor, velocidad media y máxima por sector.
- Gráfico velocidad/distancia, mapa coloreado por velocidad, repetición del recorrido sobre el mapa.
- Perfil: kilómetros, número de tramos, mejores tiempos, evolución, velocidades medias e historial de análisis.
- Fuera: exportar o compartir.

### Criterios de aceptación
- [ ] El análisis de un tramo grabado con `gps-replay` coincide con los tiempos del cronometraje en vivo (±0,1 s).
- [ ] El perfil agrega correctamente varios tramos de prueba.

### Tests
- Motor: estadísticas por sector, agregados del perfil.
- Robolectric: pantallas con datos falsos (BancoCompose).

### Estado
pendiente · Depende de: H6

## H8 — Edición de notas y reconocimiento

### Objetivo
Corregir notas a mano o por voz y hacer pasadas de reconocimiento.

### Alcance
- Edición tocando la nota sobre el mapa; edición por voz (reconocimiento offline) activada con botón (en pantalla o del volante) y palabra de activación opcional.
- Notas editadas ligadas al tramo; siempre prevalecen sobre las automáticas.
- Modo reconocimiento: pasada para dictar o corregir notas; recalibra el perfil de velocidad de Joan en ese tramo.
- Edición de sectores arrastrando sobre el mapa.
- Fuera: compartir notas.

### Criterios de aceptación
- [ ] Una nota editada sobrevive a regenerar las automáticas del tramo.
- [ ] Dictar «derecha tres larga» en reconocimiento crea esa nota en la posición actual.

### Tests
- Motor: fusión de notas editadas con automáticas; intérprete de órdenes de voz (gramática del glosario).

### Estado
pendiente · Depende de: H3, H6 · Dudas: resueltas (2026-10-28)

## H9 — Precarga de rutas y satélite de alta calidad

### Objetivo
Preparar tramos antes de conducirlos y descargar satélite de más detalle donde importa.

### Alcance
- Importar GPX/KML, dibujar rutas en el mapa y reutilizar rutas recorridas.
- Satélite z17–18 automático en un corredor de ~300 m de rutas planificadas, recorridas y tramos guardados.
- Descarga manual de una zona a z18–19.
- Fuera: exportar.

### Criterios de aceptación
- [ ] Un GPX importado genera tramo, notas y descarga de corredor.
- [ ] El corredor descargado se ve sin red a z18 alrededor de la ruta y no fuera de ella.

### Tests
- Motor: parseo GPX/KML (variantes reales), cálculo de teselas de un corredor.

### Estado
pendiente · Depende de: H1, H6

## H10 — Interfaz completa estilo WRC

### Objetivo
Pulir toda la app con estética de videojuego WRC y retransmisión de rally.

### Alcance
- Menú de inicio tipo juego (Conducir · Mis tramos · Reconocimiento · Mapas offline · Perfil del piloto · Ajustes).
- Pantalla de conducción con el mapa como protagonista y modo alternativo centrado en notas grandes.
- Iconos de notas tipo roadbook, banners animados, HUD.
- Ajustes completos: voz, modo del copiloto, anticipación, notas simplificadas, grabación automática, mapas, tema (oscuro por defecto, claro opcional).
- Fuera: Android Auto.

### Criterios de aceptación
- [ ] Revisión de `ux-reviewer` sin hallazgos altos; `ContrastTest` en verde en ambos temas.
- [ ] Capturas de cada pantalla en vertical y horizontal.

### Tests
- Robolectric: render de pantallas con BancoCompose; contraste.

### Estado
pendiente · Depende de: H1–H9 · Dudas: resueltas (seguir la dirección de H0)

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
pendiente · Depende de: H4 y el material de Joan · Dudas: resueltas (2026-10-28)

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
- [ ] Versión publicada en GitHub e instalada con Obtainium en el móvil de Joan.
- [ ] (Pospuesto) Versión en la pista interna de Play y promocionada a la prueba cerrada.

### Tests
- E2E: navegación + notas + voz + cronometraje + análisis sobre las trazas de `docs/rutas/`.

### Estado
pendiente · Depende de: H1–H11

## Cambios respecto a la propuesta inicial
- **H1** deja el corredor z17–18 y la zona manual z18–19 para **H9**, porque dependen de rutas y tramos guardados.
- **H3** incluye una presentación provisional en texto para validar las notas antes de la voz.
- **H8** incorpora la edición de sectores arrastrando sobre el mapa (pedida en §4.7).
- **H11** puede empezar en paralelo en el PC (pipeline de `voice-lab/`) en cuanto Joan aporte material, aunque la integración en la app espere a H4.
