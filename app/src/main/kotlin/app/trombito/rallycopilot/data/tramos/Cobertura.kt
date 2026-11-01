package app.trombito.rallycopilot.data.tramos

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Cuánto de un recorrido cae sobre la traza de un tramo ya guardado (para no duplicar tramos). */
internal object Cobertura {
    /** Distancia (m) a la traza por debajo de la cual un punto del recorrido cuenta como «sobre el tramo». */
    const val RADIO_M = 30.0

    /** Fracción del recorrido (por metros) que está a [RADIO_M] o menos de [traza]; 0 si no mide nada. */
    fun fraccion(
        recorrido: List<PuntoGeo>,
        traza: List<PuntoGeo>,
        radioM: Double = RADIO_M,
    ): Double {
        if (recorrido.size < 2 || traza.size < 2) return 0.0
        val escalaX = PuntoGeo.RADIO_TIERRA_M * cos(Math.toRadians(traza.sumOf { it.lat } / traza.size))
        val x = { p: PuntoGeo -> Math.toRadians(p.lon) * escalaX }
        val y = { p: PuntoGeo -> Math.toRadians(p.lat) * PuntoGeo.RADIO_TIERRA_M }
        val tx = DoubleArray(traza.size) { x(traza[it]) }
        val ty = DoubleArray(traza.size) { y(traza[it]) }
        var total = 0.0
        var cubierto = 0.0
        for (i in 0 until recorrido.lastIndex) {
            val paso = recorrido[i].distanciaA(recorrido[i + 1])
            total += paso
            if (cercano(x(recorrido[i]), y(recorrido[i]), tx, ty, radioM)) cubierto += paso
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

    @Suppress("LongParameterList") // un punto y los dos extremos de un segmento, en coordenadas planas
    private fun distanciaASegmento(
        px: Double,
        py: Double,
        ax: Double,
        ay: Double,
        bx: Double,
        by: Double,
    ): Double {
        val dx = bx - ax
        val dy = by - ay
        val largo2 = dx * dx + dy * dy
        val t = if (largo2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / largo2).coerceIn(0.0, 1.0)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }
}
