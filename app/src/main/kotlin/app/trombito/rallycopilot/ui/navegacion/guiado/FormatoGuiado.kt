package app.trombito.rallycopilot.ui.navegacion.guiado

import app.trombito.rallycopilot.ui.comun.FormatoEs
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val PASO_M = 10
private const val S_POR_MIN = 60
private const val MIN_POR_H = 60
private val ES = Locale.forLanguageTag("es-ES")

/** Los `ref` que se pintan como placa (los mismos que reconoce la frase del motor); un nombre de calle no. */
private val REFERENCIA_PLACA = Regex("""^(MA|AP|PM|A|E|N|C)-\d+\w*$""", RegexOption.IGNORE_CASE)

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
internal fun formatearDistanciaRestante(metros: Double): String = FormatoEs.distanciaRedondeada(metros, PASO_M, PASO_M)

/**
 * Separa la vía del final de la [frase] de una maniobra («cruce, izquierda, Ma-10» -> «cruce, izquierda» y «Ma-10»)
 * para pintarla como placa. Solo si la frase acaba en «, ref» con un `ref` de [vias] que sea una referencia de
 * carretera; en otro caso (nombre de calle, sin vía) la frase queda entera y la vía es null.
 */
internal fun separarViaDeFrase(
    frase: String,
    vias: List<String>,
): Pair<String, String?> {
    val via =
        vias
            .map { it.trim() }
            .firstOrNull { REFERENCIA_PLACA.matches(it) && frase.endsWith(", $it") }
    return if (via == null) frase to null else frase.removeSuffix(", $via") to via
}
