package app.trombito.rallycopilot.motor.vias

import app.trombito.rallycopilot.motor.geo.PuntoGeo

/**
 * Sigue la vía por la que se circula con histéresis: mantiene la vía actual mientras siga dentro del radio, salvo
 * que otra puntúe al menos [margenMetros] mejor (distancia más penalización de rumbo). Evita saltos entre calles
 * paralelas o en cruces por ruido del GPS.
 *
 * - Con rumbo conocido solo cuentan las vías cuyo eje se desvía como mucho [MAX_DESVIO_GRADOS] (45°) del rumbo, en
 *   ambos sentidos: una calle que cruza por debajo de una autopista no puede ser la vía actual aunque quede a 0 m en
 *   planta. Si la vía actual sigue en el radio, se conserva aunque no cumpla (curva cerrada, rumbo retrasado).
 * - Cambiar a una vía que no está conectada con la actual cuesta más: 60 m extra si es de otro nivel (puente, túnel o
 *   `layer`: se pasa por encima o por debajo, no se cruza) y, con [grafo], 15 m si es del mismo nivel pero no comparte
 *   ningún nodo con ella. Sin [grafo] solo cuenta el nivel.
 * - Ese cambio «duro» exige además que la misma candidata gane [FIJACIONES_PARA_CAMBIAR] fijaciones seguidas (cuenta
 *   por fijación, no por reloj). La vía actual se recuerda aunque el GPS se desvíe de ella más del radio (hasta
 *   [RADIO_MEMORIA_FACTOR] veces el radio, y hasta [FIJACIONES_DE_MEMORIA] fijaciones sin ninguna vía): así el nivel
 *   que se lleva no se pierde bajo un puente o dentro de un túnel.
 *
 * No es seguro entre hilos.
 */
class SeguidorVia(
    private val indice: IndiceVias,
    private val margenMetros: Double = 5.0,
    private val radioMetros: Double = 25.0,
    private val grafo: GrafoVias? = null,
) {
    private var actual: Via? = null
    private var candidataId: Long? = null
    private var candidataFijaciones = 0
    private var sinVia = 0

    /** Vía actual para la posición dada, o null si no hay ninguna dentro del radio. */
    fun actualizar(
        punto: PuntoGeo,
        rumboGrados: Double?,
    ): Coincidencia? {
        val maxDesvio = if (rumboGrados != null) MAX_DESVIO_GRADOS else null
        val mejor = indice.buscar(punto, rumboGrados, radioMetros, null, maxDesvio)
        val via = actual ?: return adoptar(mejor?.coincidencia)
        val propia = indice.buscar(punto, rumboGrados, radioMetros, via.id, null)
        return when {
            mejor == null -> sinCandidata(punto, rumboGrados, via, propia)
            mejor.coincidencia.via.id == via.id -> mantener(mejor.coincidencia)
            else -> otraCandidata(punto, rumboGrados, via, mejor, propia)
        }
    }

    private fun sinCandidata(
        punto: PuntoGeo,
        rumboGrados: Double?,
        via: Via,
        propia: Candidata?,
    ): Coincidencia? {
        if (propia != null) return mantener(propia.coincidencia)
        val cerca = indice.buscar(punto, rumboGrados, radioMetros * RADIO_MEMORIA_FACTOR, via.id, null)
        val olvidar = cerca == null || sinVia >= FIJACIONES_DE_MEMORIA
        if (!olvidar) {
            sinVia++
            candidataId = null
            candidataFijaciones = 0
        }
        return if (olvidar) adoptar(null) else null
    }

    private fun otraCandidata(
        punto: PuntoGeo,
        rumboGrados: Double?,
        via: Via,
        mejor: Candidata,
        propia: Candidata?,
    ): Coincidencia? {
        val nueva = mejor.coincidencia
        val coste = penalizacion(via, nueva.via)
        if (propia != null && propia.puntuacion - mejor.puntuacion - coste < margenMetros) {
            return mantener(propia.coincidencia)
        }
        val recordada =
            propia?.coincidencia
                ?: indice.buscar(punto, rumboGrados, radioMetros * RADIO_MEMORIA_FACTOR, via.id, null)?.coincidencia
        return cambiar(nueva, coste, recordada)
    }

    /** [recordada] es la vía actual aunque el GPS se haya desviado de ella; null si ya quedó lejos de ella. */
    private fun cambiar(
        nueva: Coincidencia,
        coste: Double,
        recordada: Coincidencia?,
    ): Coincidencia? {
        val inmediato = recordada == null || coste == 0.0
        if (!inmediato) {
            candidataFijaciones = if (candidataId == nueva.via.id) candidataFijaciones + 1 else 1
            candidataId = nueva.via.id
            sinVia = 0
        }
        return if (inmediato || candidataFijaciones >= FIJACIONES_PARA_CAMBIAR) adoptar(nueva) else recordada
    }

    private fun mantener(coincidencia: Coincidencia): Coincidencia {
        candidataId = null
        candidataFijaciones = 0
        sinVia = 0
        return coincidencia
    }

    private fun adoptar(coincidencia: Coincidencia?): Coincidencia? {
        actual = coincidencia?.via
        candidataId = null
        candidataFijaciones = 0
        sinVia = 0
        return coincidencia
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
        const val MAX_DESVIO_GRADOS = 45.0
        const val FIJACIONES_PARA_CAMBIAR = 3
        const val FIJACIONES_DE_MEMORIA = 5
        const val RADIO_MEMORIA_FACTOR = 3.0
    }
}
