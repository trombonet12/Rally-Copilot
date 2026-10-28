# Herencia de los proyectos hermanos

> Propósito: qué hereda Rally Copilot de Sous Chef y Trombito, qué se adapta y qué se descarta, y por qué · Audiencia: Joan y agentes · Actualizado: 2026-10-28 · Relacionado: [DECISIONES.md](DECISIONES.md), [../CLAUDE.md](../CLAUDE.md)

## Resumen
- **Referencia principal: Sous Chef** (la más actual). Trombito sirve de apoyo; Sous Chef ya deriva de él y arrastra
  algunos restos suyos (Supabase, pnpm), que **no** se heredan.
- Se hereda la **línea general**: stack Android, arquitectura por capas con un módulo de dominio puro, convenciones de
  commits, skills/agentes/hooks de Claude Code, CI de release + Play, y marca (mascota Trombito, latón y rojo).
- Se descarta todo lo del **dominio de cocina**, el vídeo promocional y la deuda técnica detectada.
- Ante conflicto, mandan las reglas del prompt de Rally Copilot (push con permiso, dudas de producto a `QUESTIONS.md`).

## Heredado tal cual

| Qué | De dónde | Por qué |
|---|---|---|
| Stack: Kotlin 2.4.20, AGP 9.4.1 (Kotlin integrado), Compose BOM 2026.06.01, Material 3, Hilt 2.60.1, Navigation 2.9.8, coroutines, kotlinx.serialization | `gradle/libs.versions.toml` | Misma línea; versiones ya probadas juntas |
| SDK: compileSdk 37, targetSdk 36, **minSdk 26**; JDK 21 (daemon y toolchain), bytecode JVM 17 | `app/build.gradle.kts` | Pedido explícito: mismo minSdk que Sous Chef |
| Gradle 9.7.1 con `distributionSha256Sum`, Version Catalog, `FAIL_ON_PROJECT_REPOS`, caché y paralelo | raíz | Reproducibilidad |
| ktlint 1.8.0 + detekt 1.23.8 (`config/detekt/detekt.yml`) + Android Lint | `config/` | Gate de calidad común |
| Versionado: `version.properties` (major.minor) + parche = commits desde el último cambio + versionCode = nº de commits; tarea `imprimirVersion` | `app/build.gradle.kts` | Release solo al subir la versión menor |
| Firma solo por variables de entorno; `assembleRelease` falla sin firma | `app/build.gradle.kts` | No romper debug; nunca release sin firma |
| R8 + shrink en release; `Log.v/d/i` eliminados en release | `proguard-rules.pro` | Privacidad (posiciones GPS) |
| Manifest endurecido: `allowBackup=false`, reglas de backup que excluyen todo, componentes no exportados | `AndroidManifest.xml`, `res/xml/` | Datos solo en el móvil |
| Tests JVM: JUnit 4, coroutines-test, Robolectric 4.17, harness `BancoCompose`/`LimpiezaCompose` (sin `ui-test`) | `app/src/test` | Rápidos y sin emulador; práctica real de Sous Chef |
| `DomainDependencyRuleTest` (fronteras domain/data/ui) y `ContrastTest` (WCAG AA) | `app/src/test` | Arquitectura y accesibilidad vigiladas por tests |
| Hooks: `protect-secrets`, `block-secret-commands`, `warn-generated`, `post-edit-lint` + `scripts/test-hooks.sh` | `.claude/hooks` | Secretos nunca leídos ni impresos |
| Scripts: `check-claude-assets`, `gen-claude-index`, `check-docs`, `android-release`, `crear-keystore`, `subir-secretos-release`, `subir-secreto-play`, `play-promocionar` (+ test), `preparar-local-properties`, `gh`, `ficha-play` | `scripts/` | Utilidades sin dependencias |
| CI `android.yml`: gate → release (solo lectura, ve el keystore) → github-release (escritura, sin keystore) → play (interna + promoción a cerrada) | `.github/workflows/` | Separación de permisos ya probada |
| `claude-assets.yml`, `dependabot.yml` (minor/patch agrupados, prefijos `chore(deps)`/`chore(ci)`), acciones fijadas por SHA | `.github/` | Mantenimiento automático y supply chain |
| Commits **Conventional Commits con resumen en español**, sin `Co-Authored-By` | historial de Sous Chef | Estilo común |
| Plantillas `DOC`, `BRIEF`, `SKILL`, `AGENT` y formato de cabecera de documentos | `docs/_templates/` | Docs homogéneos |
| Marca: mascota Trombito (trombón dorado, gafas rojas), grid 108, zona segura `translate(0 8.4) scale(0.75)`, icono solo vectorial con capa monocroma | `docs/assets/`, `res/drawable/` | Coherencia visual entre apps |
| Letra del sistema, escala de espaciado, sin colores dinámicos | `ui/theme/` | Coherencia visual |
| AVD `trombito-36` compartido; patrones adb (install -r, logcat filtrado, screencap) | skill `android-install-and-logcat` | Mismo entorno de pruebas |

## Adaptado

| Qué | Cambio | Por qué |
|---|---|---|
| Módulo de dominio `:receta` | → `:motor` (Kotlin/JVM puro: geometría, notas, cronometraje, sectores, deltas, GPX/KML) | Misma idea (lógica crítica testeable sin Android), otro dominio |
| Push | Sous Chef lo permite; aquí **pide permiso cada vez** (`permissions.ask`), force-push prohibido | Regla del usuario: el push dispara releases |
| Dudas no bloqueantes | Sous Chef: decidir y anotar. Aquí: producto/UI/alcance → `QUESTIONS.md`; técnico menor → `docs/DECISIONES.md` | Regla del usuario: no decidir producto |
| Delegación | Paralelo máx. 5 → **presupuesto ≤ 1 punto** (opus 1, sonnet 0,5, haiku 0,25) | Regla del usuario |
| Fases `docs/fases/` (referenciadas pero inexistentes en Sous Chef) | → hitos en `ROADMAP.md`; skill `phase` → `hito` | El usuario pide ROADMAP con hitos |
| ADRs `docs/adr/` + `decisiones.md` | → `docs/DECISIONES.md` (fuente única) + `docs/investigacion/*.md`; skill `create-adr` → `registrar-decision` | El usuario pide DECISIONES.md |
| Memoria | Nueva: `docs/MEMORIA.md` importada desde CLAUDE.md + skill `recordar` | Petición del usuario: recordar entre sesiones |
| Gate CI | Sin el check «manifest sin INTERNET» (aquí la red es necesaria para mapas y DGT); añadido `:app:minifyReleaseWithR8` | Producto distinto; R8 fallaba solo al publicar en Sous Chef |
| Navegación con rutas de texto | → **rutas type-safe** (`@Serializable`) | Mejor práctica de Navigation 2.8+ |
| Tema | Claro por defecto → **oscuro por defecto** (carbón), latón como primario, rojo de marca como acento; `ColoresRally` (delta verde/rojo, avisos) | Estética de retransmisión WRC pedida |
| Gorro de chef en la mascota | → **casco de rally** (visera ahumada levantada, franja roja, ribetes latón); fondo carbón | Motivo pedido; el usuario eligió mascota + casco |
| `qa-scenario` | Escenarios de alarma → pantalla apagada, segundo plano, pérdida de GPS, doze, permisos de ubicación | Navegación en vez de cronómetro de cocina |
| `security-review` / `android-review` | Foco `alarms` → `location` / `location` + `audio` | Riesgos propios de esta app |
| `android-create-system-component` | Alarmas exactas → **servicio en primer plano de ubicación** (de la versión de Trombito) | Navegación con pantalla apagada |
| `crear-keystore.py` | Ruta de JDK fija → busca JDK 21 instalados | Portabilidad |
| Skills y agentes nuevos | `recordar`, `gps-replay`, agente `copiloto-reviewer` (análogo a `chef-reviewer`) | Necesidades de este dominio |

## Descartado

| Qué | Por qué |
|---|---|
| `:receta`, `:tools` y la CLI `sc`, `data/` (recetas, técnicas, legado), tareas `CopiarDatos` y `ComprobarNovedades` | Dominio de cocina |
| Skills `receta-cronometrada`, `receta-revisar`, `tecnica-nueva`, `despensa`; agentes `chef-reviewer`, `recetario-strategist`, `product-strategist` | Dominio de cocina (el último, muy ligado al producto de Sous Chef) |
| Remotion (`remotion-*`), `video-promo`, `video/` | No pedido; se puede recuperar en H12 si se quiere vídeo de Play |
| Room, Coil, Play In-App Update/Review | Sin uso en H0: se añaden con `dependency-add` en el hito que los necesite (sin código muerto) |
| Turbine y MockK | Turbine estaba declarado sin ningún uso en Sous Chef; MockK casi sin uso. Se usan fakes escritos a mano |
| Restos de Trombito en Sous Chef: reglas Supabase en hooks y settings, sqlfluff, checkbox RLS en la plantilla de PR, plantillas que dicen «Trombito»/`pnpm` | Deuda heredada sin relación con este proyecto |
| Referencias rotas de Sous Chef (`docs/fases/`, `docs/ci-cd.md`, `scripts/sc.ps1`, `docs/_research/`) y alias de fuentes muertos | Deuda técnica |
| Monorepo pnpm/Turbo, web, Supabase, worker de IA de Trombito | Rally Copilot es solo Android, sin backend |
| `inAppUpdatePriority: 5` (actualización obligatoria de Sous Chef) | Rally Copilot no fuerza actualizaciones (no hay ADR equivalente); se puede proponer más adelante |

## Pendiente de decidir
- Nada de la herencia queda abierto. Las decisiones de producto que salen de la investigación están en
  [QUESTIONS.md](../QUESTIONS.md).
