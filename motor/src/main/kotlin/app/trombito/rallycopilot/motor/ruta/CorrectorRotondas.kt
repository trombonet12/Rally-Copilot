package app.trombito.rallycopilot.motor.ruta

import app.trombito.rallycopilot.motor.vias.GrafoVias

/**
 * Pone en cada rotonda de una [Ruta] el número de salida calculado sobre el grafo local de vías (Valhalla da siempre
 * 2): entra por el punto de la ruta donde empieza la maniobra y sale por donde empieza la salida.
 */
object CorrectorRotondas {
    /** [ruta] con `salidaRotonda` recalculado; null en las rotondas que el grafo no permite resolver. */
    fun aplicar(
        ruta: Ruta,
        grafo: GrafoVias,
    ): Ruta =
        ruta.copy(
            maniobras =
                ruta.maniobras.map { m ->
                    if (m.tipo == TipoManiobra.ROTONDA_ENTRADA) m.copy(salidaRotonda = ordinal(ruta, m, grafo)) else m
                },
        )

    private fun ordinal(
        ruta: Ruta,
        m: Maniobra,
        grafo: GrafoVias,
    ): Int? {
        val entrada = ruta.puntos.getOrNull(m.inicioIndice)
        val salida = ruta.puntos.getOrNull(m.salidaAnilloIndice ?: m.finIndice)
        return if (entrada == null || salida == null) null else grafo.ordinalSalidaRotonda(entrada, salida)
    }
}
