package app.trombito.rallycopilot.motor.voz

/** Tipo de frase del copiloto. [prioridad] menor = más importante (ALERTA manda sobre MANIOBRA, NOTA y CRONO). */
enum class TipoFrase(
    val prioridad: Int,
) {
    ALERTA(0),
    MANIOBRA(1),
    NOTA(2),
    CRONO(3),
}

/** Cuánto habla el copiloto: cada modo añade tipos al anterior. */
enum class ModoCopiloto(
    private val maximo: TipoFrase?,
) {
    /** No habla nada. */
    SILENCIO(null),

    /** Solo alertas y maniobras de navegación. */
    SOLO_ALERTAS(TipoFrase.MANIOBRA),

    /** Alertas, maniobras y notas de la carretera. */
    NOTAS(TipoFrase.NOTA),

    /** Todo, incluidos delta y parciales del cronometraje. */
    COMPLETO(TipoFrase.CRONO),
    ;

    /** `true` si el modo deja hablar a las frases de [tipo]. */
    fun permite(tipo: TipoFrase): Boolean = maximo != null && tipo.prioridad <= maximo.prioridad

    companion object {
        val POR_DEFECTO = COMPLETO
    }
}

/**
 * Algo que el copiloto puede decir. [id] es estable entre recálculos (identifica la frase aunque cambie el [texto]);
 * [puntoM] es la posición en la traza (m) a la que se refiere, o `null` si no caduca por posición (p. ej. un exceso
 * de velocidad).
 */
data class Frase(
    val id: String,
    val texto: String,
    val tipo: TipoFrase,
    val puntoM: Double?,
)
