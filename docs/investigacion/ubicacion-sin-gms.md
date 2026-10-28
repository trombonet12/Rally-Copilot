# Ubicación sin Google Play Services, servicio en primer plano y simulación de GPS

> Propósito: fijar cómo obtener posición, velocidad y rumbo a 1 Hz sin GMS, cómo mantenerlo con pantalla apagada cumpliendo Android 14+ y la política de Play, y cómo simular GPS en el emulador · Audiencia: agente principal y usuario · Actualizado: 2026-10-28 · Relacionado: docs/DECISIONES.md § Ubicación y servicio en primer plano, ROADMAP.md § H1, H2

## Contexto
- El móvil del usuario puede ser LineageOS sin GMS: no se puede usar `FusedLocationProviderClient`. minSdk 26, compileSdk 37.
- La app debe seguir con pantalla apagada o en segundo plano, como Google Maps (prompt-inicial §4.3). Con minSdk 26 no hay que tratar API < 26.
- `LocationManager` es API del framework: `GPS_PROVIDER` siempre existe si hay receptor GNSS. `FUSED_PROVIDER` (constante añadida en API 31) existe en el framework; la documentación de Android Automotive dice que antes solo era accesible vía Play Services y que hay una implementación de referencia en AOSP (`FusedLocationProvider.java`), pero **no he podido confirmar que esté presente y dé buenas posiciones en una ROM sin GMS** (LineageOS): VERIFICAR en el dispositivo real con `hasProvider`.
- `Location` ofrece velocidad (m/s) y rumbo con sus precisiones (`getSpeedAccuracyMetersPerSecond`, `getBearingAccuracyDegrees`, API 26) e `isMock` (API 31). La página de referencia no se cargó completa; los datos de versión salen de conocimiento general: VERIFICAR contra la referencia.
- **Servicio en primer plano** (docs de Android, 2026-10-28): tipo `location` con `android:foregroundServiceType="location"`, permisos `FOREGROUND_SERVICE` y `FOREGROUND_SERVICE_LOCATION`, `ACCESS_COARSE/FINE_LOCATION` concedido en tiempo de ejecución y ubicación activada. Desde Android 14 es obligatorio declarar el tipo. "No se puede crear un servicio location en segundo plano salvo con `ACCESS_BACKGROUND_LOCATION`": el servicio debe arrancarse con la app visible (acción del usuario).
- **Política de Play** (ubicación en segundo plano): solo para funciones centrales y con formulario de declaración, vídeo y divulgación en la app. Un servicio en primer plano puede aprobarse caso a caso si es continuación de una acción iniciada por el usuario y para en cuanto acaba; si equivale a ubicación en segundo plano, se aplican los requisitos de segundo plano. Aparte, para apps con target Android 14+ hay que declarar en Play Console (App content) cada tipo de servicio en primer plano, con descripción, impacto, vídeo y caso de uso; para `location` hay tres opciones, entre ellas **Navegación** ("el usuario inicia navegación activa o seguimiento de ruta").
- **Simulación**: consola del emulador `geo fix longitud latitud [altitud] [satélites] [velocidad en nudos]` y `geo nmea` (solo `$GPGGA` y `$GPRMC`; la doc dice `$GPRCM`, errata); se invoca con `adb emu geo fix ...`; la consola telnet exige token de `~/.emulator_console_auth_token`. Los controles extendidos permiten guardar rutas, "Load GPX/KML", velocidad de reproducción y repetición (pueden requerir la ventana independiente). La consola oficial no menciona reproducir GPX.

## Alternativas
| Alternativa | Pros | Contras | Licencia | Madurez / versión verificada |
|---|---|---|---|---|
| `LocationManager` con `GPS_PROVIDER` a 1 Hz | Sin dependencias; funciona sin GMS; velocidad y rumbo del chip GNSS; control total | Hay que filtrar saltos/sin velocidad; mala cobertura en interiores/túneles (se resuelve con map matching) | AOSP (Apache-2.0) | Framework estable |
| `LocationManager` con `FUSED_PROVIDER` | Combina fuentes, arranque más rápido de la primera posición | Incierto en ROMs sin GMS; menos control; puede estar ausente | AOSP | API 31+, disponibilidad VERIFICAR |
| `FusedLocationProviderClient` (GMS) | Estándar con GMS | Excluido por requisito; Ferrostar lo aísla en un módulo opcional (`google-play-services`) | Propietaria | No aplicable |
| Biblioteca puente (Lost) o microG | API compatible con Play Services sobre `LocationManager` / sustituto libre | Dependencia extra o instalación del usuario; mantenimiento no verificado | Apache-2.0 (Lost: VERIFICAR) | VERIFICAR |

## Recomendación
- **Fuente**: interfaz propia `LocationSource` (dominio) con implementación `GPS_PROVIDER` (1 s de intervalo, 0 m de distancia mínima; `LocationRequest.Builder` en API 31+, sobrecarga clásica por debajo) y una implementación de reproducción (GPX/JSON) para tests y emulador. Usar `FUSED_PROVIDER` solo como primera posición rápida si el dispositivo lo ofrece (opcional, tras probarlo). Usar `hasSpeed`/`hasBearing`; si no hay, derivar de dos fijaciones.
- **Servicio**: un `ForegroundService` tipo `location` con notificación persistente (canal propio), **iniciado solo desde la UI por una acción explícita** ("Empezar tramo/navegación"), parado al terminar. **No pedir `ACCESS_BACKGROUND_LOCATION`**: no hace falta con servicio en primer plano y evita la revisión de Play. Declarar el uso (Navegación) en Play Console con vídeo. Notificación y `POST_NOTIFICATIONS` (API 33): VERIFICAR comportamiento si se deniega.
- **Pantalla apagada**: el servicio mantiene las actualizaciones; no usar wake lock salvo que las pruebas muestren cortes (VERIFICAR en LineageOS real con 30+ min y pantalla apagada). Audio/TTS y ducking: investigación aparte.
- **Pruebas**: (1) emulador con una imagen de sistema **sin Google APIs** para comprobar que no hay dependencia de GMS (VERIFICAR nombre de imagen); (2) script en `scripts/` que lee un GPX de Mallorca y emite `adb emu geo fix` a 1 Hz (velocidad en nudos = m/s x 1,944); (3) alternativa en debug: `LocationSource` de reproducción sin tocar la consola; (4) en dispositivo físico, proveedor de prueba (`addTestProvider`/`setTestProviderLocation`) tras elegir la app como "aplicación de ubicación simulada" en Opciones de desarrollador o con `adb shell appops set <paquete> android:mock_location allow` (comando habitual, **no confirmado en la documentación oficial leída**: VERIFICAR); marcar las ubicaciones con `elapsedRealtimeNanos` y precisión.

## Consecuencias
- Positivas: sin GMS ni SDK propietario; lógica testeable al aislar la fuente; superficie de permisos mínima.
- Negativas / costes: más trabajo propio de filtrado y arranque en frío (la primera fijación GNSS puede tardar); en Android 12+ no se puede arrancar el servicio desde segundo plano (restricción); ROMs con ahorro agresivo de batería pueden matar el servicio (VERIFICAR en el móvil del usuario).
- Seguimiento: prueba en LineageOS (FUSED_PROVIDER, 1 Hz real, pantalla apagada); preparar vídeo y texto del formulario de servicios en primer plano de Play; revisar si la "grabación automática" (§4.7) puede cumplir "iniciado por el usuario".

## ¿Afecta al producto?
Sí → QUESTIONS.md § Grabación automática y segundo plano: Play exige que el servicio de ubicación nazca de una acción del usuario y pare al terminar. ¿Se acepta que la "grabación automática de trayectos" empiece solo al pulsar Conducir/Empezar (y no al detectar movimiento con la app cerrada)? Así se evita pedir ubicación en segundo plano. Y: ¿aceptas una notificación persistente mientras se navega o graba?

## Fuentes
- https://developer.android.com/develop/background-work/services/fgs/service-types (2026-10-28)
- https://developer.android.com/develop/sensors-and-location/location/background (2026-10-28)
- https://support.google.com/googleplay/android-developer/answer/9799150 (política de ubicación en segundo plano, 2026-10-28)
- https://support.google.com/googleplay/android-developer/answer/13392821 (declaración de servicios en primer plano, 2026-10-28)
- https://developer.android.com/reference/android/location/LocationManager y .../Location (carga parcial; datos de API a verificar, 2026-10-28)
- https://developer.android.com/training/cars/apps/coarse-location (nota sobre FUSED_PROVIDER, vía resultado de búsqueda, 2026-10-28)
- https://developer.android.com/studio/run/emulator-console (geo fix, geo nmea, adb emu; 2026-10-28)
- https://developer.android.com/studio/run/emulator-extended-controls (GPX/KML, rutas; 2026-10-28)
- https://raw.githubusercontent.com/stadiamaps/ferrostar/main/android/README.md (módulo GMS opcional; 2026-10-28)
