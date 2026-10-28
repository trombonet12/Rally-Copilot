---
name: android-create-system-component
description: Crear piezas de sistema de Rally Copilot: foreground service de ubicación (navegación/grabación), canal de notificación o receiver de arranque; sin Google Play Services.
argument-hint: "[location-service|notification|boot-receiver]"
allowed-tools: Read, Write, Edit, Grep, Glob, Bash(./gradlew *)
---

# android-create-system-component

Crea el componente `$ARGUMENTS` en `:app`. Por defecto `notification`; si el valor no es uno de los tres, pedirlo y parar. Contexto: la navegación y el copiloto deben seguir funcionando con la pantalla apagada o la app en segundo plano (como un navegador), offline y sin Google Play Services (ver `docs/DECISIONES.md`).

## Cuándo usarla
- Hace falta una pieza que el sistema mantiene viva fuera de la UI: servicio de ubicación de la navegación/grabación de tramo, notificación persistente, reacción a reinicio o actualización.

## Cuándo NO usarla
- Solo declarar o pedir un permiso → `android-add-permission`.
- Pantallas Compose → `android-create-feature`.
- Workers de descarga de mapas (WorkManager): requieren decisión registrada (`registrar-decision`) y dependencia con `dependency-add`.

## Precondiciones
- Lógica de dominio (notas, cronometraje, detección de tramo) en `:motor` (puro): decide qué leer y cuándo; el componente de sistema solo recibe posiciones, delega y muestra.
- Dependencias por Hilt (`android-create-hilt-module` si faltan). Detalles de API y límites por versión de Android se confirman con la documentación oficial, no de memoria.

## Pasos
### Comunes
1. Lógica en una clase testeable aparte (servicio y receiver solo delegan). Declarar en `AndroidManifest.xml` con `exported="false"` salvo que el sistema lo exija.
2. Textos en `strings_<feature>.xml` (solo español). Escribir primero el test de la lógica, después el componente.

### location-service
1. `Service` en primer plano con `android:foregroundServiceType="location"`; permisos `FOREGROUND_SERVICE` y `FOREGROUND_SERVICE_LOCATION` (API 34+) más `ACCESS_FINE_LOCATION` (con `android-add-permission`). Se inicia desde la UI («Conducir», «Reconocimiento»), nunca en segundo plano.
2. `startForeground(id, notificación, FOREGROUND_SERVICE_TYPE_LOCATION)` en los primeros segundos; notificación persistente con icono `ic_stat_rally`, estado del tramo y acción «Parar».
3. Posiciones con `LocationManager` (`GPS_PROVIDER`, `requestLocationUpdates` a ~1 Hz o lo decidido) tras una interfaz de dominio (fuente de posiciones), **sin Fused Location de Google Play Services**. Detectar pérdida de señal (sin fix en N s) y avisar.
4. Exponer el flujo de posiciones y el estado (en marcha, pausado, sin señal) a la UI vía un repositorio inyectado (`StateFlow`), no por broadcast.
5. Parar ordenadamente (`stopForeground(STOP_FOREGROUND_REMOVE)` + `stopSelf`) al llegar, al parar el usuario o al revocar el permiso; persistir el tramo en curso en Room para sobrevivir a la muerte del proceso.

### notification
1. Canal del servicio de conducción (importancia baja, sin sonido: la voz va por TTS) y canal de avisos (incidencias, importancia alta), ids constantes, creados idempotentes al arrancar la app.
2. `NotificationFactory` inyectable: icono `ic_stat_rally`, título, `PendingIntent` inmutable que abre la pantalla de conducción, acción para parar.
3. `POST_NOTIFICATIONS` en runtime (API 33+) con `android-add-permission`; sin él el servicio funciona igualmente.

### boot-receiver
1. Receiver para `MY_PACKAGE_REPLACED` (y `BOOT_COMPLETED` solo si una decisión lo requiere) que limpia tramos a medias o retoma tareas pendientes desde Room; idempotente. Nunca reinicia la navegación solo.

## Validaciones / tests
- `./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug`.
- Unit/Robolectric: lógica del servicio con fuente de posiciones fake, construcción de notificación y canal (`ShadowNotificationManager`), arranque/parada del servicio (`ShadowService`).
- Prueba manual en emulador con `gps-replay` + `qa-scenario screen-off|background|gps-loss`; revisar con `android-review` (location, background, permissions).

## Definition of Done
- [ ] Componente declarado en el manifest con su `foregroundServiceType`, no exportado salvo necesidad, y lógica cubierta por tests.
- [ ] Sin Google Play Services; posiciones tras una interfaz de dominio.
- [ ] Sigue funcionando con pantalla apagada y en segundo plano (escenario ejecutado).
- [ ] Sin coordenadas ni datos de usuario en logs.
- [ ] `./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug` en verde.

## Docs a actualizar
- `docs/DECISIONES.md` si cambia el enfoque de servicio o ubicación · docs/ESTADO.md (skill `session-handoff`)
