package app.trombito.rallycopilot.data.tramos

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.velocidad.Fijacion

/**
 * Detecta cuándo empieza a moverse el coche en una conducción: la primera vez que la velocidad pasa de
 * [UMBRAL_VELOCIDAD_KMH] y se mantiene [SOSTENIDA_MS], o que se está a más de [DISTANCIA_MINIMA_M] de la primera
 * posición, lo que ocurra antes. Ese instante es la salida: el cronómetro de la conducción y el tramo que se crea del
 * recorrido empiezan ahí y no al entrar en el mapa. No es concurrente; el tiempo es el de las fijaciones.
 */
internal class DetectorSalida {
    private var origen: PuntoGeo? = null
    private var inicioRacha: Long? = null

    /** Tiempo (de las fijaciones) de la salida; null mientras el coche no se haya movido. */
    var salidaMs: Long? = null
        private set

    /** Procesa una fijación en orden temporal; devuelve [salidaMs]. */
    fun alFijar(f: Fijacion): Long? {
        if (salidaMs != null) return salidaMs
        val inicio = origen ?: f.punto.also { origen = it }
        val rapido = (f.velocidadMs ?: 0.0) * KMH_POR_MS > UMBRAL_VELOCIDAD_KMH
        if (!rapido) {
            inicioRacha = null
        } else if (inicioRacha == null) {
            inicioRacha = f.tiempoMs
        }
        val racha = inicioRacha
        salidaMs =
            when {
                racha != null && f.tiempoMs - racha >= SOSTENIDA_MS -> racha
                inicio.distanciaA(f.punto) > DISTANCIA_MINIMA_M -> racha ?: f.tiempoMs
                else -> null
            }
        return salidaMs
    }

    companion object {
        const val UMBRAL_VELOCIDAD_KMH = 5.0
        const val SOSTENIDA_MS = 2_000L
        const val DISTANCIA_MINIMA_M = 20.0
        private const val KMH_POR_MS = 3.6
    }
}
