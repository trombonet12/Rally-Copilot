---
name: android-add-permission
description: Añadir un permiso Android (manifest, petición runtime, rationale, denegación permanente) con test y mínimo privilegio; típico: ubicación, notificaciones, foreground service.
allowed-tools: Read, Write, Edit, Grep, Glob, Bash(./gradlew *), Bash(grep *)
---

# android-add-permission

Añade un permiso con el mínimo privilegio: declaración, petición en el momento de uso, rationale y todos los estados.

## Cuándo usarla
- Una función necesita un permiso de runtime o especial: `ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION`, `POST_NOTIFICATIONS` (API 33+), `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION` (API 34+), `INTERNET` (descarga de mapas e incidencias), `RECORD_AUDIO` (edición de notas por voz, H8).

## Cuándo NO usarla
- Crear el servicio, receiver o canal que lo usa → `android-create-system-component` (los permisos se añaden con esta skill).
- Solo auditar permisos existentes → `android-review` (permissions) o `security-review`.
- `ACCESS_BACKGROUND_LOCATION`: la navegación usa foreground service iniciado desde la UI y no lo necesita; no añadir sin decisión registrada en `docs/DECISIONES.md`.

## Precondiciones
- Función concreta que lo justifica y pantalla desde la que se pedirá. Sin uso real, no se añade.
- Comprobar que no hay alternativa sin permiso y que no está ya declarado: `grep -n "uses-permission" app/src/main/AndroidManifest.xml`.

## Pasos
1. Declarar solo ese permiso en `AndroidManifest.xml`; usar `android:maxSdkVersion` si solo aplica hasta cierta versión. Nada "por si acaso".
2. Modelar el estado en una clase sellada: `Granted`, `Denied`, `ShouldShowRationale`, `PermanentlyDenied` (y para ubicación, `SoloAproximada`).
3. Pedir con `ActivityResultContracts.RequestPermission`/`RequestMultiplePermissions` (`rememberLauncherForActivityResult`) en el momento de uso («Conducir», «Reconocimiento»), nunca al arrancar.
4. Rationale: si `shouldShowRequestPermissionRationale` es true, explicación propia (para qué sirve la ubicación precisa en las notas) y botón para pedir de nuevo.
5. Denegación permanente: botón a ajustes (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`); al volver, reevaluar con `ContextCompat.checkSelfPermission` en `ON_RESUME`.
6. Degradación: sin permiso la app sigue usable (mapas, mis tramos, análisis); con ubicación aproximada se avisa de que las notas no son fiables; sin notificaciones, el servicio funciona pero se avisa.
7. Lógica de estado en el ViewModel o helper testeable; el chequeo de plataforma tras una interfaz inyectada (`android-create-hilt-module`).
8. Tests JVM: mapeo de estados con fake del chequeo; Compose/Robolectric del rationale y del estado permanente (`shadowOf(application).grantPermissions(...)`).

## Validaciones / tests
- `./gradlew testDebugUnitTest ktlintCheck detekt lintDebug`.
- `grep -rn "uses-permission" app/src --include=AndroidManifest.xml`: solo permisos justificados.

## Definition of Done
- [ ] Permiso declarado una vez, con `maxSdkVersion` si procede.
- [ ] Petición en contexto, con rationale y denegación permanente cubiertos.
- [ ] La app funciona degradada sin el permiso.
- [ ] Tests de estados en verde; lint sin avisos nuevos.
- [ ] `./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug` en verde.

## Docs a actualizar
- `docs/DECISIONES.md` si cambia la estrategia de permisos · `docs/privacidad.md` si existe · docs/ESTADO.md (skill `session-handoff`)
