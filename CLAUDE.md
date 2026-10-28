# Rally Copilot

## Producto
App Android de navegación cuyo núcleo es un **copiloto de rally por voz**: lee la carretera con notas de pacenote
(escala 1–6, 1 la más cerrada) y convierte cada trayecto en un tramo cronometrado con estética de videojuego WRC.
Un usuario (Joan). Solo **Mallorca**, solo **español**, **offline por defecto**, **sin backend** (internet solo para
descargar mapas y refrescar incidencias). Especificación completa: `docs/_meta/prompt-inicial.md`. Plan: `ROADMAP.md`.

## Stack
- Android: Kotlin, Compose/Material 3, Hilt, Navigation type-safe (`app/`, Gradle KTS + Version Catalog). JDK 21.
- `motor/`: Kotlin/JVM puro (geometría, notas, cronometraje, sectores, deltas, GPX/KML). Sin Android, disco ni red.
- Decisiones y versiones: `docs/DECISIONES.md` (+ `docs/investigacion/`). Herencia: `docs/HERENCIA.md`.

## Comandos
```
./gradlew ktlintCheck detekt lintDebug testDebugUnitTest :motor:test assembleDebug :app:minifyReleaseWithR8   # gate
node scripts/check-claude-assets.mjs ; node scripts/gen-claude-index.mjs --check ; node scripts/check-docs.mjs
bash scripts/test-hooks.sh ; node --test scripts/play-promocionar.test.mjs scripts/gps-replay.test.mjs
node scripts/gps-replay.mjs docs/rutas/<traza>.gpx --kmh 60     # GPS simulado en el emulador (AVD trombito-36)
```
JDK 21 vía `~/.gradle/gradle.properties` (`org.gradle.java.installations.paths`). Secretos en `~/.rallycopilot/`
(nunca en el repo). `local.properties`: `python -I scripts/preparar-local-properties.py`.

## Reglas de trabajo
- Antes de trabajar: `docs/ESTADO.md`, el hito activo de `ROADMAP.md` y `docs/MEMORIA.md` (skill session-start).
  Al terminar: ESTADO.md (skill session-handoff). Al cerrar un hito: ROADMAP.md (estado, hecho, pendiente).
- **Modo desatendido**: avanzar hito a hito. Parar solo al cerrar un hito o si no se puede avanzar; entonces presentar
  todas las dudas acumuladas juntas.
- **No decidir producto** (funcionalidad, interfaz, alcance): apuntar en `QUESTIONS.md` con contexto, opciones y
  recomendación, y seguir con lo que no dependa. Lo técnico menor se decide y se registra en `docs/DECISIONES.md`
  (skill registrar-decision).
- Lo que Joan pida recordar («a partir de ahora», «recuerda», correcciones generales) → `docs/MEMORIA.md` (skill recordar).
- TDD en lógica crítica: generación de notas, cronometraje, detección de tramos, deltas, parseo de datos, migraciones.
- Commits locales pequeños y frecuentes: Conventional Commits `tipo(ámbito): resumen` en español. **Sin
  `Co-Authored-By` ni atribución de IA.** `git add` explícito.
- **Sin pedir permiso a Joan** (también push y fusionar PRs; 2026-10-28). Un push a `main` que cambie `version.properties` publica release: hacerlo solo al cerrar un hito. Nunca force-push.
- No añadir dependencias sin la skill dependency-add. No cambiar arquitectura sin registrarlo (registrar-decision).
- No tocar ni imprimir secretos (`~/.rallycopilot/*`, `local.properties`, `.env*`, keystores). Nombres, nunca valores.
- No desactivar tests para pasar el gate. No ignorar errores. TODOs solo con registro en ESTADO.md.
- Respetar las licencias y atribuciones de datos (OSM ODbL, IGN CC BY 4.0, DGT) en la app y en los docs.
- Pruebas en el emulador con trazas reales de Mallorca (`docs/rutas/`, skill gps-replay). Pedir el móvil por USB solo
  cuando la app esté avanzada.

## Delegación
- Subagentes pequeños: 1 objetivo, brief ≤ 40 líneas, ≤ 5 archivos (o un directorio propio), 1 comando de validación,
  respuesta ≤ 15 líneas. Plantilla `docs/_templates/BRIEF.template.md` (skill delegate-task).
- Modelo siempre explícito (nunca omitir `model`): haiku = mecánico · sonnet = ingeniería · opus =
  seguridad/arquitectura/revisión de notas de rally. Si un script puede hacerlo, ningún modelo.
- **Presupuesto simultáneo ≤ 1 punto: opus 1 · sonnet 0,5 · haiku 0,25** (1 opus, o 2 sonnet, o 4 haiku, o
  1 sonnet + 2 haiku). Si se pasa, encadenar. Paralelo solo con archivos disjuntos.
- Solo el agente principal hace commits y push.
- Reservados al agente principal: CLAUDE.md, .claude/settings.json, .claude/hooks/**, docs/_templates/**,
  docs/ESTADO.md, docs/DECISIONES.md, docs/MEMORIA.md, ROADMAP.md, QUESTIONS.md, gradle/libs.versions.toml,
  settings.gradle.kts.
- Procedimientos en `.claude/skills/` (índice `docs/skills.md`); especialistas en `.claude/agents/` (`docs/agentes.md`).
- Si un agente no está cargado: `general-purpose` + «Actúa según .claude/agents/<nombre>.md» con el modelo de su
  frontmatter.

## Memoria del proyecto
@docs/MEMORIA.md
