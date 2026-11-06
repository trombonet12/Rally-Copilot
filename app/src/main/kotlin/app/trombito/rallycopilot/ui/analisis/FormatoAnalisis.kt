package app.trombito.rallycopilot.ui.analisis

import app.trombito.rallycopilot.ui.comun.FormatoEs
import app.trombito.rallycopilot.ui.tramos.formatearDelta
import kotlin.math.abs

private const val MENOS = '−'

/** «+0,8 s» / «−1,2 s»: el delta con signo y la unidad. */
internal fun formatearDeltaSegundos(ms: Long): String = "${formatearDelta(ms)} s"

/** «84 km/h»: velocidad entera. */
internal fun formatearKmhEntero(kmh: Double): String = FormatoEs.velocidad(kmh)

/** «84» sin unidad, para las celdas de la tabla. */
internal fun formatearKmhCelda(kmh: Double): String = Math.round(kmh).toString()

/** «3,2 km» a partir de metros. */
internal fun formatearKmDesdeM(metros: Double): String = FormatoEs.kilometrosDesdeMetros(metros)

/** Etiqueta del eje de distancia: «0», «2», «1,5» (kilómetros, sin unidad). */
internal fun etiquetaKm(km: Double): String = if (km == Math.rint(km)) km.toLong().toString() else FormatoEs.decimal(km)

/** Etiqueta del eje de delta: «+5», «−5», «0» (segundos, con una décima si no son enteros). */
internal fun etiquetaDeltaS(segundos: Double): String {
    val modulo = abs(segundos)
    val texto =
        if (modulo == Math.rint(modulo)) modulo.toLong().toString() else FormatoEs.decimal(modulo)
    return when {
        segundos > 0.0 -> "+$texto"
        segundos < 0.0 -> "$MENOS$texto"
        else -> texto
    }
}
