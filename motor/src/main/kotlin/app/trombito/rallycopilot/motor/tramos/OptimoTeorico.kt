package app.trombito.rallycopilot.motor.tramos

/** Mejor tiempo de un sector: lo aportó la pasada de índice [pasadaIndice] de la lista dada a [OptimoTeorico.de]. */
data class SectorOptimo(
    val numero: Int,
    val desdeM: Double,
    val hastaM: Double,
    val tiempoMs: Long,
    val pasadaIndice: Int,
)

/**
 * Tramo óptimo teórico: [totalMs] es la suma del mejor tiempo de cada sector ([sectores]). [mejorPasadaIndice] y
 * [mejorPasadaMs] son la mejor pasada completa (null si ninguna cubre el tramo entero).
 */
data class Optimo(
    val totalMs: Long,
    val sectores: List<SectorOptimo>,
    val mejorPasadaIndice: Int?,
    val mejorPasadaMs: Long?,
) {
    /** Lo que se ganaría juntando los mejores sectores frente a la mejor pasada completa (≥ 0); null sin ella. */
    val gananciaMs: Long? get() = mejorPasadaMs?.let { it - totalMs }
}

/** Cálculo del tramo óptimo teórico (mejor sector de cada pasada). No existía algo equivalente en `analisis`. */
object OptimoTeorico {
    /**
     * Óptimo de [tramo] con las [pasadas] de ese tramo (las de otro `tramoId` se ignoran). Los sectores son los cortes
     * de `tramo.sectores` más la meta; cada uno toma la pasada que lo cubre y lo recorrió en menos tiempo (la primera
     * si empatan). Null sin pasadas o si algún sector no lo cubre ninguna (no habría óptimo completo).
     */
    fun de(
        tramo: Tramo,
        pasadas: List<Pasada>,
    ): Optimo? {
        val candidatas = pasadas.withIndex().filter { it.value.tramoId == tramo.id }
        val limites = listOf(0.0) + tramo.sectores + tramo.metaM
        val sectores =
            limites.zipWithNext().mapIndexed { i, (desde, hasta) ->
                val mejor =
                    candidatas
                        .mapNotNull { (indice, pasada) -> tiempoEntre(pasada, desde, hasta)?.let { indice to it } }
                        .minByOrNull { it.second } ?: return null
                SectorOptimo(i + 1, desde, hasta, mejor.second, mejor.first)
            }
        val completa =
            candidatas
                .mapNotNull { (indice, pasada) -> tiempoEntre(pasada, 0.0, tramo.metaM)?.let { indice to it } }
                .minByOrNull { it.second }
        return Optimo(sectores.sumOf { it.tiempoMs }, sectores, completa?.first, completa?.second)
    }

    private fun tiempoEntre(
        pasada: Pasada,
        desdeM: Double,
        hastaM: Double,
    ): Long? {
        val inicio = pasada.tiempoEn(desdeM)
        val fin = pasada.tiempoEn(hastaM)
        // Redondear los extremos: los sectores suman exactamente el total.
        return if (inicio == null || fin == null) null else Math.round(fin) - Math.round(inicio)
    }
}
