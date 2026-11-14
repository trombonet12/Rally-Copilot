package app.trombito.rallycopilot.motor.ruta

/**
 * Ritmo real del viaje en curso frente al previsto, para corregir en vivo lo que queda. Se alimenta con muestras
 * ([registrar]): distancia y tiempo de un intervalo y el tiempo previsto para esa distancia. Las paradas de más de
 * [PARADA_LARGA_S] seguidos no cuentan; las cortas (semáforos, retenciones) sí.
 */
class RitmoViaje {
    private var realS = 0.0
    private var previstoS = 0.0
    private var distanciaM = 0.0
    private var paradaS = 0.0

    /** Kilómetros recorridos en marcha. */
    val kmHechos: Double get() = distanciaM / METROS_POR_KM

    /** Intervalo de [tiempoS] en el que se avanzaron [distanciaM] metros y que se había previsto en [previstoS]. */
    fun registrar(
        distanciaM: Double,
        tiempoS: Double,
        previstoS: Double,
    ) {
        if (tiempoS <= 0.0) return
        if (distanciaM / tiempoS < VELOCIDAD_PARADO_MS) {
            paradaS += tiempoS
            return
        }
        if (paradaS <= PARADA_LARGA_S) realS += paradaS
        paradaS = 0.0
        realS += tiempoS
        this.previstoS += previstoS
        this.distanciaM += distanciaM
    }

    /** Ritmo r = real/previsto de lo recorrido, acotado a 0,7–1,6; 1 si aún no hay datos. */
    val ritmo: Double
        get() = if (previstoS <= 0.0) 1.0 else (realS / previstoS).coerceIn(RITMO_MIN, RITMO_MAX)

    /** Factor a aplicar a lo que queda: 1 + w·(r − 1), con w creciente con los km hechos. */
    val factor: Double get() = 1.0 + peso(kmHechos) * (ritmo - 1.0)

    companion object {
        const val PARADA_LARGA_S = 120.0
        const val RITMO_MIN = 0.7
        const val RITMO_MAX = 1.6
        private const val VELOCIDAD_PARADO_MS = 0.5
        private const val METROS_POR_KM = 1000.0
        private const val KM_PESO_MEDIO = 5.0
        private const val KM_PESO_ALTO = 20.0
        private const val PESO_MEDIO = 0.5
        private const val PESO_ALTO = 0.8

        /** Peso de la corrección según km hechos: 0 a 0 km, 0,5 a 5 km, 0,8 a 20 km (lineal entre medias). */
        fun peso(km: Double): Double =
            when {
                km <= 0.0 -> {
                    0.0
                }

                km <= KM_PESO_MEDIO -> {
                    PESO_MEDIO * km / KM_PESO_MEDIO
                }

                km <= KM_PESO_ALTO -> {
                    PESO_MEDIO + (PESO_ALTO - PESO_MEDIO) * (km - KM_PESO_MEDIO) / (KM_PESO_ALTO - KM_PESO_MEDIO)
                }

                else -> {
                    PESO_ALTO
                }
            }
    }
}
