package app.trombito.rallycopilot.motor.notas

/** Matiz que se canta tras el grado («larga», «cierra»…). El orden del enum es el orden en que se cantan. */
enum class Modificador { LARGA, MUY_LARGA, CIERRA, ABRE }

/** Nota reducida a su categoría gruesa (modo de notas simplificadas). */
enum class GradoSimplificado { HORQUILLA, CERRADA, MEDIA, SUAVE }

/**
 * Nota de rally de una curva. [posicionM] es el inicio de la curva y [finM] su final (metros desde el inicio de la
 * traza). [grado] va de 1 (la más cerrada) a 6; `null` es una horquilla. [distanciaSiguienteM] es el hueco hasta la
 * nota siguiente ya redondeado (`null` si va [unidaConSiguiente] o si es la última).
 */
data class Nota(
    val posicionM: Double,
    val finM: Double,
    val direccion: Direccion,
    val grado: Int?,
    val modificadores: Set<Modificador> = emptySet(),
    val distanciaSiguienteM: Int? = null,
    val unidaConSiguiente: Boolean = false,
) {
    init {
        require(grado == null || grado in 1..6) { "Grado fuera de 1..6: $grado" }
    }

    /** 1–2 cerrada, 3–4 media, 5–6 suave; la horquilla se queda como horquilla. */
    fun simplificada(): GradoSimplificado =
        when (grado) {
            null -> GradoSimplificado.HORQUILLA
            1, 2 -> GradoSimplificado.CERRADA
            3, 4 -> GradoSimplificado.MEDIA
            else -> GradoSimplificado.SUAVE
        }
}
