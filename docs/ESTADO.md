# Estado

> Propósito: estado de la sesión y próxima acción exacta · Audiencia: agentes y Joan · Actualizado: 2026-11-01 · Relacionado: [../ROADMAP.md](../ROADMAP.md), [../QUESTIONS.md](../QUESTIONS.md)

| Campo | Valor |
|---|---|
| Hito | H12 cerrado en versión recortada (2026-11-02) · **v1.0.0** · H11 pendiente |
| Estado | v1.0.0 publicada. Pendientes: E2E en emulador, ux-reviewer de H10, prueba en coche, H11 |
| Último commit | ver `git log -1`. Push a `main` con todo en verde (2026-11-01); v0.3 publicada como `v0.3.1` |

## Completado
- 2026-10-28 · Herencia de Sous Chef/Trombito analizada y documentada ([HERENCIA.md](HERENCIA.md)).
- 2026-10-28 · CLAUDE.md, memoria del proyecto ([MEMORIA.md](MEMORIA.md) + skill `recordar`), memoria personal (presupuesto de subagentes, push con permiso, sin coautoría).
- 2026-10-28 · `.claude/`: settings (push en `ask`), hooks de secretos y lint con 88 pruebas, 40 skills (37 adaptadas + `hito`, `registrar-decision`, `recordar`, `gps-replay`), 18 agentes (+ `copiloto-reviewer`).
- 2026-10-28 · Esqueleto Android `:app` + `:motor`, tema oscuro, inicio provisional tipo menú de juego; CI (gate + R8, release firmada, GitHub Release, Play interna → prueba cerrada).
- 2026-10-28 · Logo: Trombito con casco jet rojo y micrófono de intercomunicador (segunda versión; la primera, blanca con visera, la rechazó Joan). Icono adaptativo, monocromo, de notificación y gráficos de Play.
- 2026-10-28 · Investigaciones de §8 en [investigacion/](investigacion/) y resumen en [DECISIONES.md](DECISIONES.md); 16 preguntas en [QUESTIONS.md](../QUESTIONS.md).
- 2026-10-28 · Respuestas de Joan a las 18 preguntas registradas en DECISIONES.md, QUESTIONS.md y ROADMAP.md. Casco más cuadrado. Licencia GPL-3.0 + README. Distribución por GitHub Release + Obtainium; Play pospuesto (`PLAY_HABILITADO`). `scripts/svg-a-vector.py` para regenerar los iconos.
- 2026-10-28 · Keystore nueva en `~/.rallycopilot/`, secretos de firma en GitHub, push y repo público (con permiso de Joan). Primera release **v0.1.11** publicada por `workflow_dispatch` (APK + AAB + checksums), lista para Obtainium.
- 2026-10-28 · `scripts/gps-replay.mjs` + traza real de la Ma-2141 ([rutas/](rutas/)), verificado en el emulador; `voice-lab/README.md` con dónde dejar el material de voz.
- 2026-10-28 · **H1**: PRs #1 y #2 de Dependabot fusionados (con permiso). Repo público [`trombonet12/rally-copilot-data`](https://github.com/trombonet12/rally-copilot-data) con workflow mensual (decidir → build sin token → publicar) y release `datos-2026.10.28-2`. Motor: manifest, plan de descarga, vías/límites, velocidad. App: MapLibre 13.6.1, Room, WorkManager, descarga reanudable verificada, GPS sin GMS, mapa vectorial/satélite, velocidad y límite, Mapas offline, Ajustes/Acerca de. Revisión de seguridad aplicada. Capturas en [capturas/h1/](capturas/h1/).

- 2026-11-02 · **H10**: Ajustes con vista previa, hoja de copiloto, tema claro con mapa claro, notas grandes, menú simplificado, Room v12. Capturas en [capturas/h10/](capturas/h10/).
- 2026-11-02 · **H9**: importar GPX/KML, dibujar rutas, satélite z18 en corredor y zona manual z18–19.
- 2026-11-02 · **H8**: órdenes de voz offline (Vosk con gramática), notas editadas que prevalecen, edición en el mapa y en el tramo, reconocimiento, sectores arrastrables (Room v9–v10).
- 2026-11-02 · **H7**: análisis de cada pasada (sectores, gráficos, mapa coloreado, repetición), Mis tramos, perfil del piloto, Room v8. Capturas en [capturas/h7/](capturas/h7/).
- 2026-11-01 · **H6**: tramos y cronometraje (motor tramos/, Room v7, delta por voz en Completo, HUD de retransmisión, tarjeta de meta); «Ir aquí» y búsqueda durante el guiado. Capturas en [capturas/h6/](capturas/h6/).
- 2026-11-01 · **H5B**: gestos de girar/inclinar, buscador por palabras (índice `datos-2026.11.01-10`), mantener pulsado con Ir aquí / Añadir parada, rutas con paradas (Room v6), panel compacto parado. Capturas en [capturas/h5b/](capturas/h5b/).
- 2026-11-01 · **H5**: radares DGT + OSM (fijos, tramo, zonas móviles) dentro del APK, incidencias DATEX II de la DGT con caché y antigüedad, alertas por voz y banner animado, exceso +10 % como pastilla roja, media de tramo, incidencias en el mapa; revisión de seguridad aplicada. Capturas en [capturas/h5/](capturas/h5/).
- 2026-11-01 · **H4**: el copiloto habla (sherpa-onnx 1.13.8, voces Piper int8 davefx/sharvard, frases sintetizadas por adelantado y cacheadas, ducking), cuatro modos, voz elegida en el primer uso, Room v5. Peticiones de Joan: ubicación bajo techo, todo menos el satélite dentro del APK (siembra), satélite z17 (`datos-2026.11.01-8`), flecha y rumbo pegados a la carretera. Capturas en [capturas/h4/](capturas/h4/).
- 2026-10-31 · **H3**: motor de notas completo y calibrado con `copiloto-reviewer`, `vias-notas` (datos-2026.10.31-6), notas en vivo en guiado y libre, franja de notas, ajustes de anticipación y simplificadas (Room v4), «Seguir a», pantalla de conducción rediseñada (mapa inclinado, botones abajo, horizontal compacto). Capturas en [capturas/h3/](capturas/h3/).
- 2026-10-31 · **H2**: navegación offline completa (Valhalla en el dispositivo, búsqueda, coordenadas y enlaces —los cortos con red—, Más rápida/Sinuosa, banner WRC con 3 variantes elegibles —B por defecto—, pie con llegada/tiempo/km, recálculo y sentido contrario, modo libre automático que graba el recorrido, servicio en primer plano) y mapa a gusto de Joan (menú de capas con iconos, relieve y curvas MDT05, zoom por velocidad, brújula, 4 iconos de posición, placas por matrícula en mapa y panel, pantalla encendida). Icono de la app centrado. Room v3. Revisión opus aplicada. Datos `datos-2026.10.31-5`. Capturas en [capturas/h2/](capturas/h2/).
## Entorno
| Pieza | Valor |
|---|---|
| JDK | 21 (`C:/Program Files/Microsoft/jdk-21.0.12.101-hotspot`, vía `~/.gradle/gradle.properties`); el `java` del PATH es 1.8 |
| Android SDK | `%LOCALAPPDATA%/Android/Sdk`; `local.properties` con `python -I scripts/preparar-local-properties.py` |
| Emulador | AVD `trombito-36` (compartido con los hermanos) y `rally-sin-gms-36` (`android-36/default`, sin GMS, puerto 5556) |
| Otros | Node ≥ 22, Python 3.12 (su almacén de certificados está caducado: usar `curl` para descargas), Chrome para `ficha-play.mjs`, actionlint vía WinGet |

## Tests
- Gate: `./gradlew ktlintCheck detekt lintDebug testDebugUnitTest :motor:test assembleDebug :app:minifyReleaseWithR8` → verde (20 tests JVM; lint solo con avisos de versiones, `ObsoleteSdkInt` y `ic_stat_rally` sin uso hasta H2).
- `assembleRelease` sin firma → falla con «Release sin firma: faltan las variables RALLY_*» (esperado).
- Scripts: `check-claude-assets`, `gen-claude-index --check`, `check-docs`, `test-hooks` (88 OK), `node --test` de `play-promocionar` (11) y `gps-replay` (8) → verde. `actionlint` sin errores.
- Emulador: APK debug instalado; inicio, icono y splash verificados con capturas; `gps-replay` fija la posición `gps` en la Ma-2141.
- H4 (2026-11-01): gate verde (≈ 650 tests JVM de app y > 600 del motor); emulador `trombito-36`: instalación limpia en modo avión con datos sembrados, voz real con sherpa (≈ 2,6 s la primera síntesis), Ma-2141 a 60 km/h con notas a 83–100 m, mapa girando con la carretera.
- H3 (2026-10-31): gate verde con 519 tests JVM de app y 463 del motor; scripts y hooks (88 OK) en verde. Emulador `trombito-36` y `rally-sin-gms-36`: guiado y libre con notas, Seguir a tras `force-stop`, horizontal, ajustes.
- H2 (2026-10-31): gate verde con 398 tests JVM de app y 233 del motor; scripts y hooks (88 OK) en verde. Emulador `trombito-36` y `rally-sin-gms-36`: rutas, búsqueda, guiado por la Ma-2141, pantalla apagada 6 min, capas, iconos, placas, galería WRC, horizontal.
- H1 (2026-10-28): gate verde con 174 tests JVM de app y 82 del motor; `rally-copilot-data`: 5 tests de scripts + actionlint. Emulador `trombito-36` y `rally-sin-gms-36`: descarga real, corte de WiFi y reanudación, modo avión, Ma-2141 a 50 km/h con límite 90/30/sin dato, satélite, horizontal y spike `mbtiles://`.

## Problemas conocidos
- Windows MAX_PATH (260): Robolectric crea bases en directorios temporales con el nombre del test; nombres de base largos dan SQLITE_CANTOPEN intermitente. Usar nombres cortos (`m.db`).
- `scripts/test-hooks.sh` falló una vez (87/88) y pasó al repetir: intermitente, probablemente por lentitud; vigilar.
- El icono adaptativo debe quedarse en `mipmap-anydpi-v26`: con `mipmap-anydpi` el release no enlaza (`@mipmap/ic_launcher` not found).
- `gradlew` debe estar en git como ejecutable (`git update-index --chmod=+x gradlew`); si no, el CI falla con «Permission denied».
- Overpass API da 502/504 a menudo: reintentar con `--max-time` o usar otro espejo.
- En modo desatendido no se puede ejecutar en el PC código de terceros (compilar Planetiler, `npm install`): todo eso va en Actions del repo de datos.
- Git Bash reescribe rutas tipo `/data/local/tmp` en `adb`: usar `MSYS_NO_PATHCONV=1`. En Git Bash no hay `pkill`: parar `gps-replay` con PowerShell (`Stop-Process`).
- `GITHUB_TOKEN` dio 403 al crear la release tras un job de más de 1 h; por eso la publicación va en un job aparte.
- `mbtiles://` en MapLibre solo con ruta absoluta (no `file://`).
- La etiqueta «MAR BALEAR» de Protomaps cae dentro de la isla (dato de Natural Earth); cosmético.
- APK de ≈ 300 MB (datos ≈ 145 MB y voces ≈ 59 MB en `assets`, onnxruntime) con `abiFilters` arm64-v8a + x86_64. Los tests Robolectric empaquetan los assets (≈ 25 s cuando cambian).
- Un solo Gradle a la vez en el árbol: dos agentes en paralelo rompen cachés (KSP, resultados binarios de tests).
- El GPS del emulador da rumbo 0 con ±30°: la app lo ignora (precisión > 20°) y usa el del movimiento.

- `gps-replay`: al acabar la traza el emulador sigue repitiendo la última posición con velocidad; para simular GPS perdido, `adb shell cmd location set-location-enabled false`.
- `adb shell input text` no teclea acentos (buscar «Soller»).
- Franja de notas: en las tarjetas pequeñas el texto largo se recorta («DERECHA 3 MU…»); dirección y grado se conservan.
- `Log.w` se conserva en release: no registrar nunca coordenadas ni URLs (solo el tipo de excepción).
## Decisiones recientes
- Ver [DECISIONES.md](DECISIONES.md) § Decisiones del usuario, § Base técnica e § Investigaciones previas.

## Dudas abiertas
- [QUESTIONS.md](../QUESTIONS.md): 0 abiertas; las del cierre de H3 y del arranque de H4 respondidas el 2026-11-01.

## Pendiente del usuario
- **Copia de seguridad de `~/.rallycopilot/`** (keystore): sin ella no se pueden publicar actualizaciones.
- Instalar la v0.4 con Obtainium (≈ 300 MB), elegir la voz, descargar el satélite (≈ 1,5 GB, WiFi) y probar en el coche: voz, ducking con música, ubicación bajo techo, flecha sobre la carretera y probar en el móvil con GPS real (velocidad, límite, guiado, modo libre).
- Pasarme un enlace corto real de Google Maps (`maps.app.goo.gl/…`) para probar el caso de éxito.
- Ir dejando material de voz en `voice-lab/data/raw/` (no bloquea hasta H11).

## Archivos importantes
`CLAUDE.md` · `ROADMAP.md` · `QUESTIONS.md` · `docs/DECISIONES.md` · `docs/HERENCIA.md` · `docs/MEMORIA.md` · `docs/investigacion/` · `app/build.gradle.kts` · `.github/workflows/android.yml`

## Próxima acción exacta
- **Agente:** v1.0 publicada. Siguiente (cuando haya límite de uso): batería E2E instrumentada con las trazas de `docs/rutas/`, revisión `ux-reviewer` de H10 y lo que salga de la prueba en coche de Joan. H11 cuando haya material en `voice-lab/data/raw/`.
