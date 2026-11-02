package app.trombito.rallycopilot.motor.tramos

/** Edición de los cortes de sector de un [Tramo]: mover uno arrastrando y repartir los microsectores por kilómetro. */
object CortesTramo {
    /** Separación mínima (m) entre cortes vecinos y con la salida o la meta. */
    const val SEPARACION_MIN_M = 50.0

    /** Máximo de microsectores por kilómetro que se pueden configurar. */
    const val MICROSECTORES_POR_KM_MAX = 4

    /**
     * Mueve el corte [indice] de [cortes] a [nuevoM] sin que pase de sus vecinos (a [SEPARACION_MIN_M] de ellos) ni de
     * la salida o la meta ([metaM]). Devuelve la lista nueva; si no cabe ningún valor, la original.
     */
    fun mover(
        cortes: List<Double>,
        indice: Int,
        nuevoM: Double,
        metaM: Double,
    ): List<Double> {
        require(indice in cortes.indices) { "Corte inexistente: $indice" }
        val minimo = (cortes.getOrNull(indice - 1) ?: 0.0) + SEPARACION_MIN_M
        val maximo = (cortes.getOrNull(indice + 1) ?: metaM) - SEPARACION_MIN_M
        if (minimo > maximo) return cortes
        return cortes.toMutableList().also { it[indice] = nuevoM.coerceIn(minimo, maximo) }
    }

    /** Microsectores de un tramo de [longitudM] con [porKm] cortes por kilómetro (0 = ninguno, máximo 4). */
    fun microsectoresPorKm(
        longitudM: Double,
        porKm: Int,
    ): List<Double> {
        require(porKm in 0..MICROSECTORES_POR_KM_MAX) { "Microsectores por km fuera de 0..4: $porKm" }
        if (porKm == 0) return emptyList()
        return Tramo.microsectoresPorDefecto(longitudM, Tramo.MICROSECTOR_M / porKm)
    }

    /** Microsectores por kilómetro que más se parecen a [microsectores] (0 si no hay); para mostrar el ajuste. */
    fun porKmDe(
        longitudM: Double,
        microsectores: List<Double>,
    ): Int {
        val kilometros = longitudM / Tramo.MICROSECTOR_M
        if (microsectores.isEmpty() || kilometros <= 0.0) return 0
        return Math.round(microsectores.size / kilometros).toInt().coerceIn(1, MICROSECTORES_POR_KM_MAX)
    }
}
