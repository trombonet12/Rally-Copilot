package app.trombito.rallycopilot.ui.analisis

import app.trombito.rallycopilot.ui.mistramos.formatearKilometros
import app.trombito.rallycopilot.ui.tramos.formatearDelta
import java.util.Locale
import kotlin.math.abs

private val ES = Locale.forLanguageTag("es-ES")
private const val METROS_POR_KM = 1000.0
private const val MENOS = '−'

/** «+0,8 s» / «−1,2 s»: el delta con signo y la unidad. */
internal fun formatearDeltaSegundos(ms: Long): String = "${formatearDelta(ms)} s"

/** «84 km/h»: velocidad entera. */
internal fun formatearKmhEntero(kmh: Double): String = String.format(ES, "%d km/h", Math.round(kmh))

/** «84» sin unidad, para las celdas de la tabla. */
internal fun formatearKmhCelda(kmh: Double): String = String.format(ES, "%d", Math.round(kmh))

/** «3,2 km» a partir de metros. */
internal fun formatearKmDesdeM(metros: Double): String = formatearKilometros(metros / METROS_POR_KM)

/** Etiqueta del eje de distancia: «0», «2», «1,5» (kilómetros, sin unidad). */
internal fun etiquetaKm(km: Double): String =
    if (km == Math.rint(km)) String.format(ES, "%d", km.toLong()) else String.format(ES, "%.1f", km)

/** Etiqueta del eje de delta: «+5», «−5», «0» (segundos, con una décima si no son enteros). */
internal fun etiquetaDeltaS(segundos: Double): String {
    val modulo = abs(segundos)
    val texto =
        if (modulo == Math.rint(modulo)) String.format(ES, "%d", modulo.toLong()) else String.format(ES, "%.1f", modulo)
    return when {
        segundos > 0.0 -> "+$texto"
        segundos < 0.0 -> "$MENOS$texto"
        else -> texto
    }
}
