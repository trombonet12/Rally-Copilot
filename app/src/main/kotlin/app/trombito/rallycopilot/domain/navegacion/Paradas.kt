package app.trombito.rallycopilot.domain.navegacion

/**
 * Edición de una lista ordenada de paradas intermedias (la del orden en que se añaden). Son funciones puras: devuelven
 * la lista nueva y, si la orden no se puede cumplir (índice fuera de rango, lista llena), la misma lista sin cambios.
 */
object EdicionParadas {
    /** Máximo de paradas: Valhalla admite 20 locations por petición (`service_limits.auto.max_locations`). */
    const val MAX_PARADAS = 18

    /** Añade [parada] al final (justo antes del destino). */
    fun anadir(
        paradas: List<Destino>,
        parada: Destino,
    ): List<Destino> = if (paradas.size >= MAX_PARADAS) paradas else paradas + parada

    fun quitar(
        paradas: List<Destino>,
        indice: Int,
    ): List<Destino> = if (indice in paradas.indices) paradas.filterIndexed { i, _ -> i != indice } else paradas

    /** Mueve la parada [desde] para que quede en la posición [hasta] de la lista resultante. */
    fun mover(
        paradas: List<Destino>,
        desde: Int,
        hasta: Int,
    ): List<Destino> {
        if (desde !in paradas.indices || hasta !in paradas.indices || desde == hasta) return paradas
        return paradas.toMutableList().apply { add(hasta, removeAt(desde)) }
    }
}

/**
 * Orden de edición de las paradas pendientes de un guiado en curso; los índices son los de
 * `EstadoNavegacion.Guiando.paradas`. La sesión las aplica en orden y recalcula la ruta desde la posición actual.
 */
sealed interface OrdenParadas {
    data class Anadir(
        val parada: Destino,
    ) : OrdenParadas

    data class Quitar(
        val indice: Int,
    ) : OrdenParadas

    data class Mover(
        val desde: Int,
        val hasta: Int,
    ) : OrdenParadas

    /** Cambia el destino final conservando las paradas pendientes; la sesión recalcula desde la posición actual. */
    data class CambiarDestino(
        val destino: Destino,
    ) : OrdenParadas
}

/** Aplica la orden a la lista de paradas pendientes ([OrdenParadas.CambiarDestino] no las toca). */
fun OrdenParadas.aplicar(paradas: List<Destino>): List<Destino> =
    when (this) {
        is OrdenParadas.Anadir -> EdicionParadas.anadir(paradas, parada)
        is OrdenParadas.Quitar -> EdicionParadas.quitar(paradas, indice)
        is OrdenParadas.Mover -> EdicionParadas.mover(paradas, desde, hasta)
        is OrdenParadas.CambiarDestino -> paradas
    }
