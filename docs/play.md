# Google Play

> Propósito: pasos para publicar Rally Copilot en Google Play y dejar la subida automática · Audiencia: Joan · Actualizado: 2026-10-28 · Relacionado: [pasos-manuales.md](pasos-manuales.md), [DECISIONES.md](DECISIONES.md)

## Resumen
El CI (`.github/workflows/android.yml`, adaptado de Sous Chef) genera el APK y el AAB firmados cuando un push a `main`
cambia `version.properties` (o con `workflow_dispatch` + `publicar`). Publica la release en GitHub y, si tiene
credenciales, sube el AAB a la **pista interna** de Play y lo promociona a la **prueba cerrada** (`alpha`). Dar de
alta la app no se puede automatizar: se hace una vez, a mano, en Play Console.

## Una sola vez, en Play Console
1. **Cuenta de desarrollador**: la misma que Sous Chef.
   - Las cuentas personales nuevas necesitan una **prueba cerrada con al menos 12 testers durante 14 días** antes de
     pedir producción. Para uso propio basta la prueba interna.
2. **Crear la app**: nombre «Rally Copilot», español (España), gratuita. El ID de paquete,
   `app.trombito.rallycopilot`, queda fijado con la primera subida.
3. **Play App Signing**: aceptarlo. La clave de `~/.rallycopilot/` queda como **clave de subida**; Google guarda la de
   firma.
4. **Primera subida, a mano** (la API no puede crear la primera versión de una app nueva):
   1. Prueba > Prueba interna > Crear versión.
   2. Subir el `rally-copilot-<versión>.aab` de la release de GitHub (el mapping de R8 va dentro del AAB).
   3. Pegar las notas de `distribution/whatsnew/whatsnew-es-ES` en «Novedades».
   4. Añadir a Joan como tester y abrir el enlace de participación en el móvil.
5. **Contenido de la app** (menú Política). Se rellena cuando la app use ubicación (H1–H2):
   - Política de privacidad: `docs/privacidad.md` (se escribe en H12).
   - Seguridad de los datos: la ubicación precisa se usa **solo en el dispositivo** (navegación, notas, tiempos); los
     recorridos se guardan en el móvil y no se envían a ningún servidor. La red solo descarga mapas e incidencias.
     VERIFICAR qué declarar en el formulario cuando la ubicación no sale del dispositivo.
   - Permisos: ubicación precisa y servicio en primer plano de tipo `location` (navegación con pantalla apagada).
     VERIFICAR en H2 si hace falta `ACCESS_BACKGROUND_LOCATION` o basta con el servicio arrancado con la app visible.
   - Datos de contacto, público (adultos) y clasificación de contenido: como Sous Chef.
6. **Ficha de Play Store** en `distribution/listing/`: icono `graphics/icon-512.png` y gráfico
   `graphics/feature-1024x500.png`, regenerados con `node scripts/ficha-play.mjs` (Chrome headless + Pillow). Textos y
   capturas en H12.

## Subida automática
1. En Google Cloud: cuenta de servicio, *Google Play Android Developer API* activada y clave JSON guardada en
   `~/.rallycopilot/` (nunca en el repo). Puede reutilizarse la cuenta de servicio de Sous Chef invitándola a esta app.
2. En Play Console > Usuarios y permisos: invitar el correo de la cuenta de servicio, solo para esta app, con permiso
   para **publicar en pistas de prueba**.
3. En GitHub > Settings > Secrets and variables > Actions:
   - secreto `PLAY_SERVICE_ACCOUNT_JSON`: `node scripts/subir-secreto-play.mjs` (comprueba la forma, no imprime valores);
   - variable `PLAY_ESTADO`: `draft` mientras la app esté en borrador; `completed` cuando ya haya salido una versión;
   - variable `PLAY_PISTA_CERRADA`: vacía = `alpha`; `ninguna` desactiva la promoción.
4. A partir de ahí, cada push a `main` que suba `versionMinor` o `versionMajor` publica en GitHub **y** en Play. El
   push lo hace Joan (o Claude con su permiso explícito).
5. Parche a mano: workflow «Android» con `workflow_dispatch` en `main` y `publicar=true`.

## Prueba cerrada
- `scripts/play-promocionar.mjs` copia la release de la interna a la cerrada con la API de edits (el mismo AAB no se
  puede subir dos veces). Rechaza `production`. Probar sin tocar Play:
  `PLAY_SERVICE_ACCOUNT_JSON="$(cat <ruta>)" node scripts/play-promocionar.mjs --paquete app.trombito.rallycopilot --version-code <n> --seco`.
- La prueba cerrada pasa la revisión de Google en cada versión; la interna no.

## Notas de versión
- `distribution/whatsnew/whatsnew-es-ES`: texto para el usuario, máximo 500 caracteres, viñetas «• ». Skill
  `release-notes`, destino `play`.

## Play y GitHub en el mismo móvil
- No se pueden mezclar: Play firma con su clave y las firmas no coinciden. Para cambiar hay que desinstalar y se
  pierden los tramos guardados (salvo copia, fuera de alcance por ahora). `allowBackup` está desactivado.

## Problemas típicos
- `release` compila y firma con `contents: read` (único job que ve el keystore); `github-release` publica con
  `contents: write` sin keystore; `play` espera a ambos. Si falla `play`, «Re-run failed jobs» lo repite (el AAB se
  guarda 7 días).
- `Only releases with status draft may be created on draft app`: poner `PLAY_ESTADO=draft`.
- `APK specifies a version code that has already been used`: clon superficial (hace falta `fetch-depth: 0`) o versión
  ya subida.
- `The caller does not have permission`: la cuenta de servicio no está invitada o le falta el permiso.
- «El App Bundle contiene código nativo sin símbolos»: informativo. El CI adjunta `native-debug-symbols-<v>.zip`
  cuando el AAB lleva `.so` (MapLibre y sherpa-onnx los traerán).
