package app.trombito.rallycopilot.motor.tramos

import app.trombito.rallycopilot.motor.geo.PuntoGeo

/**
 * Tramo cronometrable: una traza con una salida (primer punto) y una meta (último). [sectores] y [microsectores] son
 * las distancias de corte en metros desde la salida, estrictamente crecientes y dentro de (0, [metaM]): N cortes
 * dan N + 1 sectores. La meta cronometrada ([metaM]) se coloca [META_ANTES_M] (10 m) antes del final de la traza
 * grabada, para que quien se detiene al llegar, unos metros antes del último punto, también cierre la pasada.
 * Por defecto 3 cortes a 1/4, 2/4 y 3/4 (4 sectores) y un microsector por kilómetro.
 */
data class Tramo(
    val id: String,
    val traza: List<PuntoGeo>,
    val longitudM: Double,
    val sectores: List<Double> = sectoresPorDefecto(longitudM),
    val microsectores: List<Double> = microsectoresPorDefecto(longitudM),
) {
    /** Distancia (m desde la salida) a la que se cruza la meta: [longitudM] menos [META_ANTES_M]. */
    val metaM: Double get() = if (longitudM > 2 * META_ANTES_M) longitudM - META_ANTES_M else longitudM

    init {
        require(traza.size >= 2) { "Un tramo necesita al menos dos puntos" }
        require(longitudM.isFinite() && longitudM > 0.0) { "Longitud inválida: $longitudM" }
        validarCortes("sectores", sectores)
        validarCortes("microsectores", microsectores)
    }

    private fun validarCortes(
        nombre: String,
        cortes: List<Double>,
    ) {
        require(cortes.all { it.isFinite() && it > 0.0 && it < metaM }) {
            "Los $nombre deben estar dentro de (0, $metaM): $cortes"
        }
        require(cortes.zipWithNext().all { (a, b) -> a < b }) { "Los $nombre deben ser estrictamente crecientes" }
    }

    companion object {
        /** Metros antes del final de la traza grabada en que se coloca la meta. */
        const val META_ANTES_M = 10.0

        /** Número de sectores por defecto. */
        const val SECTORES_POR_DEFECTO = 4

        /** Paso del microsector por defecto: uno por kilómetro. */
        const val MICROSECTOR_M = 1000.0

        /** Cortes que reparten [longitudM] en [numero] sectores iguales (numero − 1 cortes). */
        fun sectoresPorDefecto(
            longitudM: Double,
            numero: Int = SECTORES_POR_DEFECTO,
        ): List<Double> = (1 until numero).map { longitudM * it / numero }

        /** Un corte cada [pasoM] metros mientras quede al menos un metro antes de la meta. */
        fun microsectoresPorDefecto(
            longitudM: Double,
            pasoM: Double = MICROSECTOR_M,
        ): List<Double> {
            require(pasoM > 0.0) { "Paso inválido: $pasoM" }
            return generateSequence(pasoM) { it + pasoM }.takeWhile { it < longitudM - 1.0 }.toList()
        }
    }
}

/** Instante [tMs] (ms desde la salida) en que se pasó por [distanciaM] (metros desde la salida). */
data class MarcaTiempo(
    val distanciaM: Double,
    val tMs: Long,
)

/**
 * Una pasada completa por un tramo: curva distancia-tiempo ([tiempos], por distancia creciente, de la salida a la meta,
 * con los cortes exactos interpolados) y tiempo [totalMs].
 */
data class Pasada(
    val tramoId: String,
    val tiempos: List<MarcaTiempo>,
    val totalMs: Long,
) {
    init {
        require(tiempos.zipWithNext().all { (a, b) -> a.distanciaM <= b.distanciaM && a.tMs <= b.tMs }) {
            "Las marcas deben ser crecientes en distancia y tiempo"
        }
    }

    /** Tiempo (ms desde la salida) al pasar por [distanciaM], interpolado entre marcas; null fuera de la pasada. */
    fun tiempoEn(distanciaM: Double): Double? {
        val cubierto = tiempos.isNotEmpty() && distanciaM in tiempos.first().distanciaM..tiempos.last().distanciaM
        if (!cubierto) return null
        val i = tiempos.indexOfFirst { it.distanciaM >= distanciaM }
        val b = tiempos[i]
        val a = if (i > 0) tiempos[i - 1] else b
        val tramoM = b.distanciaM - a.distanciaM
        return if (tramoM <= 0.0) b.tMs.toDouble() else a.tMs + (distanciaM - a.distanciaM) / tramoM * (b.tMs - a.tMs)
    }
}
