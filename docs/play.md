# Google Play

> Propósito: pasos para publicar Rally Copilot en Google Play y dejar la subida automática · Audiencia: Joan · Actualizado: 2026-11-25 · Relacionado: [pasos-manuales.md](pasos-manuales.md), [privacidad.md](privacidad.md), [DECISIONES.md](DECISIONES.md)

> **Estado (2026-11-25):** la distribución actual es GitHub Release + Obtainium ([obtainium.md](obtainium.md)). El job
> `play` del workflow solo corre si la variable de repo `PLAY_HABILITADO` vale `true`. La ficha, la política de
> privacidad y el «Checklist de Play Console» de este documento están listos; activar Play lo decide Joan.

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
   - Política de privacidad: [privacidad.md](privacidad.md). Valores exactos en «Checklist de Play Console».
   - Seguridad de los datos: la ubicación precisa se usa **solo en el dispositivo** (navegación, notas, tiempos); los
     recorridos se guardan en el móvil y no se envían a ningún servidor. La red solo descarga mapas e incidencias.
     El **audio del micrófono** se reconoce en el dispositivo (Vosk, sin red) y no se guarda ni se envía. VERIFICAR qué
     declarar en el formulario cuando ubicación y audio no salen del dispositivo.
   - Permisos (el manifest vigente): ubicación precisa y aproximada (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`),
     `FOREGROUND_SERVICE` con los tipos `location` (navegación con pantalla apagada), `microphone`
     (`FOREGROUND_SERVICE_MICROPHONE`, la escucha de «Copiloto» con Vosk) y `dataSync` (descargas), `POST_NOTIFICATIONS`,
     `INTERNET` y `ACCESS_NETWORK_STATE`. **No** se declara `ACCESS_BACKGROUND_LOCATION`: el servicio arranca con la
     app visible. `RECORD_AUDIO` se pide solo al activar «Copiloto».
   - Datos de contacto, público (adultos) y clasificación de contenido: ver «Checklist de Play Console».
6. **Ficha de Play Store** en `distribution/listing/`: icono `graphics/icon-512.png` y gráfico
   `graphics/feature-1024x500.png`, regenerados con `node scripts/ficha-play.mjs` (Chrome headless + Pillow). Textos en
   `es-ES/` y titulares de las capturas en `es-ES/capturas.json` (ver «Checklist de Play Console»).

## Checklist de Play Console
Valores exactos para pegar, en el orden del menú de Play Console. Lo marcado VERIFICAR no está confirmado y se
comprueba en el formulario antes de enviarlo.

### 1. Crear la app
- Nombre: `Rally Copilot`. Idioma predeterminado: español (España). Tipo: **Aplicación** (no juego). Precio: gratuita.
- Categoría: **Mapas y navegación**.
- ID de paquete: `app.trombito.rallycopilot` (fijado con la primera subida). Aceptar Play App Signing.

### 2. Ficha principal de la tienda
| Campo | Valor |
|---|---|
| Nombre de la app (≤ 30) | `distribution/listing/es-ES/title.txt` |
| Descripción breve (≤ 80) | `distribution/listing/es-ES/short-description.txt` |
| Descripción completa (≤ 4000) | `distribution/listing/es-ES/full-description.txt` |
| Icono (512×512) | `distribution/listing/graphics/icon-512.png` |
| Gráfico de funciones (1024×500) | `distribution/listing/graphics/feature-1024x500.png` |
| Capturas de teléfono (8) | `01-notas.png` a `08-hoja.png` en 1080×1920 (aún no existen: VERIFICAR; las de `docs/capturas/` son 9:20 y Play no las admite). Titulares y subtítulos en `distribution/listing/es-ES/capturas.json` |
| Vídeo | URL de YouTube del vídeo promocional, subido como **Oculto** |

### 3. Contenido de la app
| Apartado | Valor |
|---|---|
| Política de privacidad | `https://github.com/trombonet12/Rally-Copilot/blob/main/docs/privacidad.md` |
| Acceso a la app | Toda la funcionalidad es accesible sin credenciales (sin login) |
| Anuncios | No contiene anuncios |
| ID de publicidad | No (la app no lo usa) |
| Público objetivo | Solo 18 años o más |
| Contacto | `trombonet12@trombonet12.es` · `https://www.trombonet12.es/` · «Trombonet12© and Co. Interactive Systems Solutions» |

**Clasificación de contenido (IARC).** Categoría: utilidad o navegación, no juego (VERIFICAR el nombre exacto de la
opción en el cuestionario). Respuestas sugeridas:
- Violencia, sangre, contenido sexual, desnudos, lenguaje soez, drogas, apuestas y miedo: **No** a todo.
- Interacción entre usuarios o contenido generado por usuarios: **No** (no hay cuentas; compartir un GPX lo hace el
  usuario con el menú de Android).
- Comparte la ubicación del usuario con otros usuarios: **No**.
- Compras digitales: **No**. Acceso libre a internet o navegador: **No**.
- Resultado esperado: apto para todos los públicos; VERIFICAR.

**Seguridad de los datos.** Criterio: lo que se procesa solo en el dispositivo y no sale de él no cuenta como
«recopilado». Respuesta propuesta, pregunta a pregunta:
1. ¿La app recopila o comparte alguno de los tipos de datos obligatorios? **Propuesta: No**, con la salvedad del punto 7.
2. ¿Todos los datos se cifran en tránsito? **Sí** (solo HTTPS; `usesCleartextTraffic="false"`).
3. ¿Ofreces una forma de solicitar la eliminación de datos? **No aplica**: no hay cuentas ni datos en servidor; los
   datos locales se borran al desinstalar.
4. Ubicación precisa: **no recopilada**; se procesa solo en el móvil (navegación, notas, tiempos) y los recorridos
   quedan en la base local.
5. Audio (voz): **no recopilado**; Vosk reconoce en el móvil, sin guardar ni enviar audio.
6. Información personal, financiera, salud, contactos, mensajes, fotos, archivos y calendario: **No**. Los GPX y KML
   se leen solo desde el URI que entrega el usuario. Identificadores de dispositivo o de usuario: **No**.
7. Teselas de satélite del IGN: la petición lleva la zona del mapa y la IP a un servidor de terceros. VERIFICAR si
   Play lo considera «Ubicación aproximada» compartida. Si se quiere declarar por prudencia: ubicación aproximada,
   **compartida** (con el IGN), opcional (solo con la capa de satélite), finalidad «Funcionalidad de la app».
8. Diagnósticos y registros: se guardan en la carpeta externa de la app y no salen del móvil: **no recopilados**.
   VERIFICAR si el registro puede contener coordenadas (seguiría sin salir del móvil).
9. Descarga de mapas (GitHub) e incidencias (DGT, NAP): sin datos del usuario en la petición; el servidor ve la IP,
   como en toda conexión. No se declara.

**Servicios en primer plano.** Un texto y un vídeo por tipo. Los vídeos son `build/promo/fgs/fgs-<tipo>.mp4`
(`fgs-location.mp4`, `fgs-microphone.mp4`, `fgs-dataSync.mp4`) y se suben a YouTube como Oculto; se pega la URL.
| Tipo | Texto de la declaración | Vídeo |
|---|---|---|
| `location` | Rally Copilot es una app de navegación. Al pulsar Empezar o Conducir, un servicio en primer plano mantiene la posición y las notas de voz del copiloto con la pantalla apagada o la app en segundo plano. Sin él, las notas y la guía se cortarían. Muestra una notificación permanente que el usuario ve y puede detener. | `build/promo/fgs/fgs-location.mp4` subido a YouTube como Oculto |
| `microphone` | Solo si el usuario activa «Copiloto», el servicio escucha la palabra «Copiloto» y la orden siguiente, con la pantalla apagada, para manejar la app con las manos en el volante. El reconocimiento se hace en el móvil y no se guarda ni se envía audio. | `build/promo/fgs/fgs-microphone.mp4` subido a YouTube como Oculto |
| `dataSync` | Descarga de los datos de mapas de Mallorca (archivos grandes y reanudables) con una notificación de progreso. La inicia el usuario desde Mapas offline. | `build/promo/fgs/fgs-dataSync.mp4` subido a YouTube como Oculto |

Camino: Contenido de la app > Servicios en primer plano > marcar los tres tipos > pegar el texto y la URL de cada uno.
`ACCESS_BACKGROUND_LOCATION` no se declara porque no se usa.

### 4. Testers de la prueba cerrada (12 × 14 días)
- Para pedir producción con una cuenta personal nueva hacen falta **12 testers con la prueba cerrada aceptada durante
  14 días seguidos** (VERIFICAR el mínimo vigente en Play Console).
- Crear la lista en Prueba > Prueba cerrada > Testers: al menos 12 correos de Google, más el de Joan. Pasar el enlace de
  participación a cada uno; deben aceptarlo y mantener la app instalada los 14 días.
- Cada versión nueva en la cerrada pasa la revisión de Google.
- Cumplidos los 14 días, solicitar el acceso a producción desde el panel de Play Console y responder el cuestionario.

## Subida automática
1. En Google Cloud: cuenta de servicio, *Google Play Android Developer API* activada y clave JSON guardada en
   `~/.rallycopilot/` (nunca en el repo). Puede reutilizarse la cuenta de servicio de Sous Chef invitándola a esta app.
2. En Play Console > Usuarios y permisos: invitar el correo de la cuenta de servicio, solo para esta app, con permiso
   para **publicar en pistas de prueba**.
3. En GitHub > Settings > Secrets and variables > Actions:
   - secreto `PLAY_SERVICE_ACCOUNT_JSON`: `node scripts/subir-secreto-play.mjs` (comprueba la forma, no imprime valores);
   - variable `PLAY_ESTADO`: `draft` mientras la app esté en borrador; `completed` cuando ya haya salido una versión;
   - variable `PLAY_PISTA_CERRADA`: vacía = `alpha`; `ninguna` desactiva la promoción;
   - variable `PLAY_HABILITADO`: `true` para que el job `play` se ejecute (sin ella, solo GitHub Release).
4. A partir de ahí, cada push a `main` que suba `versionMinor` o `versionMajor` publica en GitHub **y** en Play. El
   push lo hace Claude sin pedir permiso cuando el gate y los scripts están en verde, o Joan.
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
  pierden los tramos guardados, salvo que antes se exporte la copia de seguridad (Ajustes > Datos, desde H14b:
  zip con la base). `allowBackup` está desactivado.

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
