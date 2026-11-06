package app.trombito.rallycopilot.motor.vias

import app.trombito.rallycopilot.motor.geo.PuntoGeo

/**
 * Sigue la vía por la que se circula con histéresis: mantiene la vía actual mientras siga dentro del radio, salvo
 * que otra puntúe al menos [margenMetros] mejor (distancia más penalización de rumbo). Evita saltos entre calles
 * paralelas o en cruces por ruido del GPS. Cambiar a una vía que no está conectada con la actual cuesta más: 60 m
 * extra si es de otro nivel (puente, túnel o `layer`: se pasa por encima o por debajo, no se cruza) y, con [grafo],
 * 15 m si es del mismo nivel pero no comparte ningún nodo con ella. Sin [grafo] solo cuenta el nivel. No es seguro
 * entre hilos.
 */
class SeguidorVia(
    private val indice: IndiceVias,
    private val margenMetros: Double = 5.0,
    private val radioMetros: Double = 25.0,
    private val grafo: GrafoVias? = null,
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
        val coste = if (propia != null) penalizacion(propia.coincidencia.via, mejor!!.coincidencia.via) else 0.0
        val elegida =
            if (propia != null &&
                propia.puntuacion - mejor!!.puntuacion - coste < margenMetros
            ) {
                propia
            } else {
                mejor
            }
        viaActualId = elegida?.coincidencia?.via?.id
        return elegida?.coincidencia
    }

    /** Lo que cuesta pasar de la vía [actual] a [candidata] si no están conectadas (0 si comparten nodo). */
    private fun penalizacion(
        actual: Via,
        candidata: Via,
    ): Double =
        when {
            grafo?.conectadas(actual, candidata) == true -> 0.0
            actual.nivel != candidata.nivel -> PENALIZACION_OTRO_NIVEL_M
            grafo != null -> PENALIZACION_SIN_NODO_M
            else -> 0.0
        }

    private companion object {
        const val PENALIZACION_OTRO_NIVEL_M = 60.0
        const val PENALIZACION_SIN_NODO_M = 15.0
    }
}
