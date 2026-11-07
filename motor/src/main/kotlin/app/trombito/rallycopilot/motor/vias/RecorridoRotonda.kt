package app.trombito.rallycopilot.motor.vias

import app.trombito.rallycopilot.motor.geo.PuntoGeo

/**
 * Recorre el anillo de una rotonda de [grafo] en su sentido de circulación (el de dibujo, salvo `oneway=-1`) para
 * contar sus salidas. Ver [GrafoVias.ordinalSalidaRotonda].
 */
internal class RecorridoRotonda(
    private val grafo: GrafoVias,
) {
    /** Posición en el anillo: vía, índice del punto y sentido de avance dentro de la vía (1 o -1). */
    private class Paso(
        val via: Via,
        val indice: Int,
        val sentido: Int,
    ) {
        val punto: PuntoGeo get() = via.puntos[indice]
    }

    fun ordinal(
        entrada: PuntoGeo,
        salida: PuntoGeo,
        toleranciaM: Double,
    ): Int? {
        val inicio = nodoMasCercano(entrada, toleranciaM)
        val fin = nodoMasCercano(salida, toleranciaM)
        return if (inicio == null || fin == null) null else contar(inicio, GrafoVias.clave(fin.punto))
    }

    /** Salidas desde [inicio] hasta llegar al nodo de clave [destino] (que cuenta siempre); null si no llega. */
    private fun contar(
        inicio: Paso,
        destino: Long,
    ): Int? {
        var cuenta = 0
        for (paso in generateSequence(siguiente(inicio)) { siguiente(it) }.take(MAX_PASOS)) {
            val llegada = GrafoVias.clave(paso.punto) == destino
            if (llegada || tieneSalida(paso.punto)) cuenta++
            if (llegada) return cuenta
        }
        return null
    }

    private fun nodoMasCercano(
        punto: PuntoGeo,
        toleranciaM: Double,
    ): Paso? {
        var mejor: Paso? = null
        var distanciaMejor = toleranciaM
        for (via in grafo.viasAnillo) {
            via.puntos.forEachIndexed { i, p ->
                val d = punto.distanciaA(p)
                if (d <= distanciaMejor) {
                    distanciaMejor = d
                    mejor = Paso(via, i, sentido(via))
                }
            }
        }
        return mejor
    }

    private fun siguiente(paso: Paso): Paso? {
        val n = paso.via.puntos.size
        val destino = paso.indice + paso.sentido
        return when {
            destino in 0 until n -> Paso(paso.via, destino, paso.sentido)
            cerrada(paso.via) -> Paso(paso.via, if (paso.sentido > 0) 1 else n - 2, paso.sentido)
            else -> continuacion(paso)
        }
    }

    /** El anillo sigue en otra vía que empieza en el nodo (por su extremo de entrada, según su sentido). */
    private fun continuacion(paso: Paso): Paso? =
        grafo.aristas(paso.punto).firstNotNullOfOrNull { arista ->
            val otra = arista.via
            val sentido = sentido(otra)
            val extremo = if (sentido > 0) 0 else otra.puntos.lastIndex
            val destino = arista.indice + sentido
            val sigue = otra.id != paso.via.id && grafo.esAnillo(otra) && arista.indice == extremo
            if (sigue && destino in otra.puntos.indices) Paso(otra, destino, sentido) else null
        }

    /** Del nodo sale alguna vía que no es del anillo y por la que se puede salir de él. */
    private fun tieneSalida(punto: PuntoGeo): Boolean =
        grafo.aristas(punto).any { a ->
            val via = a.via
            !grafo.esAnillo(via) &&
                (
                    (a.indice < via.puntos.lastIndex && via.sentidoUnico != SentidoUnico.ATRAS) ||
                        (a.indice > 0 && via.sentidoUnico != SentidoUnico.ADELANTE)
                )
        }

    private fun cerrada(via: Via): Boolean =
        via.puntos.size >= PUNTOS_MINIMOS_CERRADA &&
            GrafoVias.clave(via.puntos.first()) == GrafoVias.clave(via.puntos.last())

    private fun sentido(via: Via): Int = if (via.sentidoUnico == SentidoUnico.ATRAS) -1 else 1

    private companion object {
        const val MAX_PASOS = 5_000
        const val PUNTOS_MINIMOS_CERRADA = 3
    }
}
