# Capturas de H15 (2026-11-07)

Emulador `trombito-36` (Android 16, 1080x2400), APK debug `.dev` instalado en limpio (`uninstall` + `install -g`), con red, tema oscuro, vertical. Trazas: `docs/rutas/ma-2141-sa-calobra.gpx` y `ma-13-santa-maria-palma.gpx` con `gps-replay`. El audio no se oye en el emulador: se comprobó con `dumpsys audio` (AudioTrack `started` del paquete) y con logcat (etiqueta `CopilotoVoz`, solo en debug).

## Resultado por prueba
1. **Voz Dave con las notas: OK.** Ma-2141 a 60 km/h: `dice id=nota-...` a 84-99 m y AudioTrack activo en ráfagas.
2. **GPS débil: OK** (pérdida simulada con `cmd location set-location-enabled false`, el emulador sigue emitiendo la última posición). A ~11 s banda ámbar «Sin señal GPS» (12, 13). Con 15 s: ningún audio ni al perder ni al recuperar. Con 40 s: un único audio a los ~30 s y otro a los ~7 s de reanudar (los avisos del GPS no pasan por el log de `CopilotoVoz`; son AudioTrack del paquete, sin otra app sonando). Al volver la señal la banda desaparece.
3. **Botones: OK.** Parado: capas, notas grandes, micrófono, modo y silencio (los tres últimos más grandes) y «Centrar en mi posición» tras desplazar el mapa (08, 14). A 60 km/h solo quedan micrófono, modo, silencio y el de orientación (10). ✕ con crono: «Toca otra vez para terminar» y el segundo toque termina (09). No probado: ✕ con grabación sin crono.
4. **Banner, señal y ficha: OK.** «IZQUIERDA 6 · 40 m» con la longitud atenuada y la distancia a la curva en negrita (10, 11); señal gris «?» (10); ficha «Luego» guiando hacia Sóller (16); pastilla de exceso en Ma-13 a 140 con límite 120: borde rojo que pulsa 3 veces (opacidad medida 202, 94, 163, 159, 99, 220, 99, 150, 224) y queda fijo (18).
5. **Ajustes > Voz: OK.** Interruptores con tachado en vivo en la cabecera (06), vista previa normal/rápida (la rápida dura la mitad), Sharvard: Descargar (21 MB) con «Descargando… 14 %» (04), «Lista, funciona sin conexión» (05) y suena. Hoja del copiloto en el mapa con los mismos controles (19, 20, 21).
6. **Capas > relieve: OK.** Aviso «Descargando relieve y curvas de nivel…» (23) y luego relieve y curvas visibles en la Tramuntana (25); `relieve-mallorca.pmtiles` y `curvas-mallorca.pmtiles` quedan en `files/mapa`. Mapas offline: tarjeta «Más datos» con Relieve y Voz Sharvard; borrar pide confirmación (34), «No borrar» conserva, «Borrar» elimina y la voz vuelve a «Descargar» (33).
7. **Mis tramos: OK.** Tramo «Consell → Es Garrovers» con «Ma-13» de subtítulo (28, 29b). Compartir abre el selector con `consell-es-garrovers.gpx` y el `Intent` lleva `clip=text/uri-list U(content)` (30). Elegir «Rally Copilot» entrega un `ACTION_SEND application/gpx+xml` con content:// y sale «¿Importar «Consell → Es Garrovers» (12,2 km)?» con Cancelar/Importar (31). No probado el `VIEW` aparte.
8. **Ajustes > Datos: OK.** «Guarda tus tramos, recorridos, la ubicación de Casa y tus ajustes en un archivo zip.» (32).
9. **Pantalla apagada 5 min: OK.** Ma-2141 a 60 km/h, 305 s con la pantalla `Asleep`: frases de 5 a 67 y notificación «Conducción libre» de «500 m» a «5,4 km grabados» (36). Sin FATAL ni ANR. Al encender, el mapa sigue sin saltos (35).

## Lista de capturas
00 y 01 inicio · 02-06 Ajustes > Voz · 07-08 mapa parado · 09 ✕ segundo toque · 10-11 banner y botones a 60 km/h · 12-13 GPS débil (7 s, 11 s, reanuda) · 14 recentrar · 15-16 ruta a Sóller y ficha «Luego» · 18 exceso · 19-21 hoja del copiloto · 22-25 capas y relieve · 26-29b tramos · 30-31 compartir e importar · 32 copia · 33-34 mapas offline · 35-36 pantalla apagada y notificación.

## Fallos y cosas que no parecen app final
- **FALLO texto:** Ajustes > Datos dice «Mapa de Mallorca y satélite descargados.» y en Mapas offline pone «Sin satélite (se descarga aparte)». Reproducir: instalación limpia, Ajustes > Datos (32 y 33).
- **Paneles translúcidos:** el banner de nota y la tarjeta de maniobra dejan ver las etiquetas de la carretera y los puntos del mapa por detrás («Ma-2141» bajo «DERECHA 5», 11 y 14). La memoria pide paneles con fondo negro opaco.
- **Aviso de ayuda persistente:** en Mis tramos, «Marca con la estrella…» seguía visible tras tocar «Entendido» y reaparece detrás del diálogo de importar (31).
- **Tramo de conducción libre sin extremos conocidos** (Ma-2141, 2,7 km): nombre solo «Ma-2141», sin «Origen → Destino» (27); probablemente por diseño.
- **Marcador de nota sobre la flecha del coche** (25) y la ficha de la siguiente nota a la derecha del banner se corta contra el borde (10, 14).
- El diálogo «No location access» (Google Play) tapa el mapa al cortar el GPS: es del sistema (12).
- Con teletransporte del emulador (cambio de traza) el contador de km suma el salto (3,9 km a los 11 s en 10).
