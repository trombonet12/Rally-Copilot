# Checklist por área (security-review)

Cada fila es una comprobación; un incumplimiento es un finding con la severidad indicada.

| Área | Comprobación | Sev. |
|------|--------------|------|
| secrets | Secretos solo en `~/.rallycopilot/`, variables de entorno o secrets de GitHub; `.gitignore` cubre `local.properties`, `.env*`, `*.jks`, `*.keystore` | BLOCKER |
| secrets | `RALLY_KEYSTORE_*` y `RALLY_KEY_*` solo vía entorno; `build.gradle.kts` no contiene valores de firma | HIGH |
| secrets | Los scripts no imprimen credenciales; la app no lleva claves de API (no hay backend propio) | HIGH |
| android-permissions | Solo permisos necesarios (ubicación precisa/aproximada, `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`, `INTERNET` para mapas e incidencias, `RECORD_AUDIO` solo si hay edición por voz); cada uno justificado | HIGH |
| android-permissions | Sin `ACCESS_BACKGROUND_LOCATION` salvo decisión registrada; sin contactos, almacenamiento amplio ni cuentas | HIGH |
| components | Componentes `exported` mínimos; receivers sin confiar en extras del intent; el servicio de ubicación no exportado | HIGH |
| components | `PendingIntent` con `FLAG_IMMUTABLE` (o mutable justificado) e intents explícitos | HIGH |
| components | `allowBackup`/`dataExtractionRules` no exponen trazas ni tramos sin decisión; `usesCleartextTraffic` desactivado (descargas por HTTPS) | HIGH |
| location | Ubicación precisa solo mientras hay conducción/reconocimiento activos (foreground service visible) y se libera al parar | HIGH |
| location | Trazas, tramos y posiciones se guardan solo en el dispositivo; nunca se envían a red (las peticiones de mapas e incidencias no incluyen la posición exacta salvo lo imprescindible y decidido) | HIGH |
| location | Pérdida de permiso en marcha: el servicio se para ordenadamente sin crash | MEDIUM |
| privacy | Sin cuentas, backend ni SDKs de analítica o de terceros; política de privacidad coherente con lo anterior | HIGH |
| privacy | Notificación del servicio sin datos sensibles en pantalla bloqueada (solo estado del tramo) | LOW |
| logging | Sin coordenadas reales, tokens ni claves en logs; `Log.v/d/i` eliminados en release por R8 | HIGH |
| ci | `permissions:` mínimos por workflow/job; acciones de terceros fijadas por SHA; sin `pull_request_target` con checkout de código externo | HIGH |
| ci | `android.yml`: secretos solo vía `secrets.*`, sin `echo`; keystore en `$RUNNER_TEMP` con `umask 077` y borrado al final; disparo solo `push` a main, `pull_request` y `workflow_dispatch` | HIGH |
