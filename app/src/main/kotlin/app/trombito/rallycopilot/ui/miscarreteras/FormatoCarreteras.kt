package app.trombito.rallycopilot.ui.miscarreteras

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val ES: Locale = Locale.forLanguageTag("es-ES")
private val FECHA: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", ES)
private const val UNA_DECIMA = 10.0

/** Curvas por kilómetro con coma decimal y sin «,0» sobrante: «7», «6,5». */
internal fun formatoCurvasPorKm(valor: Double): String {
    val decimas = (valor * UNA_DECIMA).roundToInt()
    return if (decimas % UNA_DECIMA.toInt() == 0) {
        (decimas / UNA_DECIMA.toInt()).toString()
    } else {
        String.format(ES, "%.1f", decimas / UNA_DECIMA)
    }
}

/** Un punto kilométrico con una décima y coma decimal: «20,1». */
internal fun formatoKm(km: Double): String = String.format(ES, "%.1f", km)

/** Los kilómetros enteros de una marca de la regla: «62». */
internal fun formatoMarcaKm(km: Double): String = km.roundToInt().toString()

/** Fecha corta con año: «12 nov 2026», en la zona horaria [zona]. */
internal fun formatoFecha(
    epochMs: Long,
    zona: ZoneId = ZoneId.systemDefault(),
): String = FECHA.format(Instant.ofEpochMilli(epochMs).atZone(zona))

/** La diferencia entre dos kilómetros, siempre positiva. */
internal fun largoKm(
    desde: Double,
    hasta: Double,
): Double = abs(hasta - desde)
