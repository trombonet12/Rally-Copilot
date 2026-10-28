package app.trombito.rallycopilot.motor.ruta

/** Frases de navegación en español con estilo copiloto («derecha, Ma-10»). */
object FrasesManiobra {
    private val REFERENCIA = Regex("^(MA|AP|PM|A|E|N|C)-\\d+\\w*$", RegexOption.IGNORE_CASE)
    private val ORDINALES =
        listOf(
            "primera",
            "segunda",
            "tercera",
            "cuarta",
            "quinta",
            "sexta",
            "séptima",
            "octava",
            "novena",
            "décima",
        )

    /** Frase base de cada tipo; la vía se añade detrás. Los tipos que no están aquí se resuelven en [frase]. */
    private val BASES =
        mapOf(
            TipoManiobra.DERECHA to "cruce, derecha",
            // Los giros normales (no ligeros) se cantan como «cruce».
            TipoManiobra.IZQUIERDA to "cruce, izquierda",
            TipoManiobra.CERRADA_DERECHA to "cruce, cerrada derecha",
            TipoManiobra.CERRADA_IZQUIERDA to "cruce, cerrada izquierda",
            TipoManiobra.LIGERA_DERECHA to "ligera derecha",
            TipoManiobra.LIGERA_IZQUIERDA to "ligera izquierda",
            TipoManiobra.RECTO to "recto",
            TipoManiobra.BIFURCACION_DERECHA to "mantente a la derecha",
            TipoManiobra.BIFURCACION_IZQUIERDA to "mantente a la izquierda",
            TipoManiobra.SALIDA_AUTOVIA_DERECHA to "salida",
            TipoManiobra.SALIDA_AUTOVIA_IZQUIERDA to "salida",
            TipoManiobra.INCORPORACION to "incorpórate",
        )

    /** Frases fijas, sin vía. */
    private val FIJAS =
        mapOf(
            TipoManiobra.ROTONDA_SALIDA to "salida de la rotonda",
            TipoManiobra.CAMBIO_SENTIDO to "cambio de sentido",
            TipoManiobra.DESTINO to "destino",
            TipoManiobra.SALIDA to "en marcha",
            TipoManiobra.FERRY to "ferri",
        )

    /** Si el tipo es [TipoManiobra.OTRA] o falta información, devuelve la instrucción de Valhalla. */
    fun frase(m: Maniobra): String {
        FIJAS[m.tipo]?.let { return it }
        val via = viaPrincipal(m.vias)
        return when (val base = BASES[m.tipo]) {
            null -> if (m.tipo == TipoManiobra.ROTONDA_ENTRADA) rotonda(m, via) else m.instruccionValhalla
            else -> con(base, via)
        }
    }

    private fun rotonda(
        m: Maniobra,
        via: String?,
    ): String {
        val ordinal = m.salidaRotonda?.let { ORDINALES.getOrNull(it - 1) } ?: return m.instruccionValhalla
        return con("rotonda, $ordinal salida", via)
    }

    private fun con(
        base: String,
        via: String?,
    ): String = if (via == null) base else "$base, $via"

    /** Primera referencia tipo Ma-/AP-/A-; si no hay, el primer nombre. */
    private fun viaPrincipal(vias: List<String>): String? {
        val limpias = vias.map { it.trim() }.filter { it.isNotEmpty() }
        return limpias.firstOrNull { REFERENCIA.matches(it) } ?: limpias.firstOrNull()
    }
}
