package app.trombito.rallycopilot.ui.alertas

import app.trombito.rallycopilot.motor.alertas.Incidencia
import app.trombito.rallycopilot.motor.alertas.IncidenciaCercana

/** Un marcador por incidencia con puntos (en el primero, o el inicio del tramo). */
internal fun marcadoresDe(cercanas: List<IncidenciaCercana>): List<MarcadorIncidencia> =
    cercanas.mapNotNull { c ->
        val punto = c.incidencia.puntos.firstOrNull() ?: return@mapNotNull null
        c.incidencia.aMarcador(punto.lat, punto.lon, c.enRuta)
    }

private fun Incidencia.aMarcador(
    lat: Double,
    lon: Double,
    enRuta: Boolean,
) = MarcadorIncidencia(id, tipo, lat, lon, descripcion, carretera, enRuta)
