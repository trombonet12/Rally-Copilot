package app.trombito.rallycopilot.motor.geo

import kotlin.math.cos
import kotlin.math.hypot

/**
 * Simplifica la polilínea con Douglas-Peucker en una proyección plana local (equirectangular centrada en la
 * latitud media; válida para trazas de unas decenas de km que no cruzan el antimeridiano). Conserva siempre el
 * primer y el último punto, el orden y los propios objetos (con su altitud); ningún punto descartado queda a
 * más de [toleranciaM] metros de la polilínea resultante. Iterativo: no desborda la pila con trazas largas.
 */
fun List<PuntoGeo>.simplificar(toleranciaM: Double): List<PuntoGeo> {
    require(toleranciaM.isFinite() && toleranciaM >= 0.0) { "Tolerancia inválida: $toleranciaM" }
    if (size <= 2) return toList()
    val plano = PlanoLocal(this)
    val conservar = BooleanArray(size)
    conservar[0] = true
    conservar[lastIndex] = true
    val pendientes = ArrayDeque<Pair<Int, Int>>()
    pendientes.add(0 to lastIndex)
    while (pendientes.isNotEmpty()) {
        val (ini, fin) = pendientes.removeLast()
        val (indice, distancia) = plano.masLejano(ini, fin)
        if (distancia > toleranciaM) {
            conservar[indice] = true
            pendientes.add(ini to indice)
            pendientes.add(indice to fin)
        }
    }
    return filterIndexed { i, _ -> conservar[i] }
}

private class PlanoLocal(
    puntos: List<PuntoGeo>,
) {
    private val xs = DoubleArray(puntos.size)
    private val ys = DoubleArray(puntos.size)

    init {
        val escalaX = PuntoGeo.RADIO_TIERRA_M * cos(Math.toRadians(puntos.sumOf { it.lat } / puntos.size))
        puntos.forEachIndexed { i, p ->
            xs[i] = Math.toRadians(p.lon) * escalaX
            ys[i] = Math.toRadians(p.lat) * PuntoGeo.RADIO_TIERRA_M
        }
    }

    /** Índice y distancia (m) del punto estrictamente entre [ini] y [fin] más lejano al segmento ini-fin. */
    fun masLejano(
        ini: Int,
        fin: Int,
    ): Pair<Int, Double> {
        var mejor = ini
        var mayor = -1.0
        for (i in ini + 1 until fin) {
            val d = distanciaASegmento(i, ini, fin)
            if (d > mayor) {
                mayor = d
                mejor = i
            }
        }
        return mejor to mayor
    }

    private fun distanciaASegmento(
        i: Int,
        a: Int,
        b: Int,
    ): Double {
        val dx = xs[b] - xs[a]
        val dy = ys[b] - ys[a]
        val longitud2 = dx * dx + dy * dy
        val proyeccion = (xs[i] - xs[a]) * dx + (ys[i] - ys[a]) * dy
        val t = if (longitud2 == 0.0) 0.0 else (proyeccion / longitud2).coerceIn(0.0, 1.0)
        return hypot(xs[i] - (xs[a] + t * dx), ys[i] - (ys[a] + t * dy))
    }
}
