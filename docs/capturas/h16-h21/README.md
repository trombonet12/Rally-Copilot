# Capturas de H16–H21 (2026-11-08)

Emulador `trombito-36` (Android 16, 1080x2400), APK debug `.dev` actualizado con `install -r`, tema oscuro, vertical.
El emulador volvió a su instantánea tras un cierre, así que los recorridos de la Ma-15 de H15b no están.

## Resultado por prueba
1. **Permiso del intercom: OK.** La bienvenida (página 6) pide «Intercom del casco» junto a ubicación, micrófono y notificaciones (01).
2. **Mis carreteras: OK.** Mapa de calor de lo conducido (la Ma-2141, 5 veces), 11,4 km, 5 recorridos, 100 curvas por grado y «Carreteras con más curvas: Ma-2141 · 11,2 km · 5 veces · 99 curvas» (02).
3. **Ruta divertida: OK.** Hacia Sóller: Rápida 1 h 14 min · 26,3 km · 10,9 km de curvas; Equilibrada 1 h 26 min · 32,9 km · 19,8 km de curvas; Curvas «Misma ruta» que Equilibrada (03). Los tres chips salen en unos 4 s. Arreglado en la prueba: «10,9 km de curvas» se cortaba y ahora pasa a dos líneas.
4. **Atajo «Conducir libre»: OK.** Registrados `conducir_libre` y `repetir_ultimo` (`dumpsys shortcut`); el atajo en frío abre el mapa en libre con «Trip: 0,0 km» en la hoja (04).
5. **Tema «Día y noche»: OK.** A las 11:30 la muestra y la app pasan a claro (05).

## Lista de capturas
01 permisos · 02 Mis carreteras · 03 tres rutas · 04 atajo en frío · 05 tema Día y noche.

## No probado en el emulador
Voz por el intercom (no hay Bluetooth), fantasma y subtramos con dos pasadas reales, reconocimiento en dos pasadas, notas en pausa con GPS pobre y «dónde estoy» con punto kilométrico (la release de datos con `pk` es `datos-2026.11.08-17`). Quedan para la prueba en coche de Joan; todos tienen tests JVM.
