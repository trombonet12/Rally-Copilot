package app.trombito.rallycopilot.motor.vias

import app.trombito.rallycopilot.motor.geo.ProyeccionPlana
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlin.math.abs
import kotlin.math.hypot

/** Punto kilométrico [km] (con decimales si se ha interpolado) de la carretera [ref]. */
data class PuntoKilometrico(
    val ref: String,
    val km: Double,
)

/**
 * Hitos kilométricos de OSM para decir «Ma-10, kilómetro veintitrés». [buscar] da el kilómetro de la carretera dada en
 * una posición:
 *
 * - Solo cuentan los hitos de la misma carretera (la `ref` se compara sin mayúsculas, espacios ni guiones, pero
 *   `Ma-13A` no es `Ma-13`; de una `ref` con varias, `Ma-13;Ma-13A`, se usa la primera, la que dice el copiloto).
 * - Se toma el hito más cercano, que debe estar a menos de [MAX_DISTANCIA_M] (con menos densidad el dato no es fiable).
 * - Si la posición queda entre ese hito y otro de distinto km (a lo sumo [MAX_SALTO_KM] de diferencia), se interpola
 *   linealmente por su proyección sobre el segmento que los une; si no, vale el km del hito.
 */
class IndicePuntosKm(
    hitos: List<HitoKm>,
) {
    private val porRef: Map<String, List<HitoKm>> = hitos.groupBy { normalizar(it.ref) }

    val tamano: Int = hitos.size

    fun buscar(
        ref: String?,
        posicion: PuntoGeo,
    ): PuntoKilometrico? {
        val primera = ref?.substringBefore(';')?.trim().orEmpty()
        val candidatos = porRef[normalizar(primera)]
        val plano = ProyeccionPlana(posicion)
        val cercano = candidatos?.minByOrNull { distancia(plano, it) }
        return if (cercano == null || distancia(plano, cercano) > MAX_DISTANCIA_M) {
            null
        } else {
            PuntoKilometrico(cercano.ref, kilometro(plano, cercano, candidatos))
        }
    }

    private fun distancia(
        plano: ProyeccionPlana,
        hito: HitoKm,
    ): Double = hypot(plano.x(hito.punto), plano.y(hito.punto))

    private fun kilometro(
        plano: ProyeccionPlana,
        cercano: HitoKm,
        candidatos: List<HitoKm>,
    ): Double {
        val cx = plano.x(cercano.punto)
        val cy = plano.y(cercano.punto)
        var mejorDistancia = Double.POSITIVE_INFINITY
        var kilometro = cercano.km
        for (otro in candidatos) {
            val salto = abs(otro.km - cercano.km)
            val d = distancia(plano, otro)
            val utilizable = salto > 0.0 && salto <= MAX_SALTO_KM && d <= MAX_DISTANCIA_M
            if (!utilizable || d >= mejorDistancia) continue
            // La posición es el origen del plano: t = (P - C)·(O - C) / |O - C|²; en (0, 1) queda entre ambos hitos.
            val dx = plano.x(otro.punto) - cx
            val dy = plano.y(otro.punto) - cy
            val t = (-cx * dx - cy * dy) / (dx * dx + dy * dy)
            if (t > 0.0 && t < 1.0) {
                mejorDistancia = d
                kilometro = cercano.km + t * (otro.km - cercano.km)
            }
        }
        return kilometro
    }

    companion object {
        const val MAX_DISTANCIA_M = 1_500.0
        const val MAX_SALTO_KM = 2.0

        /** `Ma-13A` -> `MA13A`: sin mayúsculas, espacios, guiones ni otros signos. */
        internal fun normalizar(ref: String): String = ref.filter { it.isLetterOrDigit() }.uppercase()
    }
}
