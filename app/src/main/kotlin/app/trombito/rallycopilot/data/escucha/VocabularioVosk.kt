package app.trombito.rallycopilot.data.escucha

import java.util.zip.GZIPInputStream

/**
 * Las palabras que conoce el modelo de Vosk (`vosk-model-small-es-0.42`, 100 000 palabras). El reconocedor con una
 * gramática cerrada no puede decir una palabra que no esté en el modelo (y una frase con una palabra desconocida deja
 * de valer), así que todo lo que entra en la gramática (las frases de las órdenes y los nombres de lugares) se
 * comprueba aquí. El modelo pequeño no trae `words.txt`: la lista sale de la tabla de símbolos incrustada en
 * `graph/Gr.fst` (script `app/src/test/resources/vosk/extraer-vocabulario.py`) y va en `resources/asr/`. Al cambiar el
 * modelo (`CopiaModeloVosk.VERSION`) hay que regenerarla.
 */
object VocabularioVosk {
    private const val RECURSO = "/asr/vocabulario-vosk-es-0.42.txt.gz"

    /** Todas las palabras, en minúsculas y con tildes tal como las escribe el modelo; vacío si falta el recurso. */
    val palabras: Set<String> by lazy { cargar() }

    /** `true` si el modelo conoce [palabra] (minúsculas, con sus tildes). */
    fun conoce(palabra: String): Boolean = palabra in palabras

    private fun cargar(): Set<String> {
        val entrada = VocabularioVosk::class.java.getResourceAsStream(RECURSO) ?: return emptySet()
        return GZIPInputStream(entrada).bufferedReader(Charsets.UTF_8).use { lector ->
            lector.lineSequence().filter { it.isNotEmpty() }.toHashSet()
        }
    }
}
