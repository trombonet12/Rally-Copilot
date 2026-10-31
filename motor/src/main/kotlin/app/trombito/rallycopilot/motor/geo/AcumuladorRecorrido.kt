package app.trombito.rallycopilot.motor.geo

/**
 * Regla única de la distancia de un recorrido grabado. El primer punto se graba; cada punto siguiente se graba y suma
 * su paso solo si está a [PASO_MIN_M] o más del último punto grabado (se mide siempre contra el último grabado, no
 * contra el descartado). Lo que queda más cerca se descarta: es GPS parado o deriva. Así la traza guardada, la
 * distancia de la notificación y la de [distanciaRecorrido] coinciden, y aplicar la regla a puntos ya filtrados da la
 * misma distancia. Determinista y sin Android.
 *
 * No hay excepción por velocidad: a marcha lenta (≥ 2 m/s, un punto por segundo) se graba un punto cada 3 s como
 * mucho, cadencia suficiente y sin complicar la regla para la lista.
 */
class AcumuladorRecorrido {
    private var ultimo: PuntoGeo? = null

    /** Metros acumulados por los puntos grabados. */
    var distanciaM: Double = 0.0
        private set

    /** Devuelve true si [punto] se graba (y, salvo el primero, suma su paso a [distanciaM]). */
    fun anadir(punto: PuntoGeo): Boolean {
        val paso = ultimo?.distanciaA(punto)
        val seGraba = paso == null || paso >= PASO_MIN_M
        if (seGraba) {
            if (paso != null) distanciaM += paso
            ultimo = punto
        }
        return seGraba
    }

    companion object {
        /** Un paso menor que esto se toma por ruido del GPS parado. */
        const val PASO_MIN_M = 5.0
    }
}

/** Distancia del recorrido con la regla de [AcumuladorRecorrido]. */
fun List<PuntoGeo>.distanciaRecorrido(): Double {
    val acumulador = AcumuladorRecorrido()
    for (punto in this) acumulador.anadir(punto)
    return acumulador.distanciaM
}
