## Resumen

<!-- Qué cambia y por qué, en 1-3 frases. -->

## Tipo (Conventional Commits)

- [ ] feat
- [ ] fix
- [ ] refactor
- [ ] perf
- [ ] test
- [ ] docs
- [ ] build / ci
- [ ] chore

## Hito

<!-- Ej.: H3 · Motor de notas (ver ROADMAP.md y docs/ESTADO.md). No implementar fuera del hito activo. -->

## Checklist (Definition of Done)

- [ ] Gate Gradle en verde: `./gradlew ktlintCheck detekt lintDebug testDebugUnitTest :motor:test assembleDebug :app:minifyReleaseWithR8`
- [ ] Tests añadidos o actualizados (TDD en notas, cronometraje, detección de tramos, deltas, parseo de datos, migraciones)
- [ ] Sin secretos, `.env*` ni keystores en el diff
- [ ] `docs/ESTADO.md` y `ROADMAP.md` actualizados
- [ ] Decisión registrada en `docs/DECISIONES.md` si cambia la arquitectura; dudas de producto en `QUESTIONS.md`
- [ ] Sin tests desactivados ni TODOs sin registro en ESTADO.md

## Cómo se probó

<!-- Comandos ejecutados y resultado, pruebas en emulador (gps-replay) o en el móvil, capturas si hay UI. -->
