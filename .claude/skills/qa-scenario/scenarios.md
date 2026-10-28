# Tabla de escenarios (qa-scenario)

`<pkg>` = applicationId de la app (`app.trombito.rallycopilot`, en debug `app.trombito.rallycopilot.dev`). Comandos `adb` estándar; con varios dispositivos añadir `-s <serial>`. Lo que no se pueda verificar en la versión de Android probada se anota como "no verificado". Ruta de referencia: una traza real corta de Mallorca de `docs/rutas/` reproducida con `node scripts/gps-replay.mjs` (skill `gps-replay`).

## process-death
- Preparación: navegación o modo libre en marcha con la traza reproduciéndose. Alternativa: `adb shell settings put global always_finish_activities 1` (restaurar a 0).
- Pasos: enviar a segundo plano (`adb shell input keyevent KEYCODE_HOME`), matar el proceso (`adb shell am kill <pkg>`; con foreground service activo no debería morir: probar también `adb shell am force-stop <pkg>`), volver desde recientes.
- Esperado: con `am kill` el servicio sigue y la grabación del tramo continúa; tras `force-stop` la app arranca limpia, el tramo grabado hasta ese punto se conserva y no hay crash.
- Evidencia: capturas antes/después; logcat sin `FATAL EXCEPTION`.

## apk-update
- Preparación: instalar la versión N-1 con tramos grabados, notas editadas y mapas descargados.
- Pasos: actualizar sin desinstalar (`adb install -r <apk-N>`); abrir la app.
- Esperado: migración de Room aplicada; tramos, mejores tiempos, notas editadas, ajustes y mapas descargados intactos.
- Evidencia: recuento de filas antes/después; logcat de la migración.

## screen-off
- Preparación: navegación con destino y modo copiloto «Notas»; traza en reproducción.
- Pasos: apagar la pantalla (`adb shell input keyevent KEYCODE_POWER`) durante 2-3 minutos de traza; volver a encender.
- Esperado: la voz sigue leyendo notas y alertas con la pantalla apagada; el cronómetro y los parciales siguen; la notificación persistente del servicio se ve; al encender, la UI muestra la posición actual sin saltos.
- Evidencia: logcat de TTS/notas con marcas de tiempo; `adb shell dumpsys activity services <pkg>`.

## background
- Preparación: navegación activa y otra app de música sonando.
- Pasos: `adb shell input keyevent KEYCODE_HOME`, abrir otra app durante la traza; volver.
- Esperado: navegación, notas y grabación continúan; la música baja de volumen mientras habla el copiloto (ducking) y se recupera después.
- Evidencia: `adb shell dumpsys audio` (focus), logcat.

## gps-loss
- Preparación: navegación activa con la traza.
- Pasos: parar `gps-replay` 30 s (túnel simulado) y reanudar más adelante en la ruta; probar también saltos grandes de posición.
- Esperado: la app indica pérdida de señal sin crash ni notas falsas; al recuperar, se reengancha a la ruta (o recalcula) y el cronometraje no se corrompe.
- Evidencia: capturas y logcat.

## doze
- Preparación: navegación activa; pantalla apagada; forzar Doze: `adb shell dumpsys deviceidle force-idle` (salir con `adb shell dumpsys deviceidle unforce`).
- Pasos: continuar la traza con la pantalla apagada; repetir con el ahorro de batería activado.
- Esperado: el foreground service de ubicación sigue recibiendo posiciones y la voz sigue sonando; sin retrasos de minutos.
- Evidencia: `adb shell dumpsys deviceidle`, `adb shell dumpsys location`, logcat.

## permissions
- Preparación: `adb shell pm revoke <pkg> android.permission.ACCESS_FINE_LOCATION` y `android.permission.POST_NOTIFICATIONS`; ubicación aproximada: `adb shell pm grant <pkg> android.permission.ACCESS_COARSE_LOCATION`.
- Pasos: abrir «Conducir»; denegar con "no volver a preguntar"; conceder solo aproximada; concederlo desde Ajustes y volver; revocar con la navegación en marcha.
- Esperado: la app explica qué falta y ofrece ir a Ajustes, sin crash; con ubicación aproximada avisa de que las notas no son fiables; al conceder, arranca sin reiniciar; al revocar en marcha, para el servicio de forma ordenada y conserva el tramo.
- Evidencia: capturas de cada estado y logcat. Restaurar con `adb shell pm grant`.

## rotation
- Preparación: pantalla de conducción con navegación activa; luego análisis de tramo.
- Pasos: desactivar auto-rotación y forzar `adb shell settings put system user_rotation 1` (volver a 0); alternar con un diálogo abierto.
- Esperado: sin reinicio del cronómetro ni de la navegación; layout usable en vertical y horizontal; velocidad y límite siempre visibles.
- Evidencia: capturas en ambas orientaciones; logcat sin excepciones.

## dark-theme
- Preparación: tema de la app en oscuro (por defecto) y luego claro desde Ajustes; `adb shell cmd uimode night yes|no` para el del sistema.
- Pasos: recorrer inicio, conducción, mis tramos, análisis, mapas offline y ajustes; alternar el tema con la app abierta.
- Esperado: contraste legible (delta verde/rojo distinguible), sin texto sobre fondo del mismo tono; cambio de tema sin perder estado.
- Evidencia: capturas de cada pantalla en ambos temas.
