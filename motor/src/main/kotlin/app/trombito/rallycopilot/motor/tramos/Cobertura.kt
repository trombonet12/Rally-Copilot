package app.trombito.rallycopilot.motor.tramos

import app.trombito.rallycopilot.motor.geo.ProyeccionPlana
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.distanciaASegmento
import kotlin.math.max
import kotlin.math.min

/** Cuánto de un recorrido cae sobre la traza de un tramo ya guardado (para no duplicar tramos). */
object Cobertura {
    /** Distancia (m) a la traza por debajo de la cual un punto del recorrido cuenta como «sobre el tramo». */
    const val RADIO_M = 30.0

    /** Fracción del recorrido (por metros) que está a [RADIO_M] o menos de [traza]; 0 si no mide nada. */
    fun fraccion(
        recorrido: List<PuntoGeo>,
        traza: List<PuntoGeo>,
        radioM: Double = RADIO_M,
    ): Double {
        if (recorrido.size < 2 || traza.size < 2) return 0.0
        val plano = ProyeccionPlana.deTraza(traza)
        val tx = DoubleArray(traza.size) { plano.x(traza[it]) }
        val ty = DoubleArray(traza.size) { plano.y(traza[it]) }
        var total = 0.0
        var cubierto = 0.0
        for (i in 0 until recorrido.lastIndex) {
            val paso = recorrido[i].distanciaA(recorrido[i + 1])
            total += paso
            if (cercano(plano.x(recorrido[i]), plano.y(recorrido[i]), tx, ty, radioM)) cubierto += paso
        }
        return if (total > 0.0) cubierto / total else 0.0
    }

    private fun cercano(
        px: Double,
        py: Double,
        tx: DoubleArray,
        ty: DoubleArray,
        radioM: Double,
    ): Boolean {
        for (i in 0 until tx.lastIndex) {
            val fuera =
                px < min(tx[i], tx[i + 1]) - radioM || px > max(tx[i], tx[i + 1]) + radioM ||
                    py < min(ty[i], ty[i + 1]) - radioM || py > max(ty[i], ty[i + 1]) + radioM
            if (!fuera && distanciaASegmento(px, py, tx[i], ty[i], tx[i + 1], ty[i + 1]) <= radioM) return true
        }
        return false
    }
}
