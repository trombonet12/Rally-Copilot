package app.trombito.rallycopilot.motor.mapa

import kotlin.math.abs

/**
 * Zoom del mapa en función de la velocidad: más lejos cuanto más deprisa se va (más carretera por delante).
 *
 * La curva es continua y monótona decreciente: interpolación lineal entre los puntos
 * (<= 20 km/h -> 17,0; 30 -> 16,5; 50 -> 16,0; 90 -> 15,0; >= 120 -> 14,0).
 */
object ZoomPorVelocidad {
    const val ZOOM_MIN = 12.0
    const val ZOOM_MAX = 18.0
    const val NIVEL_MIN = -2
    const val NIVEL_MAX = 2

    /** Zoom que añade (positivo, más cerca) o resta cada paso de `nivel`. */
    const val ZOOM_POR_NIVEL = 0.5

    private val CURVA = listOf(20.0 to 17.0, 30.0 to 16.5, 50.0 to 16.0, 90.0 to 15.0, 120.0 to 14.0)

    /**
     * Zoom objetivo para [kmh] y el ajuste manual [nivel] (se limita a -2..2), acotado a 12..18.
     * NaN y negativos cuentan como parado; +infinito, como velocidad máxima de la curva.
     */
    fun zoom(
        kmh: Double,
        nivel: Int = 0,
    ): Double = (zoomBase(kmh) + nivel.coerceIn(NIVEL_MIN, NIVEL_MAX) * ZOOM_POR_NIVEL).coerceIn(ZOOM_MIN, ZOOM_MAX)

    private fun zoomBase(kmh: Double): Double {
        val v = if (kmh.isNaN() || kmh < 0.0) 0.0 else kmh
        val primero = CURVA.first()
        val ultimo = CURVA.last()
        return when {
            v <= primero.first -> {
                primero.second
            }

            v >= ultimo.first -> {
                ultimo.second
            }

            else -> {
                val i = CURVA.indexOfLast { it.first <= v }
                val (x0, y0) = CURVA[i]
                val (x1, y1) = CURVA[i + 1]
                y0 + (y1 - y0) * (v - x0) / (x1 - x0)
            }
        }
    }
}

/**
 * Aplica [ZoomPorVelocidad] sin saltos: histéresis y velocidad de cambio limitada.
 *
 * @param maxNivelesPorSegundo velocidad máxima de cambio del zoom (niveles por segundo).
 * @param histeresis diferencia mínima entre el zoom objetivo nuevo y el vigente para cambiar de objetivo;
 *   una vez fijado, el zoom lo alcanza por completo (a ritmo limitado) aunque la velocidad oscile.
 */
class SuavizadorZoom(
    private val maxNivelesPorSegundo: Double = 0.5,
    private val histeresis: Double = 0.25,
) {
    private var zoom: Double? = null
    private var objetivo: Double = 0.0
    private var ultimoMs: Long = 0
    private var pausadoHastaMs: Long? = null

    /** Último zoom devuelto (o el manual fijado con [pausar]); null si aún no hay ninguno. */
    val zoomActual: Double? get() = zoom

    /**
     * Devuelve el zoom a aplicar en [ahoraMs] (reloj monótono en ms). La primera llamada devuelve el zoom
     * objetivo sin suavizar. Durante una [pausar] se mantiene el zoom vigente.
     */
    fun actualizar(
        kmh: Double,
        nivel: Int,
        ahoraMs: Long,
    ): Double {
        val bruto = ZoomPorVelocidad.zoom(kmh, nivel)
        val dtMs = if (zoom == null) 0L else (ahoraMs - ultimoMs).coerceAtLeast(0L)
        ultimoMs = if (zoom == null) ahoraMs else maxOf(ultimoMs, ahoraMs)
        if (pausadoHastaMs?.let { ahoraMs >= it } == true) pausadoHastaMs = null
        val actual = zoom
        val nuevo =
            when {
                actual == null -> {
                    bruto.also { objetivo = it }
                }

                pausadoHastaMs != null -> {
                    actual
                }

                else -> {
                    if (abs(bruto - objetivo) >= histeresis) objetivo = bruto
                    avanzar(actual, dtMs)
                }
            }
        zoom = nuevo
        return nuevo
    }

    /**
     * Mantiene el zoom manual hasta [hastaMs] (excluido); después `actualizar` vuelve al automático a ritmo
     * limitado. Si se pasa [zoomManual] (el que dejó el pellizco), pasa a ser el zoom vigente (acotado a 12..18);
     * si no, se conserva el actual.
     */
    fun pausar(
        hastaMs: Long,
        zoomManual: Double? = null,
    ) {
        pausadoHastaMs = hastaMs
        zoomManual?.let { zoom = it.coerceIn(ZoomPorVelocidad.ZOOM_MIN, ZoomPorVelocidad.ZOOM_MAX) }
        zoom?.let { objetivo = it }
    }

    private fun avanzar(
        actual: Double,
        dtMs: Long,
    ): Double {
        val maxPaso = maxNivelesPorSegundo * dtMs / MS_POR_S
        val delta = (objetivo - actual).coerceIn(-maxPaso, maxPaso)
        return actual + delta
    }

    private companion object {
        const val MS_POR_S = 1000.0
    }
}
