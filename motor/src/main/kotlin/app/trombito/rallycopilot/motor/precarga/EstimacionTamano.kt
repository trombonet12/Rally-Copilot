package app.trombito.rallycopilot.motor.precarga

/**
 * Tamaño estimado de una descarga de satélite PNOA. El medio por tesela sale del z17 de Mallorca (≈ 0,9 GB en ≈ 65 000
 * teselas de tierra, ≈ 14 KB) y de la medición de DECISIONES (≈ 5,4 MB/km² a z18–19, ≈ 15 KB por tesela); z18 y z19
 * comparten el medio más alto para no quedarse cortos. Es una estimación: una zona con mar baja menos.
 */
object EstimacionTamano {
    const val BYTES_MEDIOS_POR_TESELA = 15_000L

    fun bytes(teselas: Long): Long {
        require(teselas >= 0) { "Teselas negativas" }
        return teselas * BYTES_MEDIOS_POR_TESELA
    }

    /** Teselas y bytes de un [rectangulo] a [zoom], recortado a Mallorca. */
    fun zona(
        rectangulo: Rectangulo,
        zoom: Int,
    ): EstimacionZona {
        val n = TeselasPrecarga.contarRectangulo(rectangulo, zoom)
        return EstimacionZona(n, bytes(n))
    }
}

data class EstimacionZona(
    val teselas: Long,
    val bytes: Long,
)
