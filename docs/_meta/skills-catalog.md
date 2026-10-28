# Catálogo de skills

Una línea por skill: `nombre | argumentos | contenido mínimo`. Las marcadas **[manual]** llevan
`disable-model-invocation: true` (efectos irreversibles). Fuente para `scripts/gen-claude-index.mjs`.
Skills propuestas para hitos futuros (aún sin SKILL.md): `notas-calibrar` (H3), `datos-mallorca` (H1, según la investigación de datos) y `voice-lab` (H11); se añaden aquí al crearlas.

## 1. Arquitectura y diseño
- registrar-decision | — | Registrar una decisión técnica en docs/DECISIONES.md (y si es grande, investigación en docs/investigacion/); las de producto van a QUESTIONS.md
- design-feature | — | Diseñar una feature antes de implementarla (contratos, flujo de datos, impacto, plan de lotes)
- architecture-review | [boundaries|data-flow|domain|concurrency] | Revisar arquitectura sin tocar código, por foco, con findings por severidad

## 2. Android — crear
- android-create-feature | [nombre_feature] | Crear una feature Android de UI (screen Compose, ViewModel con UiState/StateFlow, ruta type-safe en AppNavHost, tests JVM)
- android-create-data-layer | [nombre_entidad] | Crear la capa de datos Android de una entidad (entity Room, DAO, repository, mapper y tests JVM)
- android-create-room-migration | — | Crear una migración Room manual (versión N a N+1) con schema JSON versionado y test Robolectric JVM
- android-create-hilt-module | — | Crear un módulo Hilt en di/*Module.kt con scopes y bindings de interfaces de dominio, y su reemplazo con fakes en tests
- android-add-permission | — | Añadir un permiso Android (manifest, petición runtime, rationale, denegación permanente) con test y mínimo privilegio
- android-create-system-component | [location-service|notification|boot-receiver] | Crear piezas de sistema: foreground service de ubicación, canal de notificación o receiver de arranque; sin Google Play Services

## 3. Android — operar y revisar
- android-build | [debug|release|bundle|motor] | Compilar con Gradle y verificar el artefacto: debug (APK), release (APK firmado), bundle (AAB) o motor (:motor)
- android-run-tests | [app|motor|all] | Ejecutar tests y leer reportes: app (JVM con Robolectric/Compose), motor (dominio puro) o all
- android-static-analysis | — | Pasar ktlintCheck, detekt y Android Lint (lintDebug) y resumir hallazgos
- android-install-and-logcat | — | Arrancar el emulador trombito-36, instalar el APK debug, lanzar la app, leer logcat filtrado y capturar pantalla
- gps-replay | [archivo] [--velocidad x2|--kmh 60] | Reproducir una ruta GPX/KML real de Mallorca en el emulador (adb emu geo fix) para probar navegación y notas
- android-debug-failure | [build|crash] | Diagnosticar un fallo Android, build (Gradle) o crash (logcat): análisis de log, hipótesis y reproducción mínima
- android-profile | [cpu|memory|battery] | Medir CPU, memoria o batería antes de optimizar y registrar métricas antes/después
- android-review | [lifecycle|background|permissions|location|audio] | Revisar Android sin modificar con findings por severidad

## 4. Calidad y tests
- test-write | [motor|unit|db|android-ui] | Escribir tests por tipo con TDD en lógica crítica (notas, cronometraje, tramos, deltas, parsing)
- test-create-double | [fixture|mock|fake] | Crear dobles deterministas reutilizables (fixture, mock o fake) sin azar, tiempo ni GPS reales
- test-debug-flaky | — | Diagnosticar y corregir un test intermitente (aislar, repetir, hallar causa raíz)
- qa | [fast|full] | Ejecutar la puerta de calidad en modo fast o full e interpretar el resultado
- qa-scenario | [process-death|apk-update|screen-off|background|gps-loss|doze|permissions|rotation|dark-theme] | Guion manual en Android por escenario
- bugfix-flow | [descripción-del-bug] | Corregir un bug: reproducir, test que falla, arreglo, regresión y causa raíz documentada

## 5. Seguridad
- security-review | [secrets|android-permissions|components|location|privacy|logging|ci] | Revisar seguridad de un área con findings por severidad, sin editar
- security-secret-scan | — | Buscar secretos (claves, JWT, keystores, cuentas de servicio, contraseñas) en árbol e historial sin mostrar valores

## 6. Git, release y dependencias
- git-check | [working-tree|generated|large-files] | Auditar el árbol git (solo lectura): cambios sin commitear, generados rastreados, archivos grandes
- git-commit | — | Revisar y crear un commit local Conventional Commits en español tras el gate qa fast y secret scan; sin coautoría; push solo con permiso **[manual]**
- git-prepare-pr | — | Redactar la descripción de un PR (resumen, tipo, hito, DoD, pruebas) para pegar o revisar
- release | [local|github] | Publicar una versión menor (versionMinor + push con permiso) o un parche (dispatch): CI, GitHub Release y Play **[manual]**
- release-preflight | — | Checklist obligatorio antes de publicar: gate qa full, secret scan, migraciones, versión/notas y firma
- release-notes | — | Redactar notas de versión desde Conventional Commits para CHANGELOG, GitHub Release o Google Play
- dependency-add | — | Evaluar y añadir una dependencia nueva (Gradle/Version Catalog) con versión exacta
- dependency-maintenance | [upgrade|remove|unused|license] | Mantener dependencias existentes: actualizar, quitar, detectar sin uso o inventariar licencias

## 7. Sesión, hitos, memoria y documentación
- session-start | — | Al empezar una sesión: leer ESTADO.md, MEMORIA.md, hito activo de ROADMAP.md y QUESTIONS.md, ver git y verificar el entorno
- session-handoff | [normal|compact] | Actualizar docs/ESTADO.md al cerrar tarea o sesión [normal|compact]
- recordar | [lo que hay que recordar] | Guardar en docs/MEMORIA.md lo que el usuario pide recordar entre sesiones
- hito | [start|plan|split|complete] [Hn] | Gestionar un hito de ROADMAP.md (start | plan = lotes | split = sub-hitos | complete = gates y cierre)
- delegate-task | — | Generar un brief para un subagente con BRIEF.template.md y comprobar límites y presupuesto de subagentes (≤ 1 punto)
- docs-update | [architecture|data-model|testing|ci|voz|runbook] | Actualizar el doc afectado según DOC.template.md
- docs-check | [links|consistency] | Revisar docs/** y los .md de la raíz (enlaces/anclas rotos, coherencia con DECISIONES.md, VERIFICAR)
