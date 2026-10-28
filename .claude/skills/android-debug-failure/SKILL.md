---
name: android-debug-failure
description: Diagnosticar un fallo Android de Rally Copilot, build (Gradle) o crash (logcat): análisis de log, hipótesis y reproducción mínima; si el bug se confirma pasa a bugfix-flow, sin corregir aquí.
argument-hint: "[build|crash]"
allowed-tools: Read, Grep, Glob, Bash(adb *), Bash(./gradlew *)
---

# android-debug-failure

Diagnostica `$ARGUMENTS` (`build` o `crash`; por defecto `build`). Produce causa aparente, hipótesis y reproducción mínima; no corrige.

## Cuándo usarla
- `build`: falla `./gradlew` (compilación, ktlint, detekt, lint o tests).
- `crash`: la app se cierra, ANR o excepción en el emulador/dispositivo.

## Cuándo NO usarla
- Bug ya reproducido con test rojo y causa conocida → `bugfix-flow`.
- Solo leer logs de la app funcionando → `android-install-and-logcat`.
- Lentitud o consumo sin fallo → `android-profile`.

## Precondiciones
- Fallo reciente y reproducible (o su log). Si `$ARGUMENTS` falta: `build` si hay log de Gradle, `crash` si hay stack de logcat; si no, preguntar.
- `crash`: `adb` con un dispositivo (ver `android-install-and-logcat`).
- Logs a un archivo temporal fuera del repo; nunca versionarlos.

## Pasos
1. Capturar el log:
   - `build`: `./gradlew <tarea> --stacktrace > <log-temporal> 2>&1` (la tarea que falló).
   - `crash`: `adb logcat -d -b crash -v threadtime > <log-temporal>`; con ANR, añadir `adb logcat -d -v threadtime *:E`.
2. Primera pasada con un subagente haiku de solo lectura sobre ese archivo: error, archivo, línea, stack (≤ 5 líneas) y errores en cascada. No pedirle arreglos.
3. Abrir el `archivo:línea` citado y verificar que existe y coincide.
4. 1-3 hipótesis por probabilidad, cada una con evidencia (línea de log o código) y cómo refutarla. Si el log no basta: `no determinable`. Si es comportamiento de AGP/Gradle/Compose no verificable, consultar la documentación oficial.
5. Reproducción mínima: el comando más corto (una tarea con `--tests "<Clase>"` o los pasos en la app). Ejecutarla una vez.
6. Decisión:
   - Reproducido y causa clara → `bugfix-flow` con el resumen (test rojo primero).
   - No reproducible → documentar lo intentado y pedir datos; no tocar código.
7. Redactar antes de compartir: sin tokens, emails ni coordenadas reales del usuario.

## Validaciones / tests
- Reproducción: `./gradlew <tarea>` (build) o pasos + `adb logcat -d -b crash` (crash) muestra el mismo error.
- Cada `archivo:línea` del informe existe en el repo.

## Definition of Done
- [ ] Causa aparente con `archivo:línea` verificada contra el código.
- [ ] Hipótesis con evidencia y forma de refutarlas.
- [ ] Reproducción mínima ejecutada (o `no reproducible` justificado).
- [ ] Derivado a `bugfix-flow` si el bug está confirmado; ningún código modificado aquí.
- [ ] Logs compartidos sin datos sensibles.

## Docs a actualizar
- Hallazgo abierto en docs/ESTADO.md (skill `session-handoff`)
