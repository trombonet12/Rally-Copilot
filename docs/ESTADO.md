# Estado

> Propósito: estado de la sesión y próxima acción exacta · Audiencia: agentes y Joan · Actualizado: 2026-10-28 · Relacionado: [../ROADMAP.md](../ROADMAP.md), [../QUESTIONS.md](../QUESTIONS.md)

| Campo | Valor |
|---|---|
| Hito | H0 — Preparación |
| Estado | **completado**; esperando respuestas de Joan a QUESTIONS.md antes de H1 |
| Último commit | ver `git log -1`; todos los commits son locales, **sin push** (el push lo autoriza Joan) |

## Completado
- 2026-10-28 · Herencia de Sous Chef/Trombito analizada y documentada ([HERENCIA.md](HERENCIA.md)).
- 2026-10-28 · CLAUDE.md, memoria del proyecto ([MEMORIA.md](MEMORIA.md) + skill `recordar`), memoria personal (presupuesto de subagentes, push con permiso, sin coautoría).
- 2026-10-28 · `.claude/`: settings (push en `ask`), hooks de secretos y lint con 88 pruebas, 40 skills (37 adaptadas + `hito`, `registrar-decision`, `recordar`, `gps-replay`), 18 agentes (+ `copiloto-reviewer`).
- 2026-10-28 · Esqueleto Android `:app` + `:motor`, tema oscuro, inicio provisional tipo menú de juego; CI (gate + R8, release firmada, GitHub Release, Play interna → prueba cerrada).
- 2026-10-28 · Logo: Trombito con casco jet rojo y micrófono de intercomunicador (segunda versión; la primera, blanca con visera, la rechazó Joan). Icono adaptativo, monocromo, de notificación y gráficos de Play.
- 2026-10-28 · Investigaciones de §8 en [investigacion/](investigacion/) y resumen en [DECISIONES.md](DECISIONES.md); 16 preguntas en [QUESTIONS.md](../QUESTIONS.md).
- 2026-10-28 · `scripts/gps-replay.mjs` + traza real de la Ma-2141 ([rutas/](rutas/)), verificado en el emulador; `voice-lab/README.md` con dónde dejar el material de voz.

## Entorno
| Pieza | Valor |
|---|---|
| JDK | 21 (`C:/Program Files/Microsoft/jdk-21.0.12.101-hotspot`, vía `~/.gradle/gradle.properties`); el `java` del PATH es 1.8 |
| Android SDK | `%LOCALAPPDATA%/Android/Sdk`; `local.properties` con `python -I scripts/preparar-local-properties.py` |
| Emulador | AVD `trombito-36` (compartido con los hermanos) |
| Otros | Node ≥ 22, Python 3.12 (su almacén de certificados está caducado: usar `curl` para descargas), Chrome para `ficha-play.mjs`, actionlint vía WinGet |

## Tests
- Gate: `./gradlew ktlintCheck detekt lintDebug testDebugUnitTest :motor:test assembleDebug :app:minifyReleaseWithR8` → verde (20 tests JVM; lint solo con avisos de versiones, `ObsoleteSdkInt` y `ic_stat_rally` sin uso hasta H2).
- `assembleRelease` sin firma → falla con «Release sin firma: faltan las variables RALLY_*» (esperado).
- Scripts: `check-claude-assets`, `gen-claude-index --check`, `check-docs`, `test-hooks` (88 OK), `node --test` de `play-promocionar` (11) y `gps-replay` (8) → verde. `actionlint` sin errores.
- Emulador: APK debug instalado; inicio, icono y splash verificados con capturas; `gps-replay` fija la posición `gps` en la Ma-2141.

## Problemas conocidos
- El icono adaptativo debe quedarse en `mipmap-anydpi-v26`: con `mipmap-anydpi` el release no enlaza (`@mipmap/ic_launcher` not found).
- Overpass API da 502/504 a menudo: reintentar con `--max-time` o usar otro espejo.

## Decisiones recientes
- Ver [DECISIONES.md](DECISIONES.md) § Decisiones del usuario, § Base técnica e § Investigaciones previas.

## Dudas abiertas
- [QUESTIONS.md](../QUESTIONS.md): 16 abiertas. Bloquean H1: «Alojamiento de los datos de Mallorca» y «Fuente del satélite y tamaño».

## Pendiente del usuario
- Responder QUESTIONS.md (al menos las de H1–H2).
- Pasos de [pasos-manuales.md](pasos-manuales.md) cuando quiera la primera release (keystore, secretos, Play Console). Decidir cuándo hacer push.
- Ir dejando material de voz en `voice-lab/data/raw/` (no bloquea hasta H11).

## Archivos importantes
`CLAUDE.md` · `ROADMAP.md` · `QUESTIONS.md` · `docs/DECISIONES.md` · `docs/HERENCIA.md` · `docs/MEMORIA.md` · `docs/investigacion/` · `app/build.gradle.kts` · `.github/workflows/android.yml`

## Próxima acción exacta
- **Joan:** responder en QUESTIONS.md (o en el chat) al menos «Alojamiento de los datos de Mallorca» y «Fuente del satélite y tamaño».
- **Agente:** con esas respuestas, `hito start H1` (skill `hito`): pasar las respuestas a DECISIONES.md, añadir MapLibre con `dependency-add`, y hacer el spike de `mbtiles://` y `pmtiles://` en MapLibre Android con un PMTiles pequeño de Mallorca. Éxito: el mapa vectorial se ve en el emulador en modo avión.
