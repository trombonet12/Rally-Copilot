# Release Android y Obtainium

> Propósito: publicar el APK firmado en GitHub e instalarlo y actualizarlo en el móvil con Obtainium · Audiencia: Joan · Actualizado: 2026-10-28 · Relacionado: [pasos-manuales.md](pasos-manuales.md), [play.md](play.md), `.github/workflows/android.yml`

## Resumen
Hoy Rally Copilot se distribuye **solo por GitHub Releases**, y el móvil la instala y actualiza con **Obtainium**.
Google Play está pospuesto: su job solo corre con la variable de repo `PLAY_HABILITADO=true` (ver [play.md](play.md)).

Publicar una versión:
1. Subir `versionMinor` (o `versionMajor`) en `version.properties` y hacer commit.
2. Push a `main`. Lo haces tú, o Claude con tu permiso explícito.
3. El workflow «Android» pasa el gate y, si `version.properties` cambió en ese push, ejecuta la release.
4. La release compila y firma el APK y el AAB y publica `v<major>.<minor>.<parche>` con estos archivos:
   - `rally-copilot-<versión>.apk`
   - `rally-copilot-<versión>.aab`
   - `checksums.txt`

Parches sin subir la versión menor: workflow «Android» → *Run workflow* en `main` con `publicar` activado. El
parche es el número de commits desde el último cambio de `version.properties`. El `versionCode` es el número total
de commits, así que siempre sube.

## Configurar Obtainium (una vez)
El repositorio es **público**, así que Obtainium no necesita token.
1. *Add app* → URL `https://github.com/trombonet12/Rally-Copilot`.
2. En las opciones de GitHub, filtro de APK `rally-copilot-.*\.apk` (para que ignore el `.aab`). Desactiva *Include
   prereleases*.
3. Instalar. Obtainium avisará de cada release nueva. Si quieres que actualice solo, activa *Background update
   checking* y, si tu Android lo permite, *Enable background updates*.

## Firma
- La clave es **propia de Rally Copilot**: no se reutiliza la de Sous Chef ni la de Trombito. Se crea una vez con
  `python -I scripts/crear-keystore.py` en `~/.rallycopilot/` y se sube a GitHub con
  `node scripts/subir-secretos-release.mjs`.
- Todas las versiones tienen que firmarse con la misma clave. Si se pierde, el móvil no deja actualizar encima: hay
  que desinstalar y se pierden los tramos guardados. **Haz copia de seguridad de `~/.rallycopilot/`.**

## Obtainium y Play
No se pueden mezclar en el mismo móvil: Play firmará con su propia clave (Play App Signing) y Android no deja
actualizar una instalación con otra firma. Cuando se active Play, habrá que elegir un canal por móvil.

## Fallos habituales
- **«Falta el secret …» en la release:** no se han subido los secretos de firma (`node scripts/subir-secretos-release.mjs`).
- **«Release sin firma»:** en local, faltan las variables `RALLY_*`. Usa `node scripts/android-release.mjs`, que las
  carga de `~/.rallycopilot/release-signing.properties`.
- **Obtainium no ve la versión:** la release no se ha publicado. Comprueba que el push cambiaba `version.properties`,
  o lanza el parche a mano.
- **Volver a una versión anterior:** Android no instala un `versionCode` menor. Revierte el commit y publica otra vez:
  saldrá una versión con un `versionCode` mayor y el código anterior.
