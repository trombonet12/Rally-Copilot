package app.trombito.rallycopilot.motor.geo

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
    private val plano = ProyeccionPlana.deTraza(puntos)
    private val xs = DoubleArray(puntos.size) { plano.x(puntos[it]) }
    private val ys = DoubleArray(puntos.size) { plano.y(puntos[it]) }

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
    ): Double = distanciaASegmento(xs[i], ys[i], xs[a], ys[a], xs[b], ys[b])
}
