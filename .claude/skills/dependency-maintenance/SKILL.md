---
name: dependency-maintenance
description: Mantener dependencias existentes según [upgrade|remove|unused|license]: actualizar, quitar, detectar sin uso o inventariar licencias; para añadir nuevas usa dependency-add.
argument-hint: "[upgrade|remove|unused|license]"
allowed-tools: Read, Grep, Glob, Edit, Bash(./gradlew *), Bash(gh pr list *)
---

# dependency-maintenance

Mantenimiento de dependencias ya presentes en `gradle/libs.versions.toml`. Operación: `$ARGUMENTS` (por defecto `upgrade`; si no es uno de los cuatro, pedirlo y parar).

## Cuándo usarla
- Hay PRs de Dependabot que revisar, una dependencia que quitar o sospechosa de sobrar, o hace falta un inventario de licencias.

## Cuándo NO usarla
- Añadir una dependencia nueva → usa `dependency-add`.
- Cambio de versión mayor sin plan aprobado → parar (ver Pasos).
- Fallos tras actualizar que no se entienden → usa `bugfix-flow`.

## Precondiciones
- Árbol limpio y gate qa fast en verde antes de empezar.
- `gradle/libs.versions.toml` es reservado del agente principal: un subagente propone el cambio.
- Dependabot, si está configurado en `.github/dependabot.yml`: minor+patch agrupados, majors individuales.

## Pasos
**upgrade**
1. Listar candidatos: PRs de Dependabot (`gh pr list --search "chore(deps)"`) o revisión manual del catálogo.
2. Por cada actualización, leer changelog/release notes y buscar breaking changes; lo no verificable → `VERIFICAR`.
3. **Nunca aplicar majors sin plan**: documentarlo (`registrar-decision`) y esperar aprobación. Solo minor/patch se aplican directamente.
4. Respetar `docs/DECISIONES.md` (versiones fijadas) y la antigüedad ≥ 2 semanas; versiones exactas en el catálogo.
5. Validar con el gate qa full (abajo).

**remove**
1. Confirmar con `Grep` que no hay usos (imports, `libs.*`, `implementation(...)`, plugins) y que ninguna otra dependencia lo requiere.
2. Quitar la entrada del Version Catalog y sus referencias.
3. Validar con el gate qa fast.

**unused**
1. Por módulo, listar dependencias declaradas y buscar sus imports con `Grep`.
2. Descartar falsos positivos: plugins de build, KSP/KAPT, procesadores de anotaciones, runtime-only.
3. Devolver findings `SEVERIDAD | archivo:línea | problema | corrección propuesta`; quitar solo mediante `remove` y con confirmación.

**license**
1. Inventario desde el Version Catalog; licencias no verificables → `VERIFICAR`.
2. Marcar copyleft, desconocidas o sin licencia como findings; no cambiar nada sin decisión.

## Validaciones / tests
- Gate qa full: `./gradlew ktlintCheck detekt lintDebug testDebugUnitTest :motor:test assembleDebug :app:minifyReleaseWithR8`.
- Tras `upgrade` de Room, Hilt, Kotlin, AGP o librerías nativas (mapas, TTS): también prueba en emulador (`android-install-and-logcat`).

## Definition of Done
- [ ] Ningún major aplicado sin plan aprobado.
- [ ] Changelog/breaking revisado por cada actualización.
- [ ] Versiones exactas en el catálogo.
- [ ] Gate qa full en verde (upgrade) o qa fast (remove).
- [ ] `unused`/`license` entregados como findings, sin cambios no pedidos.

## Docs a actualizar
- `docs/DECISIONES.md` (vía agente principal, reservado) · docs/ESTADO.md (skill `session-handoff`)
