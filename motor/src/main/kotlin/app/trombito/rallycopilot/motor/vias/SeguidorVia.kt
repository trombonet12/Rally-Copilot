package app.trombito.rallycopilot.motor.vias

import app.trombito.rallycopilot.motor.geo.PuntoGeo

/**
 * Sigue la vía por la que se circula con histéresis: mantiene la vía actual mientras siga dentro del radio, salvo
 * que otra puntúe al menos [margenMetros] mejor (distancia más penalización de rumbo). Evita saltos entre calles
 * paralelas o en cruces por ruido del GPS. No es seguro entre hilos.
 */
class SeguidorVia(
    private val indice: IndiceVias,
    private val margenMetros: Double = 5.0,
    private val radioMetros: Double = 25.0,
) {
    private var viaActualId: Long? = null

    /** Vía actual para la posición dada, o null si no hay ninguna dentro del radio (se olvida la vía anterior). */
    fun actualizar(
        punto: PuntoGeo,
        rumboGrados: Double?,
    ): Coincidencia? {
        val mejor = indice.buscar(punto, rumboGrados, radioMetros, null)
        val actualId = viaActualId
        val cambio = mejor != null && actualId != null && actualId != mejor.coincidencia.via.id
        val propia = if (cambio) indice.buscar(punto, rumboGrados, radioMetros, actualId) else null
        val elegida = if (propia != null && propia.puntuacion - mejor!!.puntuacion < margenMetros) propia else mejor
        viaActualId = elegida?.coincidencia?.via?.id
        return elegida?.coincidencia
    }
}
