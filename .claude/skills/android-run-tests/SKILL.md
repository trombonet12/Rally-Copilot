---
name: android-run-tests
description: Ejecutar tests de Rally Copilot y leer reportes: app (JVM con Robolectric/Compose), motor (dominio puro) o all; no compila (android-build) ni escribe tests (test-write).
argument-hint: "[app|motor|all]"
allowed-tools: Read, Grep, Glob, Bash(./gradlew *), Bash(ls *)
---

# android-run-tests

Ejecuta los tests de `$ARGUMENTS` (por defecto `all`), lee los reportes y resume los fallos. Todos los tests son JVM (JUnit4, coroutines-test, Robolectric, fakes a mano); los end-to-end en emulador llegan en H12.

## Cuándo usarla
- Validar un cambio en `:app` o `:motor`.
- Releer el reporte de una ejecución previa.

## Cuándo NO usarla
- Escribir o ampliar tests → `test-write`.
- Solo compilar → `android-build`; ktlint/detekt/lint → `android-static-analysis`.
- Un test falla y hay que diagnosticar → `android-debug-failure`.

## Precondiciones
- Gradle desde la raíz, JDK 21 y Android SDK (CLAUDE.md § Comandos).
- TDD en lógica crítica: notas, cronometraje, detección de tramos, deltas, parsing, migraciones Room.

## Pasos
1. Resolver `$ARGUMENTS`; si falta, `all`; si es otro valor, pedir aclaración y parar.
2. Ejecutar:

| Valor | Comando | Notas |
|-------|---------|-------|
| `app` | `./gradlew :app:testDebugUnitTest` | Un caso: `--tests "app.trombito.rallycopilot.<Clase>"` |
| `motor` | `./gradlew :motor:test` | Un caso: `--tests "<Clase>"` |
| `all` | `./gradlew :motor:test testDebugUnitTest` | Gate completo en `qa` |

3. Si hay fallos, leer el reporte (ver Validaciones) y listar `Clase.método` + mensaje de aserción. No desactivar ni saltar tests para pasar el gate.
4. Si el fallo no es obvio, pasar el XML/log a un subagente haiku de solo lectura antes de proponer cambios.
5. Informar: ejecutados/fallidos/omitidos, ruta del reporte y siguiente acción.

## Validaciones / tests
- Reportes `:app`: `app/build/reports/tests/testDebugUnitTest/index.html` y XML en `app/build/test-results/testDebugUnitTest/`.
- Reportes `:motor`: `motor/build/reports/tests/test/index.html`. Si difiere, `Glob` `**/build/reports/**`.
- `BUILD SUCCESSFUL` y 0 fallidos.

## Definition of Done
- [ ] Tests del valor pedido ejecutados con resultado reportado.
- [ ] Fallos listados con clase, método y mensaje; ninguno ocultado ni deshabilitado.
- [ ] Ruta del reporte incluida en la respuesta.
- [ ] `./gradlew :motor:test testDebugUnitTest` en verde (o fallo atribuido).

## Docs a actualizar
- Tests inestables o rotos conocidos: registro en docs/ESTADO.md (skill `session-handoff`), nunca skip silencioso.
