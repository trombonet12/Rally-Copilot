package app.trombito.rallycopilot.domain.tramos

import app.trombito.rallycopilot.motor.geo.PuntoGeo

/** Compone el nombre automático de un tramo («Ma-2141 · Escorca») a partir de su traza; null si no se puede. */
fun interface NombraTraza {
    suspend fun nombrar(traza: List<PuntoGeo>): String?
}
