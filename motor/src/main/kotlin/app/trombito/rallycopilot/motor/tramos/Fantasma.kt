package app.trombito.rallycopilot.motor.tramos

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlin.math.roundToLong

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
 *
 * Con [deTrozo] el cronómetro arranca al pasar la pasada por `desdeM` (desfase), no en la salida del tramo: sirve para
 * el fantasma de un subtramo. Un fantasma de trozo no existe antes del trozo ni después de su final (null).
 */
class Fantasma private constructor(
    tramo: Tramo,
    private val pasada: Pasada,
    desdeM: Double?,
    hastaM: Double?,
) {
    /** Fantasma de la pasada completa: el cronómetro arranca en la salida del tramo. */
    constructor(tramo: Tramo, pasada: Pasada) : this(tramo, pasada, null, null)

    private val traza = TrazaPlana(tramo.traza, tramo.longitudM)
    private val longitudM = tramo.longitudM
    private val distancias = DoubleArray(pasada.tiempos.size) { pasada.tiempos[it].distanciaM }
    private val tiempos = LongArray(pasada.tiempos.size) { pasada.tiempos[it].tMs }

    /** Desfase (ms de la pasada) en el que arranca el cronómetro y duración del trozo (null: pasada completa). */
    private val desfaseMs: Long
    private val duracionMs: Long?

    init {
        require(pasada.tramoId == tramo.id) { "La pasada es del tramo ${pasada.tramoId}, no de ${tramo.id}" }
        require(pasada.tiempos.size >= 2) { "La pasada no tiene marcas" }
        if (desdeM == null) {
            desfaseMs = 0L
            duracionMs = null
        } else {
            val t0 = requireNotNull(pasada.tiempoEn(desdeM)) { "La pasada no cubre el inicio del trozo ($desdeM m)" }
            val fin = pasada.hastaAcotado(hastaM ?: pasada.tiempos.last().distanciaM)
            val t1 = requireNotNull(pasada.tiempoEn(fin)) { "La pasada no cubre el final del trozo ($fin m)" }
            require(t1 > t0) { "El trozo [$desdeM, $fin] no avanza en la pasada" }
            desfaseMs = t0.roundToLong()
            duracionMs = t1.roundToLong() - desfaseMs
        }
    }

    /** Duración (ms) del trozo del fantasma; null en el fantasma de la pasada completa. */
    val duracionTrozoMs: Long? get() = duracionMs

    /**
     * Distancia (m desde la salida del tramo) a la que iba el fantasma a [transcurridoMs] ms del inicio del cronómetro:
     * null antes de la salida (instante negativo o anterior a la primera marca) y la distancia de la meta tras el
     * final de la pasada. En un fantasma de trozo, null fuera de `[0, duración]`.
     */
    fun distanciaEn(transcurridoMs: Long): Double? {
        if (duracionMs != null && (transcurridoMs < 0L || transcurridoMs > duracionMs)) return null
        val t = desfaseMs + transcurridoMs
        return when {
            t < tiempos.first() -> null
            t >= tiempos.last() -> distancias.last()
            else -> interpolar(t)
        }
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

    companion object {
        private const val SEMIVENTANA_RUMBO_M = 10.0

        /**
         * Fantasma del trozo [desdeM]..[hastaM] (metros desde la salida de [tramo]) de [pasada]: el cronómetro marca 0
         * al pasar la pasada por [desdeM]. Sin [hastaM], el trozo llega hasta el final de la pasada (para subtramos en
         * curso, cuyo final aún no se conoce). Falla si la pasada no es del tramo o no cubre el trozo.
         */
        fun deTrozo(
            tramo: Tramo,
            pasada: Pasada,
            desdeM: Double,
            hastaM: Double? = null,
        ): Fantasma = Fantasma(tramo, pasada, desdeM, hastaM)
    }
}
