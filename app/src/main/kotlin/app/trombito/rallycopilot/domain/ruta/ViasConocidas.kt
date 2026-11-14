package app.trombito.rallycopilot.domain.ruta

import app.trombito.rallycopilot.motor.geo.PuntoGeo

/**
 * Las vías que Joan ya ha conducido (de sus recorridos grabados). El enrutador la usa para preferir, con poco coste de
 * tiempo, rutas que pasen por carreteras que conoce.
 */
interface ViasConocidas {
    /** Metros de [puntos] (la polilínea de una ruta) que discurren por vías ya conducidas; 0 si no hay histórico. */
    suspend fun metrosConocidos(puntos: List<PuntoGeo>): Double

    /** Si hay algún histórico que medir; sin él el enrutador se ahorra calcular candidatas para la Rápida. */
    suspend fun hayHistorial(): Boolean = true

    /** Sin histórico: nada es conocido y las rutas se eligen como siempre. */
    object Ninguna : ViasConocidas {
        override suspend fun metrosConocidos(puntos: List<PuntoGeo>): Double = 0.0

        override suspend fun hayHistorial(): Boolean = false
    }
}
