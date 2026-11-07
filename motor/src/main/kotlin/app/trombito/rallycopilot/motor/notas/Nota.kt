package app.trombito.rallycopilot.motor.notas

/** Grado más cerrado (muy cerrada). */
internal const val GRADO_MIN = 1

/** Grado más suave. */
internal const val GRADO_MAX = 6

/** Límites de los grados «media» (3-4). */
internal const val GRADO_MEDIA_MIN = 3
internal const val GRADO_MEDIA_MAX = 4

/** Matiz que se canta tras el grado («larga», «cierra»…). El orden del enum es el orden en que se cantan. */
enum class Modificador { ESCUADRA, LARGA, MUY_LARGA, CIERRA, ABRE }

/** Nota reducida a su categoría gruesa (modo de notas simplificadas). */
enum class GradoSimplificado { HORQUILLA, MUY_CERRADA, CERRADA, MEDIA, SUAVE }

/**
 * Nota de rally de una curva. [posicionM] es el inicio de la curva y [finM] su final (metros desde el inicio de la
 * traza). [grado] va de 1 (la más cerrada) a 6; `null` es una horquilla. [distanciaSiguienteM] es el hueco hasta la
 * nota siguiente ya redondeado (`null` si va [unidaConSiguiente] o si es la última). [cuidado] marca una curva que
 * pide precaución (su frase empieza por «cuidado,»); lo decide la hoja de notas, no el generador de notas.
 */
data class Nota(
    val posicionM: Double,
    val finM: Double,
    val direccion: Direccion,
    val grado: Int?,
    val modificadores: Set<Modificador> = emptySet(),
    val distanciaSiguienteM: Int? = null,
    val unidaConSiguiente: Boolean = false,
    val cuidado: Boolean = false,
) {
    init {
        require(grado == null || grado in GRADO_MIN..GRADO_MAX) { "Grado fuera de $GRADO_MIN..$GRADO_MAX: $grado" }
    }

    /**
     * 1 muy cerrada, 2 cerrada, 3–4 media, 5–6 suave; la horquilla se queda como horquilla y la escuadra cuenta como
     * muy cerrada.
     */
    fun simplificada(): GradoSimplificado =
        when (if (Modificador.ESCUADRA in modificadores) 1 else grado) {
            null -> GradoSimplificado.HORQUILLA
            1 -> GradoSimplificado.MUY_CERRADA
            2 -> GradoSimplificado.CERRADA
            GRADO_MEDIA_MIN, GRADO_MEDIA_MAX -> GradoSimplificado.MEDIA
            else -> GradoSimplificado.SUAVE
        }
}
