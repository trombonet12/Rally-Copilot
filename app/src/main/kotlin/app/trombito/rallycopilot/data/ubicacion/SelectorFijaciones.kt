package app.trombito.rallycopilot.data.ubicacion

import app.trombito.rallycopilot.motor.velocidad.Fijacion

/** De qué proveedor viene una fijación: el GNSS (con velocidad y rumbo) o el rápido (fused/red, aproximado). */
internal enum class OrigenFijacion { GPS, RAPIDO }

/**
 * Fusión simple de dos fuentes de posición. Una fijación del GPS siempre pasa; una del proveedor rápido solo pasa si
 * el GPS no ha dado ninguna en los últimos [ventanaMs] (comparando el reloj monótono `tiempoMs` de las fijaciones).
 * Así, conduciendo, manda el GPS con su velocidad y rumbo; bajo techo, donde el GPS no fija, sale la aproximada
 * del proveedor rápido. Es seguro llamarla desde varios hilos.
 */
internal class SelectorFijaciones(
    private val ventanaMs: Long = VENTANA_GPS_MS,
) {
    private var ultimaGpsMs: Long? = null

    @Synchronized
    fun aceptar(
        fijacion: Fijacion,
        origen: OrigenFijacion,
    ): Boolean {
        val gps = ultimaGpsMs
        return if (origen == OrigenFijacion.GPS) {
            ultimaGpsMs = if (gps == null) fijacion.tiempoMs else maxOf(gps, fijacion.tiempoMs)
            true
        } else {
            gps == null || fijacion.tiempoMs - gps >= ventanaMs
        }
    }

    companion object {
        const val VENTANA_GPS_MS = 3_000L
    }
}
