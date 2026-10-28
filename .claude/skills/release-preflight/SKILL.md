---
name: release-preflight
description: Checklist obligatorio antes de publicar: gate qa full, secret scan, migraciones, versión/notas y firma; informa GO/NO-GO, no publica (para eso release).
allowed-tools: Read, Grep, Glob, Bash(./gradlew *), Bash(node scripts/*), Bash(bash scripts/*), Bash(git status*), Bash(git log*), Bash(git diff*), Bash(gh secret list*), Bash(gh variable list*), Bash(gh workflow list*)
---

# release-preflight

Comprueba que el repositorio está listo para publicar. Solo lee y ejecuta verificaciones; el veredicto es GO / NO-GO con huecos justificados.

## Cuándo usarla
- Antes de cualquier release Android y antes de invocar `release` (y antes de pedir permiso para un push que cambie `version.properties`).

## Cuándo NO usarla
- Publicar o disparar workflows → usa `release` (que exige este preflight).
- Generar las notas desde commits → usa `release-notes`.
- Revisión de calidad sin release → usa `qa`.

## Precondiciones
- Árbol limpio (`git status`); si no, listar cambios y parar con NO-GO.
- `gh` autenticado (o `node scripts/gh.mjs`) solo para comprobar secrets de CI; si no, el punto 6 queda "no verificable" (no es un OK).
- Nunca leer, imprimir ni pedir valores de secretos ni del keystore.

## Pasos
1. Gate qa full (skill `qa`). Un SKIP es un hueco que debe justificarse.
2. Secret scan: skill `security-secret-scan` sobre árbol e historial; cualquier hallazgo = NO-GO hasta rotar/limpiar.
3. Migraciones Room: si cambió el `@Database` o `Migrations.kt`, el test de migración de `./gradlew testDebugUnitTest` debe estar en verde; si no, anotar «sin migraciones».
4. Versión: `./gradlew -q :app:imprimirVersion` (`versionName versionCode`, calculados desde git; necesita historial completo). `versionCode` debe superar al publicado y `versionName` ser coherente con las notas.
5. Notas: existe `CHANGELOG.md` o notas de la versión (skill `release-notes`); si no, proponer generarlas. Las notas de Play `distribution/whatsnew/whatsnew-es-ES` deben estar al día para esta versión y medir ≤ 500 (`wc -m`).
6. Firma y CI (solo nombres): `~/.rallycopilot/release-signing.properties` existe (comprobar con `test -f`, sin leerlo); el workflow `.github/workflows/android.yml` existe y genera la release con APK, AAB y `checksums.txt`; constan en `gh secret list` `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` y, para Play, `PLAY_SERVICE_ACCOUNT_JSON`; variables `PLAY_ESTADO`/`PLAY_PISTA_CERRADA` revisadas con `gh variable list`. Falta alguno de firma → NO-GO; falta el de Play → HUECO (subida manual).
7. Atribuciones de licencia de datos (OSM, IGN, voces) visibles en la app si la versión incluye mapas o voces nuevas.
8. Entregar informe `punto | estado (OK/HUECO/NO-GO) | evidencia | acción` y el veredicto final.

## Validaciones / tests
- Gate qa full con código 0 (o huecos documentados).
- `node scripts/check-claude-assets.mjs` sin errores si cambiaron skills o agentes.

## Definition of Done
- [ ] Informe con los puntos y veredicto GO/NO-GO.
- [ ] Ningún valor de secreto en la salida.
- [ ] Todo hueco con justificación y responsable.
- [ ] Veredicto GO guardado en docs/ESTADO.md con el hash del commit evaluado.

## Docs a actualizar
- docs/ESTADO.md (skill `session-handoff`): veredicto, commit evaluado y huecos abiertos.
