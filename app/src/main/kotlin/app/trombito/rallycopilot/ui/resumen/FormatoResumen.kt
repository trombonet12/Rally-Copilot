package app.trombito.rallycopilot.ui.resumen

import app.trombito.rallycopilot.ui.comun.FormatoEs
import java.util.Locale
import kotlin.math.abs

private const val MS_POR_S = 1_000L
private const val M_POR_KM = 1_000.0
private const val S_POR_MIN = 60
private const val MIN_POR_H = 60
private const val MENOS = '−'
private val ES: Locale = Locale.forLanguageTag("es-ES")

/** «mm:ss» y, pasada la hora, «h:mm:ss» (un reloj, sin décimas). */
internal fun formatearDuracion(segundos: Long): String = FormatoEs.tiempo(segundos.coerceAtLeast(0L) * MS_POR_S, false)

/** Duración hablada: «5 min» y, desde la hora, «1 h 05 min» (minutos redondeados). */
internal fun formatearDuracionLarga(segundos: Long): String {
    val minutos = Math.round(abs(segundos).toDouble() / S_POR_MIN).toInt()
    return if (minutos < MIN_POR_H) {
        "$minutos min"
    } else {
        String.format(ES, "%d h %02d min", minutos / MIN_POR_H, minutos % MIN_POR_H)
    }
}

/** Minutos de la desviación de la llegada (redondeados): 0 = a la hora prevista. */
internal fun minutosDeDesviacion(desviacionS: Long): Int = Math.round(abs(desviacionS).toDouble() / S_POR_MIN).toInt()

/** Diferencia con tu media con signo: «+6 %» (más rápido) o «−3 %» (más lento); «0 %» si redondea a cero. */
internal fun formatearPorcentaje(pct: Double): String {
    val entero = Math.round(pct)
    return when {
        entero > 0L -> "+$entero %"
        entero < 0L -> "$MENOS${abs(entero)} %"
        else -> "0 %"
    }
}

/** Velocidad en km/h sin unidad («54»), para las tarjetas que ponen la unidad aparte. */
internal fun formatearKmhEntero(kmh: Double): String = Math.round(kmh.coerceAtLeast(0.0)).toString()

/** Kilómetros con una décima y sin unidad («31,9»), para la tarjeta que la pone aparte. */
internal fun formatearKm(metros: Double): String = String.format(ES, "%.1f", metros.coerceAtLeast(0.0) / M_POR_KM)
