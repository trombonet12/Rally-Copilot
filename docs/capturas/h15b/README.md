# Capturas de H15b (2026-11-08)

Emulador `trombito-36` (Android 16, 1080x2400), APK debug `.dev` actualizado con `install -r`, tema oscuro, vertical.
Traza `docs/rutas/ma-20-via-cintura-tramo.gpx` (Ma-15 hacia Palma con el radar de tramo `osm-r20234638`) y
`ma-13-santa-maria-palma.gpx`, con `gps-replay`.

## Resultado por prueba
1. **Media del radar de tramo en modo libre: OK.** Primera pasada a 90 km/h: «Radar de tramo 100», «media 87» y al final «FIN 87» (01, 02). Segunda a 110: «media 106» y la píldora con borde de exceso.
2. **Coche por encima de la píldora: OK.** Con «110», límite y la caja de la media, el coche queda por encima de la zona inferior en todas las capturas (01, 03, 07).
3. **Ajuste al camino: OK en la traza.** La Ma-15 pasa sobre varias calles y la Ma-30; el coche sigue la vía sin saltos ni giros en las 20 capturas (07). La prueba de verdad es en coche (Joan).
4. **Subtramos: OK.** La primera conducción crea el tramo «sa Casa Blanca → Son Ferriol» (5,1 km). La segunda empieza en el km 1 a 110: «Subtramo · 825 m … 3,8 km» con el delta en vivo (▼ −28,4) (03) y, en el detalle del tramo, «Subtramos: km 1,0 → 5,1 · 02:18,1 · −29,3» (04).
5. **Centrar en marcha: OK.** A 100 km/h, tras desplazar el mapa aparece «Centrar en mi posición» (05) y a los 10 s sin tocar el mapa vuelve a seguir al coche (06).

## Lista de capturas
01 media del tramo · 02 fin del tramo · 03 subtramo en vivo · 04 subtramos en el detalle · 05 centrar en marcha · 06 vuelve a seguir · 07 recorrido completo (mosaico).

## Observaciones
- La segunda conducción no creó su propio tramo: el emulador «teletransporta» la posición del final de la traza al km 1 al empezar, y esa primera fijación lejana rompe la pasada del tramo nuevo. En coche no hay saltos así; queda anotado en ESTADO para comprobar con datos reales.
