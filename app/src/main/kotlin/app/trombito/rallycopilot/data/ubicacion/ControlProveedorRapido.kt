package app.trombito.rallycopilot.data.ubicacion

/**
 * Decide cuándo sobra el proveedor rápido (fused/red) para ahorrar batería (auditoría de noviembre de 2026, REN-3):
 * con el GPS entregando fijaciones precisas el rápido solo mantiene Wi-Fi y móvil encendidos y sus fijaciones se
 * descartan, así que se quita tras [seguidasParaQuitar] fijaciones del GPS seguidas con error ≤ [precisionMaxM], y se
 * vuelve a poner cuando pasan más de [sinGpsMs] sin ninguna fijación del GPS. Es seguro llamarla desde varios hilos
 * (el listener del GPS y la vigilancia corren en hilos distintos); el tiempo es el reloj monótono que se le pasa.
 */
internal class ControlProveedorRapido(
    private val seguidasParaQuitar: Int = SEGUIDAS_PARA_QUITAR,
    private val precisionMaxM: Double = PRECISION_GPS_UTIL_M,
    private val sinGpsMs: Long = SIN_GPS_MS,
) {
    private var activo = true
    private var seguidas = 0
    private var ultimaGpsMs: Long? = null

    /** Anota una fijación del GPS; `true` si hay que quitar el proveedor rápido. */
    @Synchronized
    fun alGps(
        precisionM: Double?,
        ahoraMs: Long,
    ): Boolean {
        ultimaGpsMs = ahoraMs
        seguidas = if (precisionM != null && precisionM <= precisionMaxM) seguidas + 1 else 0
        if (!activo || seguidas < seguidasParaQuitar) return false
        activo = false
        return true
    }

    /** Se llama periódicamente; `true` si hay que volver a poner el proveedor rápido (el GPS lleva callado). */
    @Synchronized
    fun alVigilar(ahoraMs: Long): Boolean {
        val ultima = ultimaGpsMs
        if (activo || ultima == null || ahoraMs - ultima <= sinGpsMs) return false
        activo = true
        seguidas = 0
        return true
    }

    companion object {
        const val SEGUIDAS_PARA_QUITAR = 3

        /** Error máximo (m) del GPS para dar el proveedor rápido por innecesario. */
        const val PRECISION_GPS_UTIL_M = 20.0
        const val SIN_GPS_MS = 5_000L
    }
}
