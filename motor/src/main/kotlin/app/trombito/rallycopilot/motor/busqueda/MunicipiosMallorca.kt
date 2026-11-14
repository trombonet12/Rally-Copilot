package app.trombito.rallycopilot.motor.busqueda

/**
 * Los municipios de Mallorca tal como están en la columna `municipio` del índice de búsqueda, con las grafías
 * castellanas y variantes habituales de las direcciones («Palma de Mallorca», «Pollensa», «San Juan»).
 * Solo sirve para reconocer el municipio al pegar una dirección; no es un catálogo de lugares.
 */
internal object MunicipiosMallorca {
    private val OFICIALES: List<String> =
        (
            "Alaró|Alcúdia|Algaida|Andratx|Ariany|Artà|Banyalbufar|Binissalem|Bunyola|Búger|Calvià|Campanet|Campos|" +
                "Capdepera|Consell|Costitx|Deià|Escorca|Esporles|Estellencs|Felanitx|Fornalutx|Inca|" +
                "Lloret de Vistalegre|Lloseta|Llubí|Llucmajor|Manacor|Mancor de la Vall|Maria de la Salut|Marratxí|" +
                "Montuïri|Muro|Palma|Petra|Pollença|Porreres|Puigpunyent|Sant Joan|Sant Llorenç des Cardassar|" +
                "Santa Eugènia|Santa Margalida|Santa Maria del Camí|Santanyí|Selva|Sencelles|Sineu|Son Servera|" +
                "Sóller|Valldemossa|Vilafranca de Bonany|sa Pobla|ses Salines"
        ).split('|')

    /** Variante -> nombre del índice. */
    private val ALIAS =
        mapOf(
            "Palma de Mallorca" to "Palma",
            "Pollensa" to "Pollença",
            "San Juan" to "Sant Joan",
            "San Lorenzo del Cardassar" to "Sant Llorenç des Cardassar",
            "San Lorenzo" to "Sant Llorenç des Cardassar",
            "Santa Margarita" to "Santa Margalida",
            "Las Salinas" to "ses Salines",
            "Salinas" to "ses Salines",
            "La Puebla" to "sa Pobla",
            "Puebla" to "sa Pobla",
            "Esporlas" to "Esporles",
            "Lluchmayor" to "Llucmajor",
            "Andraitx" to "Andratx",
            "Bañalbufar" to "Banyalbufar",
            "Buñola" to "Bunyola",
            "Binisalem" to "Binissalem",
            "Valldemosa" to "Valldemossa",
            "Villafranca de Bonany" to "Vilafranca de Bonany",
            "Mancor del Valle" to "Mancor de la Vall",
            "Santa Maria del Camino" to "Santa Maria del Camí",
            "Deya" to "Deià",
        )

    /** Nombre normalizado (como [NormalizadorNombre]) -> nombre del índice. */
    private val POR_NOMBRE: Map<String, String> =
        buildMap {
            for (m in OFICIALES) put(NormalizadorNombre.normalizar(m), m)
            for ((variante, oficial) in ALIAS) put(NormalizadorNombre.normalizar(variante), oficial)
        }

    private val TOKENS_POR_NOMBRE: List<Pair<List<String>, String>> =
        POR_NOMBRE.map { (clave, oficial) -> clave.split(' ') to oficial }.sortedByDescending { it.first.size }

    /** El nombre del índice si [normalizado] (ya normalizado) es exactamente un municipio. */
    fun oficial(normalizado: String): String? = POR_NOMBRE[normalizado]

    /**
     * Cuántas palabras del final de [tokens] (normalizados) forman un municipio, y su nombre del índice; null si el
     * final no es un municipio o si el municipio es toda la lista.
     */
    fun cola(tokens: List<String>): Pair<Int, String>? =
        TOKENS_POR_NOMBRE
            .firstOrNull { (palabras, _) -> palabras.size < tokens.size && tokens.takeLast(palabras.size) == palabras }
            ?.let { (palabras, oficial) -> palabras.size to oficial }
}
