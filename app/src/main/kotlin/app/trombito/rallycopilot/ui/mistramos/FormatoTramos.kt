package app.trombito.rallycopilot.ui.mistramos

import app.trombito.rallycopilot.ui.comun.FormatoEs
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

private val ES = Locale.forLanguageTag("es-ES")
private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/** «850 m» por debajo del kilómetro y «12,4 km» desde él (coma decimal, vía [FormatoEs]). */
fun formatearDistanciaTramo(metros: Double): String = FormatoEs.distancia(metros)

/** «123,4 km» con una décima y coma decimal (vía [FormatoEs]). */
fun formatearKilometros(km: Double): String = FormatoEs.kilometros(km)

/** «54 km/h»: velocidad entera (vía [FormatoEs]). */
fun formatearVelocidadKmh(kmh: Double): String = FormatoEs.velocidad(kmh)

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
