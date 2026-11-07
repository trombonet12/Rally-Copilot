package app.trombito.rallycopilot.domain.tramos

/**
 * El nombre automático de un tramo se guarda en una sola columna como «título» y, si lo hay, «subtítulo» separados por
 * un salto de línea: título «Palma → Montuïri» y subtítulo las carreteras «Ma-13 / Ma-15». Los nombres antiguos («Ma-13
 * · Santa María») no llevan salto y son todo título. Así no hace falta migrar la base de datos.
 */
object NombreAutomatico {
    private const val SEPARADOR = '\n'

    /** El texto a guardar; sin [subtitulo] (o en blanco) es solo el [titulo]. */
    fun componer(
        titulo: String,
        subtitulo: String?,
    ): String {
        val sub = subtitulo?.trim()?.takeIf { it.isNotEmpty() }
        return if (sub == null) titulo.trim() else "${titulo.trim()}$SEPARADOR$sub"
    }

    /** La primera línea del nombre guardado. */
    fun titulo(guardado: String): String = guardado.substringBefore(SEPARADOR).trim()

    /** El resto del nombre guardado (las carreteras) o null si no lo hay. */
    fun subtitulo(guardado: String): String? =
        if (SEPARADOR in guardado) guardado.substringAfter(SEPARADOR).trim().takeIf { it.isNotEmpty() } else null
}
