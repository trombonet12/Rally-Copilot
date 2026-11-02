package app.trombito.rallycopilot.ui.mistramos

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

private const val METROS_POR_KM = 1000.0
private val ES = Locale.forLanguageTag("es-ES")
private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/** «850 m» por debajo del kilómetro y «12,4 km» desde él (coma decimal). */
fun formatearDistanciaTramo(metros: Double): String =
    if (metros < METROS_POR_KM) {
        String.format(ES, "%d m", Math.round(metros.coerceAtLeast(0.0)))
    } else {
        formatearKilometros(metros / METROS_POR_KM)
    }

/** «123,4 km» con una décima y coma decimal. */
fun formatearKilometros(km: Double): String = String.format(ES, "%.1f km", km.coerceAtLeast(0.0))

/** «54,3 km/h». */
fun formatearVelocidadKmh(kmh: Double): String = String.format(ES, "%.1f km/h", kmh.coerceAtLeast(0.0))

/** «2 nov 2026 · 18:42» en la zona dada (la del dispositivo por defecto). */
fun formatearFechaHora(
    epochMs: Long,
    zona: ZoneId = ZoneId.systemDefault(),
): String {
    val f = Instant.ofEpochMilli(epochMs).atZone(zona)
    return String.format(ES, "%d %s %d · %02d:%02d", f.dayOfMonth, MESES[f.monthValue - 1], f.year, f.hour, f.minute)
}

/** «2 nov» sin año ni hora, para ejes y listas compactas. */
fun formatearFechaCorta(
    epochMs: Long,
    zona: ZoneId = ZoneId.systemDefault(),
): String {
    val f = Instant.ofEpochMilli(epochMs).atZone(zona)
    return "${f.dayOfMonth} ${MESES[f.monthValue - 1]}"
}
