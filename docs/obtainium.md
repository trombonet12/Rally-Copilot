# Release Android y Obtainium

> Propósito: publicar el APK firmado en GitHub e instalarlo y actualizarlo en el móvil con Obtainium · Audiencia: Joan · Actualizado: 2026-11-07 · Relacionado: [pasos-manuales.md](pasos-manuales.md), [play.md](play.md), `.github/workflows/android.yml`

## Resumen
Hoy Rally Copilot se distribuye **solo por GitHub Releases**, y el móvil la instala y actualiza con **Obtainium** (Joan ya lo usa con las versiones 1.x).
Google Play está pospuesto: su job solo corre con la variable de repo `PLAY_HABILITADO=true` (ver [play.md](play.md)).

Publicar una versión:
1. Subir `versionMinor` (o `versionMajor`) en `version.properties` y hacer commit.
2. Push a `main`. Claude lo hace sin pedir permiso cuando el gate y los scripts están en verde (CLAUDE.md, `docs/MEMORIA.md` 2026-11-01); nunca force-push. Tú también puedes hacerlo.
3. El workflow «Android» pasa el gate y, si `version.properties` cambió en ese push, ejecuta la release.
4. La release compila el APK y el AAB **sin firmar** (Gradle no ve el keystore), los firma en un paso aparte con
   `apksigner`/`jarsigner`, verifica la firma, el `applicationId` y el `versionCode`, y publica `v<major>.<minor>.<parche>` con estos archivos:
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
- Los secretos de firma (y `PLAY_SERVICE_ACCOUNT_JSON`) viven en el Environment `release` de GitHub, restringido a la
  rama `main`; los jobs `release` y `play` usan `environment: release`. Paso manual: crear el Environment
  (Settings > Environments > New environment > `release`, con *Deployment branches* = `main`), subir los secretos con
  `node scripts/subir-secretos-release.mjs --entorno release` y `gh secret set PLAY_SERVICE_ACCOUNT_JSON --env release`
  (solo si se activa Play), y borrar después los secretos del mismo nombre del repositorio.
- Todas las versiones tienen que firmarse con la misma clave. Si se pierde, el móvil no deja actualizar encima: hay
  que desinstalar y se pierden los tramos guardados (salvo que antes hayas exportado la copia de seguridad desde Ajustes > Datos). **Haz copia de seguridad de `~/.rallycopilot/`** (VERIFICAR si ya está hecha).

### Huella del certificado de firma
**Huella SHA-256 del certificado: VERIFICAR** (la rellena el agente principal; no inventarla).

Sirve para comprobar la **primera instalación** (otro móvil, reinstalación): `checksums.txt` va en la misma release que
el APK y no protege si se compromete la cuenta; las actualizaciones ya las protege Android con la continuidad de firma.
Cómo obtenerla y compararla:
- De la release publicada: `apksigner verify --print-certs rally-copilot-<versión>.apk` y mirar
  `Signer #1 certificate SHA-256 digest`. La release también la escribe en el resumen del run del workflow «Android».
- Del keystore local (sin imprimir contraseñas en el chat): `keytool -list -v -keystore ~/.rallycopilot/<keystore>`
  y la línea `SHA256:` del certificado.
- En el móvil, antes de instalar un APK nuevo: la app AppVerifier (o `apksigner`) y comparar con la huella de aquí.
- Para que la release falle si el certificado cambia: variable de repo (o del entorno) `RELEASE_CERT_SHA256` con la huella.

## Obtainium y Play
No se pueden mezclar en el mismo móvil: Play firmará con su propia clave (Play App Signing) y Android no deja
actualizar una instalación con otra firma. Cuando se active Play, habrá que elegir un canal por móvil.

## Fallos habituales
- **«Falta el secret …» en la release:** no se han subido los secretos de firma (`node scripts/subir-secretos-release.mjs`).
- **«Release sin firma»:** en local, faltan las variables `RALLY_*`. Usa `node scripts/android-release.mjs`, que las
  carga de `~/.rallycopilot/release-signing.properties`. (CI compila con `-Prally.sinFirma=true` y firma después.)
- **Obtainium no ve la versión:** la release no se ha publicado. Comprueba que el push cambiaba `version.properties`,
  o lanza el parche a mano.
- **Volver a una versión anterior:** Android no instala un `versionCode` menor. Revierte el commit y publica otra vez:
  saldrá una versión con un `versionCode` mayor y el código anterior.
