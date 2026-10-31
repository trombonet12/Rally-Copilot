package app.trombito.rallycopilot.motor.ruta

/**
 * Decide que el conductor se ha salido de la ruta: la distancia a ella supera [umbralM] de forma continuada durante
 * [tiempoMs], o circula en sentido contrario ([SeguidorRuta] lo marca tras 50 m de retroceso, ya confirmado por sí
 * mismo) durante [tiempoContrarioMs]. Para darlo por vuelto (histéresis) debe bajar de la mitad del umbral y dejar de
 * ir en sentido contrario.
 */
class DetectorSalidaRuta(
    private val umbralM: Double = 40.0,
    private val tiempoMs: Long = 3000,
    private val tiempoContrarioMs: Long = 1000,
) {
    private var fuera = false
    private var fueraDesdeMs: Long? = null
    private var contrarioDesdeMs: Long? = null

    fun actualizar(
        distanciaALaRutaM: Double,
        ahoraMs: Long,
        sentidoContrario: Boolean = false,
    ): Boolean {
        if (fuera) {
            if (distanciaALaRutaM < umbralM / 2 && !sentidoContrario) fuera = false
        } else {
            if (distanciaALaRutaM > umbralM) {
                val desde = fueraDesdeMs ?: ahoraMs
                fueraDesdeMs = desde
                if (ahoraMs - desde >= tiempoMs) fuera = true
            }
            if (sentidoContrario) {
                val desde = contrarioDesdeMs ?: ahoraMs
                contrarioDesdeMs = desde
                if (ahoraMs - desde >= tiempoContrarioMs) fuera = true
            }
        }
        if (!fuera && distanciaALaRutaM <= umbralM) fueraDesdeMs = null
        if (!sentidoContrario) contrarioDesdeMs = null
        return fuera
    }
}
