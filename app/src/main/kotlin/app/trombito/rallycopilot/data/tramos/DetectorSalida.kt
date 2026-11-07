package app.trombito.rallycopilot.data.tramos

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.motor.velocidad.PrecisionGps
import kotlin.math.max

/**
 * Detecta cuándo empieza a moverse el coche en una conducción: la primera vez que la velocidad pasa de
 * [UMBRAL_VELOCIDAD_KMH] y se mantiene [SOSTENIDA_MS], o que se está a más de max([DISTANCIA_MINIMA_M], 2 × la
 * precisión de la fijación) de la primera posición, lo que ocurra antes. Ese instante es la salida: el cronómetro de la
 * conducción y el tramo que se crea del recorrido empiezan ahí y no al entrar en el mapa.
 *
 * Solo cuentan las fijaciones con precisión conocida y de [PrecisionGps.SALIDA_M] o mejor (la deriva del GPS en parado
 * da saltos y velocidades falsas con precisión mala); el resto se ignoran, también como origen: el origen es la
 * primera fijación válida. No es concurrente; el tiempo es el de las fijaciones.
 */
internal class DetectorSalida {
    private var origen: PuntoGeo? = null
    private var inicioRacha: Long? = null

    /** Tiempo (de las fijaciones) de la salida; null mientras el coche no se haya movido. */
    var salidaMs: Long? = null
        private set

    /** Procesa una fijación en orden temporal; devuelve [salidaMs]. */
    fun alFijar(f: Fijacion): Long? {
        val precision = f.precisionM
        if (salidaMs == null && precision != null && precision <= PrecisionGps.SALIDA_M) evaluar(f, precision)
        return salidaMs
    }

    private fun evaluar(
        f: Fijacion,
        precision: Double,
    ) {
        val inicio = origen ?: f.punto.also { origen = it }
        val rapido = (f.velocidadMs ?: 0.0) * KMH_POR_MS > UMBRAL_VELOCIDAD_KMH
        if (!rapido) {
            inicioRacha = null
        } else if (inicioRacha == null) {
            inicioRacha = f.tiempoMs
        }
        val racha = inicioRacha
        val distanciaMinimaM = max(DISTANCIA_MINIMA_M, FACTOR_PRECISION * precision)
        salidaMs =
            when {
                racha != null && f.tiempoMs - racha >= SOSTENIDA_MS -> racha
                inicio.distanciaA(f.punto) > distanciaMinimaM -> racha ?: f.tiempoMs
                else -> null
            }
    }

    companion object {
        /** Velocidad (km/h) por encima de la cual el coche se considera en marcha. */
        const val UMBRAL_VELOCIDAD_KMH = 15.0

        /** Tiempo que debe mantenerse la velocidad sobre el umbral. */
        const val SOSTENIDA_MS = 3_000L

        /** Distancia mínima (m) al origen para dar la salida, o el doble de la precisión si es mayor. */
        const val DISTANCIA_MINIMA_M = 50.0

        private const val FACTOR_PRECISION = 2.0
        private const val KMH_POR_MS = 3.6
    }
}
