package app.trombito.rallycopilot.ui.navegacion

import kotlin.math.roundToInt

private const val SEGUNDOS_POR_MINUTO = 60
private const val MINUTOS_POR_HORA = 60

/** Duración de una ruta: «23 min» o, desde una hora, «1 h 05 min». Nunca menos de «1 min». */
fun formatearDuracionRuta(segundos: Double): String {
    val minutos = (segundos.coerceAtLeast(0.0) / SEGUNDOS_POR_MINUTO).roundToInt().coerceAtLeast(1)
    val horas = minutos / MINUTOS_POR_HORA
    val resto = minutos % MINUTOS_POR_HORA
    return if (horas == 0) "$minutos min" else "$horas h ${resto.toString().padStart(2, '0')} min"
}
