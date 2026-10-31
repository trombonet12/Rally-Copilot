package app.trombito.rallycopilot.ui.navegacion.guiado

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private const val UN_KM = 1000.0
private const val PASO_M = 10
private const val S_POR_MIN = 60
private const val MIN_POR_H = 60
private val ES = Locale.forLanguageTag("es-ES")

/** Hora de llegada «HH:mm» (24 h) en la [zona] dada; [llegadaMs] es milisegundos desde la época (reloj de pared). */
internal fun formatearHoraLlegada(
    llegadaMs: Long,
    zona: ZoneId,
): String = DateTimeFormatter.ofPattern("HH:mm", ES).withZone(zona).format(Instant.ofEpochMilli(llegadaMs))

/** Tiempo restante: «< 1 min», «12 min» o «1 h 05 min» (los minutos se truncan). */
internal fun formatearTiempoRestante(segundos: Double): String {
    val minutosTotales = (segundos.coerceAtLeast(0.0) / S_POR_MIN).toInt()
    return when {
        minutosTotales < 1 -> "< 1 min"
        minutosTotales < MIN_POR_H -> "$minutosTotales min"
        else -> String.format(ES, "%d h %02d min", minutosTotales / MIN_POR_H, minutosTotales % MIN_POR_H)
    }
}

/** Distancia restante: «12,4 km» desde 1 km y «850 m» (a 10 m) por debajo. */
internal fun formatearDistanciaRestante(metros: Double): String {
    val m = metros.coerceAtLeast(0.0)
    if (m < UN_KM) {
        val redondeado = (m / PASO_M).roundToInt() * PASO_M
        if (redondeado < UN_KM) return "$redondeado m"
    }
    return String.format(ES, "%.1f km", m / UN_KM)
}
