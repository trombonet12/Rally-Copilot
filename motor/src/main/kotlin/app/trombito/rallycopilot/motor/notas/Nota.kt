package app.trombito.rallycopilot.motor.notas

/** Grado más cerrado (muy cerrada). */
internal const val GRADO_MIN = 1

/** Grado más suave. */
internal const val GRADO_MAX = 6

/** Límites de los grados «media» (3-4). */
internal const val GRADO_MEDIA_MIN = 3
internal const val GRADO_MEDIA_MAX = 4

/** Longitud máxima (caracteres) del texto libre de una nota. */
const val LONGITUD_MAX_TEXTO_LIBRE = 80

/** Matiz que se canta tras el grado («larga», «cierra»…). El orden del enum es el orden en que se cantan. */
enum class Modificador { ESCUADRA, LARGA, MUY_LARGA, CIERRA, ABRE }

/** Nota reducida a su categoría gruesa (modo de notas simplificadas). */
enum class GradoSimplificado { HORQUILLA, MUY_CERRADA, CERRADA, MEDIA, SUAVE }

/**
 * Nota de rally de una curva. [posicionM] es el inicio de la curva y [finM] su final (metros desde el inicio de la
 * traza). [grado] va de 1 (la más cerrada) a 6; `null` es una horquilla. [distanciaSiguienteM] es el hueco hasta la
 * nota siguiente ya redondeado (`null` si va [unidaConSiguiente] o si es la última). [cuidado] marca una curva que
 * pide precaución (su frase empieza por «cuidado,»); lo decide la hoja de notas, no el generador de notas.
 * [textoLibre] es lo que escribió Joan para esta nota («cuidado gravilla, derecha tres»): si lo hay, se canta y se
 * muestra tal cual en lugar de la frase generada (la distancia a la siguiente se sigue cantando después).
 * [radioMinM] es el radio mínimo de la curva medida (m); `null` en las notas dictadas o editadas. El modo simplificado
 * lo usa para cantar una curva lenta de grado 4 y para callar una de grado 6 casi recta.
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
    val textoLibre: String? = null,
    val radioMinM: Double? = null,
) {
    init {
        require(grado == null || grado in GRADO_MIN..GRADO_MAX) { "Grado fuera de $GRADO_MIN..$GRADO_MAX: $grado" }
    }

    /** El [textoLibre] si tiene contenido; un texto en blanco es como no tenerlo. */
    val textoPropio: String? get() = textoLibre?.takeIf { it.isNotBlank() }

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

/** Texto libre listo para guardar: una línea, sin espacios sobrantes y de [LONGITUD_MAX_TEXTO_LIBRE] como mucho. */
fun limpiarTextoLibre(texto: String?): String? =
    texto
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.take(LONGITUD_MAX_TEXTO_LIBRE)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
