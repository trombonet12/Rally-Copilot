# Estado

> Propósito: estado de la sesión y próxima acción exacta · Audiencia: agentes y Joan · Actualizado: 2026-10-28 · Relacionado: [../ROADMAP.md](../ROADMAP.md), [../QUESTIONS.md](../QUESTIONS.md)

| Campo | Valor |
|---|---|
| Hito | H2 — Navegación offline y mapa a tu gusto |
| Estado | **en curso** (2026-10-28): investigación (cartelería, relieve) ‖ teselas Valhalla en el repo de datos |
| Último commit | ver `git log -1`. **Commits de H1 sin publicar** en `Rally-Copilot` (push solo con permiso de Joan); `rally-copilot-data` publicado |

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
- H1 (2026-10-28): gate verde con 174 tests JVM de app y 82 del motor; `rally-copilot-data`: 5 tests de scripts + actionlint. Emulador `trombito-36` y `rally-sin-gms-36`: descarga real, corte de WiFi y reanudación, modo avión, Ma-2141 a 50 km/h con límite 90/30/sin dato, satélite, horizontal y spike `mbtiles://`.

## Problemas conocidos
- El icono adaptativo debe quedarse en `mipmap-anydpi-v26`: con `mipmap-anydpi` el release no enlaza (`@mipmap/ic_launcher` not found).
- `gradlew` debe estar en git como ejecutable (`git update-index --chmod=+x gradlew`); si no, el CI falla con «Permission denied».
- Overpass API da 502/504 a menudo: reintentar con `--max-time` o usar otro espejo.
- En modo desatendido no se puede ejecutar en el PC código de terceros (compilar Planetiler, `npm install`): todo eso va en Actions del repo de datos.
- Git Bash reescribe rutas tipo `/data/local/tmp` en `adb`: usar `MSYS_NO_PATHCONV=1`. En Git Bash no hay `pkill`: parar `gps-replay` con PowerShell (`Stop-Process`).
- `GITHUB_TOKEN` dio 403 al crear la release tras un job de más de 1 h; por eso la publicación va en un job aparte.
- `mbtiles://` en MapLibre solo con ruta absoluta (no `file://`).
- La etiqueta «MAR BALEAR» de Protomaps cae dentro de la isla (dato de Natural Earth); cosmético.
- APK debug de 63 MB por las librerías nativas de MapLibre en 4 ABI; valorar splits por ABI antes de H12.

## Decisiones recientes
- Ver [DECISIONES.md](DECISIONES.md) § Decisiones del usuario, § Base técnica e § Investigaciones previas.

## Dudas abiertas
- [QUESTIONS.md](../QUESTIONS.md): 0 abiertas; las 4 de H1 respondidas el 2026-10-28.

## Pendiente del usuario
- **Copia de seguridad de `~/.rallycopilot/`** (keystore nueva): sin ella no se pueden publicar actualizaciones.
- Instalar la v0.1.11 con Obtainium ([obtainium.md](obtainium.md)).
- Permiso para el push de los commits de H1 a `Rally-Copilot` (dispara el CI; no publica release porque `version.properties` no cambia).
- Ir dejando material de voz en `voice-lab/data/raw/` (no bloquea hasta H11).

## Archivos importantes
`CLAUDE.md` · `ROADMAP.md` · `QUESTIONS.md` · `docs/DECISIONES.md` · `docs/HERENCIA.md` · `docs/MEMORIA.md` · `docs/investigacion/` · `app/build.gradle.kts` · `.github/workflows/android.yml`

## Próxima acción exacta
- **Agente:** H1 está cerrado. Si Joan lo autoriza: push de los commits de H1 a `Rally-Copilot`; implementar las dos respuestas pendientes de H1 (botón rumbo arriba/norte arriba y diálogo al desactivar «Solo con WiFi»; ver DECISIONES.md); después `hito start H2` (Valhalla: añadir las teselas de ruta al workflow de `rally-copilot-data`).
