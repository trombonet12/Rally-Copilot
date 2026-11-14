package app.trombito.rallycopilot.motor.vias

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.TrazaMetros
import java.util.Arrays

/** Calibración: a [posM] metros del origen del eje el hito marca [km]. */
internal data class AnclaKm(
    val posM: Double,
    val km: Double,
)

/**
 * Eje lineal de una carretera: una polilínea con origen y sentido fijos que da un punto kilométrico a cada posición.
 *
 * - [puntos] va del origen al otro extremo. [pk] proyecta una posición sobre el eje y devuelve los km desde el origen.
 * - Con calibración ([deHitos], al menos dos hitos de OSM proyectados sobre el eje) el PK se interpola entre hitos
 *   (desplazamiento y escala por tramos) y, fuera de ellos, sigue con 1 km por km desde el hito más cercano.
 * - Sin hitos el PK es la distancia a lo largo del eje desde el origen, en km (estimado).
 */
class EjeCarretera internal constructor(
    val ref: String,
    val puntos: List<PuntoGeo>,
    anclas: List<AnclaKm>,
) {
    private val traza = TrazaMetros(puntos)
    private val posiciones = anclas.map { it.posM }.toDoubleArray()
    private val kilometros = anclas.map { it.km }.toDoubleArray()

    val longitudM: Double get() = traza.longitudM

    /** El PK sale de hitos de OSM (calibrado); si no, es una estimación por distancia desde el origen. */
    val deHitos: Boolean get() = posiciones.size >= 2

    /** PK (km) de la posición a [metros] del origen del eje. */
    fun pkEn(metros: Double): Double =
        when {
            posiciones.isEmpty() -> metros / METROS_POR_KM
            metros <= posiciones.first() -> kilometros.first() + (metros - posiciones.first()) / METROS_POR_KM
            metros >= posiciones.last() -> kilometros.last() + (metros - posiciones.last()) / METROS_POR_KM
            else -> interpolar(metros)
        }

    private fun interpolar(metros: Double): Double {
        val b = Arrays.binarySearch(posiciones, metros)
        if (b >= 0) return kilometros[b]
        val i = -b - 2
        val t = (metros - posiciones[i]) / (posiciones[i + 1] - posiciones[i])
        return kilometros[i] + t * (kilometros[i + 1] - kilometros[i])
    }

    /** PK de [punto] proyectado sobre el eje; null si queda a más de [maxDistanciaM] de él. */
    fun pk(
        punto: PuntoGeo,
        maxDistanciaM: Double = MAX_DISTANCIA_M,
    ): Double? {
        val proyeccion = traza.proyectar(punto)?.takeIf { it.distanciaM <= maxDistanciaM } ?: return null
        return pkEn(proyeccion.metros)
    }

    companion object {
        private const val METROS_POR_KM = 1_000.0

        /** Distancia máxima de una posición al eje para proyectarla: cubre la otra calzada de una autovía. */
        const val MAX_DISTANCIA_M = 200.0
    }
}
