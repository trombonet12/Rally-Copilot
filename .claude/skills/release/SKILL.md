---
name: release
description: Publicar una versión menor (versionMinor + push con permiso) o un parche (dispatch con publicar): gate, vigilar el CI y subir el AAB a Play; no preparar (release-preflight) ni notas (release-notes).
argument-hint: "[local|github]"
allowed-tools: Read, Edit, Grep, Bash(./gradlew *), Bash(git status*), Bash(git log*), Bash(git rev-parse*), Bash(git add version.properties*), Bash(git add distribution/*), Bash(git add CHANGELOG.md*), Bash(git commit *), Bash(gh run *), Bash(gh workflow run *), Bash(gh release view*), Bash(node scripts/gh.mjs *), Bash(node scripts/android-release.mjs*), Bash(wc *), Bash(sha256sum *)
disable-model-invocation: true
---

# release

Publica la versión `$ARGUMENTS`. Sin valor, preguntar `local|github` y parar. Acción con efectos externos: solo bajo petición explícita del usuario.

## Cuándo usarla
- `github`: el usuario pide publicar una versión nueva (release de GitHub con APK y AAB, y subida a Play).
- `local`: el usuario pide solo el APK firmado en su máquina, sin publicar nada.

## Cuándo NO usarla
- Sin `release-preflight` en GO para el commit actual → ejecutar antes `release-preflight`.
- Redactar las notas (CHANGELOG, GitHub Release, Play) → usa `release-notes`.
- Solo verificar el gate → usa `qa`.

## Precondiciones
- `release-preflight` en GO sobre el commit a publicar (ver `docs/ESTADO.md`); si falta o es de otro commit, parar.
- `release-notes` con destino Play hecho: `distribution/whatsnew/whatsnew-es-ES` listo para esta versión.
- `local`: `node scripts/android-release.mjs` carga `RALLY_KEYSTORE_FILE`, `RALLY_KEYSTORE_PASSWORD`, `RALLY_KEY_ALIAS`, `RALLY_KEY_PASSWORD` desde `~/.rallycopilot/release-signing.properties`. Nunca leer ni mostrar valores.
- `github`: `gh` autenticado (o `node scripts/gh.mjs <args>`, que usa el token de Git Credential Manager). **El push necesita permiso explícito del usuario** (regla `ask`); jamás force-push.

## Pasos
1. `git status` y `git rev-parse HEAD`; mostrar commit y versión actual (`./gradlew -q :app:imprimirVersion` → `versionName versionCode`, calculados desde git) y confirmar con el usuario antes de actuar.
2. **local**: `node scripts/android-release.mjs` (o `./gradlew assembleRelease` con las variables en el entorno). El APK queda en `app/build/outputs/apk/release/`; el script imprime su SHA-256. Terminar con el paso 11.
3. **github**: elegir con el usuario **menor** (subir `versionMinor`, o `versionMajor` con `versionMinor=0`, en `version.properties`: ese commit es el `.0`) o **parche** (no se toca nada: el parche son los commits desde el último cambio de `version.properties`). El versionCode es `git rev-list --count HEAD`, siempre creciente.
4. Gate qa full (skill `qa`). Si falla, parar y reportar; no se publica con el gate en rojo.
5. Comprobar `wc -m distribution/whatsnew/whatsnew-es-ES` ≤ 500.
6. Commit **sin** `Co-Authored-By`: `chore(release): v<versionName>` (incluye `version.properties` si es menor, notas de Play y `CHANGELOG.md` si cambió); `<versionName>` sale de `imprimirVersion` tras el commit.
7. Pedir permiso al usuario y `git push origin main`. Menor: el push publica (el CI solo publica si el push cambia `version.properties`). Parche: después del push, `gh workflow run android.yml --ref main -f publicar=true`.
8. Vigilar el CI: `gh run list --limit 3`, luego `gh run view <id> --json conclusion` hasta `success`. Si falla, reportar el job y no reintentar a ciegas.
9. Comprobar la release: `gh release view v<versionName> --json assets` debe listar `rally-copilot-<v>.apk`, `rally-copilot-<v>.aab` y `checksums.txt`.
10. Play: con el secreto `PLAY_SERVICE_ACCOUNT_JSON`, el job `play` sube el AAB a la pista interna y promociona esa versión a la prueba cerrada (`scripts/play-promocionar.mjs`, variable `PLAY_PISTA_CERRADA`, por defecto `alpha`; `ninguna` la desactiva). Comprobar en Play Console que sale en las dos pistas. Sin secreto, subida manual (ver `docs/play.md`). La primera subida de la app a Play es siempre manual.
11. Informar `modo | commit | versión | resultado | artefactos | SHA-256`. No pegar logs con valores sensibles.

## Validaciones / tests
- `local`: el APK firmado existe; `adb install -r` solo si el usuario lo pide.
- `github`: gate en verde antes del push; run del CI en `success`; la release tiene los 3 assets.

## Definition of Done
- [ ] `release-preflight` en GO para el commit publicado.
- [ ] `github`: push autorizado por el usuario; run en `success`, release `v<versionName>` con APK, AAB y `checksums.txt`.
- [ ] `github`: AAB en la pista interna y en la prueba cerrada (CI o manual) con las notas de `whatsnew-es-ES`.
- [ ] `local`: APK firmado generado con commit y SHA-256 registrados.
- [ ] Ningún force-push, tag ni valor de secreto fuera de lo pedido; keystore y `release-signing.properties` fuera del repo y sin imprimir.

## Docs a actualizar
- Versión, commit y estado de Play en docs/ESTADO.md (skill `session-handoff`) · `CHANGELOG.md` (skill `release-notes`)
