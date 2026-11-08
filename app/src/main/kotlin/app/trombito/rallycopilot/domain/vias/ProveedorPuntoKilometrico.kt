package app.trombito.rallycopilot.domain.vias

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.vias.PuntoKilometrico

/** Punto kilométrico de una carretera en una posición, con los hitos de OSM de los datos instalados. */
fun interface ProveedorPuntoKilometrico {
    /** Null si no hay archivo de hitos, no hay hito de esa carretera cerca o no se sabe la carretera. */
    suspend fun puntoKilometrico(
        ref: String?,
        posicion: PuntoGeo,
    ): PuntoKilometrico?

    companion object {
        /** Sin datos de hitos (archivo opcional ausente): nunca hay punto kilométrico. */
        val Ninguno = ProveedorPuntoKilometrico { _, _ -> null }
    }
}
