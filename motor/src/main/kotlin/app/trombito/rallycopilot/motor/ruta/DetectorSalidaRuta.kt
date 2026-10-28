package app.trombito.rallycopilot.motor.ruta

/**
 * Decide que el conductor se ha salido de la ruta: la distancia a ella supera [umbralM] de forma continuada durante
 * [tiempoMs]. Para darlo por vuelto (histéresis) debe bajar de la mitad del umbral.
 */
class DetectorSalidaRuta(
    private val umbralM: Double = 40.0,
    private val tiempoMs: Long = 5000,
) {
    private var fuera = false
    private var fueraDesdeMs: Long? = null

    fun actualizar(
        distanciaALaRutaM: Double,
        ahoraMs: Long,
    ): Boolean {
        if (fuera) {
            if (distanciaALaRutaM < umbralM / 2) fuera = false
        } else if (distanciaALaRutaM > umbralM) {
            val desde = fueraDesdeMs ?: ahoraMs
            fueraDesdeMs = desde
            if (ahoraMs - desde >= tiempoMs) fuera = true
        }
        if (!fuera && distanciaALaRutaM <= umbralM) fueraDesdeMs = null
        return fuera
    }
}
