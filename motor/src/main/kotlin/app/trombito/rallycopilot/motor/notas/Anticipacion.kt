package app.trombito.rallycopilot.motor.notas

/** Cuánto antes de la curva se canta la nota, en segundos de marcha. */
enum class NivelAnticipacion(
    val segundos: Double,
) {
    MUY_PRONTO(8.0),
    PRONTO(6.5),
    NORMAL(5.0),
    TARDE(3.5),
    MUY_TARDE(2.5),
}

/**
 * Distancia de anticipación: `v · (T + tVozS)` acotada a [[minM], [maxM]], con `T` los segundos del nivel y
 * [tVozS] lo que tarda la voz en cantar la frase.
 */
class Anticipacion(
    private val tVozS: Double = 1.0,
    private val minM: Double = 40.0,
    private val maxM: Double = 400.0,
) {
    init {
        require(tVozS >= 0.0 && minM <= maxM) { "Anticipación inválida: tVoz=$tVozS min=$minM max=$maxM" }
    }

    /** Metros antes de la nota a los que hay que empezar a cantarla yendo a [vMs] (m/s). */
    fun distanciaM(
        vMs: Double,
        nivel: NivelAnticipacion,
    ): Double = (vMs.coerceAtLeast(0.0) * (nivel.segundos + tVozS)).coerceIn(minM, maxM)
}

/** Elemento visible ahora: a [distanciaM] por delante (negativa si se está pasando); [activa] si ya toca cantarlo. */
data class NotaVisible(
    val elemento: ElementoHoja,
    val distanciaM: Double,
    val activa: Boolean,
)

/** Elige los próximos elementos de una [HojaNotas] según la posición y la velocidad. Pensado para correr a 1 Hz. */
class SelectorNotas(
    private val anticipacion: Anticipacion = Anticipacion(),
) {
    /**
     * Los [n] primeros elementos de [hoja] con posición ≥ [posicionM] − [VISTA_ATRAS_M] (el que se está pasando aún
     * se ve un instante), en orden. [NotaVisible.activa] es `true` si ya está dentro de la distancia de
     * anticipación. Búsqueda binaria: O(log n + [n]).
     */
    fun siguientes(
        hoja: HojaNotas,
        posicionM: Double,
        vMs: Double,
        nivel: NivelAnticipacion,
        n: Int = 3,
    ): List<NotaVisible> {
        val elementos = hoja.elementos
        val umbral = anticipacion.distanciaM(vMs, nivel)
        val desde = primeroDesde(elementos, posicionM - VISTA_ATRAS_M)
        val hasta = minOf(elementos.size.toLong(), desde.toLong() + n.coerceAtLeast(0)).toInt()
        return (desde until hasta).map {
            val d = elementos[it].posicionM - posicionM
            NotaVisible(elementos[it], d, d <= umbral)
        }
    }

    /** Índice del primer elemento con `posicionM >= minimo` (el tamaño si no hay). */
    private fun primeroDesde(
        elementos: List<ElementoHoja>,
        minimo: Double,
    ): Int {
        var bajo = 0
        var alto = elementos.size
        while (bajo < alto) {
            val medio = (bajo + alto) ushr 1
            if (elementos[medio].posicionM < minimo) bajo = medio + 1 else alto = medio
        }
        return bajo
    }

    companion object {
        /** Metros por detrás de la posición durante los que una nota sigue visible. */
        const val VISTA_ATRAS_M = 5.0
    }
}
