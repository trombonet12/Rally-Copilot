package app.trombito.rallycopilot.motor.tramos

import app.trombito.rallycopilot.motor.geo.PuntoGeo

/** Dónde iba el fantasma: [punto] y [rumboGrados] (desde el norte) de la traza en él, a [distanciaM] de la salida. */
data class PosicionFantasma(
    val punto: PuntoGeo,
    val rumboGrados: Double,
    val distanciaM: Double,
)

/**
 * Fantasma de una [pasada] (normalmente la mejor) sobre su [tramo]: dónde iba el coche cuando el cronómetro marcaba
 * [transcurridoMs]. Es la inversa de [Pasada.tiempoEn]: interpolación lineal de la distancia entre marcas, con
 * búsqueda binaria (sin recorrer la traza ni las marcas enteras), para llamarse a 1–20 Hz.
 */
class Fantasma(
    tramo: Tramo,
    private val pasada: Pasada,
) {
    private val traza = TrazaPlana(tramo.traza, tramo.longitudM)
    private val longitudM = tramo.longitudM
    private val distancias = DoubleArray(pasada.tiempos.size) { pasada.tiempos[it].distanciaM }
    private val tiempos = LongArray(pasada.tiempos.size) { pasada.tiempos[it].tMs }

    init {
        require(pasada.tramoId == tramo.id) { "La pasada es del tramo ${pasada.tramoId}, no de ${tramo.id}" }
        require(pasada.tiempos.size >= 2) { "La pasada no tiene marcas" }
    }

    /**
     * Distancia (m desde la salida) a la que iba el fantasma a [transcurridoMs] ms de la salida: null antes de la
     * salida (instante negativo o anterior a la primera marca) y la distancia de la meta tras el final de la pasada.
     */
    fun distanciaEn(transcurridoMs: Long): Double? =
        when {
            transcurridoMs < tiempos.first() -> null
            transcurridoMs >= tiempos.last() -> distancias.last()
            else -> interpolar(transcurridoMs)
        }

    private fun interpolar(transcurridoMs: Long): Double {
        val i = primeraMarcaDesde(transcurridoMs)
        val dt = tiempos[i] - tiempos[i - 1]
        val fraccion = if (dt <= 0L) 1.0 else (transcurridoMs - tiempos[i - 1]).toDouble() / dt
        return distancias[i - 1] + fraccion * (distancias[i] - distancias[i - 1])
    }

    /** Posición del fantasma a [transcurridoMs] ms de la salida; null antes de la salida, en la meta tras el final. */
    fun posicionEn(transcurridoMs: Long): PosicionFantasma? {
        val d = distanciaEn(transcurridoMs)?.coerceIn(0.0, longitudM) ?: return null
        return PosicionFantasma(traza.puntoEn(d), traza.rumboEn(d, SEMIVENTANA_RUMBO_M), d)
    }

    /** Índice (≥ 1) de la primera marca con tiempo ≥ [t]; exige que [t] esté entre la primera y la última marca. */
    private fun primeraMarcaDesde(t: Long): Int {
        var bajo = 1
        var alto = tiempos.lastIndex
        while (bajo < alto) {
            val medio = (bajo + alto) ushr 1
            if (tiempos[medio] < t) bajo = medio + 1 else alto = medio
        }
        return bajo
    }

    private companion object {
        const val SEMIVENTANA_RUMBO_M = 10.0
    }
}
