package app.trombito.rallycopilot.data.escucha

import app.trombito.rallycopilot.domain.busqueda.Buscador
import java.util.Locale

/**
 * Los nombres de municipios y localidades de Mallorca tal como se pueden decir («Sóller», «Cala d'Or» → «cala d or»),
 * para ofrecerlos a la gramática cerrada del reconocedor como destinos («llévame a Sóller»). Salen del índice de
 * búsqueda offline ([Buscador.nombresDeLugares], de más a menos importantes). Solo valen los nombres cuyas palabras
 * conoce el modelo ([conoce], por defecto [VocabularioVosk]): los demás (Pollença, Llucmajor…) no se pueden reconocer
 * y se descartan. La lista se calcula la primera vez que hay índice y se conserva mientras viva el proceso; si el
 * índice aún no está, no se guarda y se vuelve a intentar.
 */
class LugaresPronunciables(
    private val buscador: Buscador,
    private val conoce: (String) -> Boolean = VocabularioVosk::conoce,
    private val consulta: Int = NOMBRES_CONSULTADOS,
) {
    @Volatile
    private var guardados: List<String>? = null

    /** Nombres pronunciables, normalizados (minúsculas, con tildes, sin signos) y sin repetir. */
    suspend fun nombres(): List<String> {
        guardados?.let { return it }
        val nombres = buscador.nombresDeLugares(consulta).mapNotNull(::pronunciable).distinct()
        if (nombres.isNotEmpty()) guardados = nombres
        return nombres
    }

    /** El nombre en palabras del modelo, o `null` si alguna no la conoce (o lleva cifras o está vacío). */
    internal fun pronunciable(nombre: String): String? {
        if (nombre.any { it.isDigit() }) return null
        val palabras =
            nombre
                .lowercase(Locale.forLanguageTag("es"))
                .replace(NO_LETRAS, " ")
                .trim()
                .split(' ')
                .filter { it.isNotEmpty() }
        return palabras.takeIf { it.isNotEmpty() && it.all(conoce) }?.joinToString(" ")
    }

    private companion object {
        /** Más de lo que cabe en la gramática; ya se recorta por presupuesto de frases (`GramaticaOrdenes`). */
        const val NOMBRES_CONSULTADOS = 1_200
        val NO_LETRAS = Regex("[^\\p{L}]+")
    }
}
