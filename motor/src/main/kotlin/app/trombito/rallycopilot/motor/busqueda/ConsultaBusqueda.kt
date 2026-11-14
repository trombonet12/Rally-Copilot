package app.trombito.rallycopilot.motor.busqueda

/**
 * Una palabra de la consulta: [escrita] (normalizada) y sus [alternativas] (ella misma primero, y sus sinónimos
 * castellano <-> catalán). Cada una vale como prefijo de una palabra del lugar.
 */
data class PalabraConsulta(
    val escrita: String,
    val alternativas: List<String>,
)

/**
 * Consulta del buscador ya interpretada (pura, sin Android). Se construye con [de]:
 * - normaliza como el índice (`NormalizadorNombre`) y descarta [PALABRAS_VACIAS] (la misma lista que `busqueda.py`);
 * - expande sinónimos por palabra («calle» -> «carrer», «castillo» -> «castell»);
 * - [categoria]: si la consulta es SOLO una palabra de categoría («hospital», «gasolineras»), la `categoria` del
 *   índice (modo «más cercanos de ese tipo»); si no, null;
 * - [pideCalle]: la consulta nombra una vía (calle/carrer, avenida, plaza, paseo, camino, carretera).
 */
data class ConsultaBusqueda(
    val palabras: List<PalabraConsulta>,
    val categoria: String?,
    val pideCalle: Boolean,
) {
    /** Sin ninguna palabra útil (vacía o solo palabras vacías). */
    val vacia: Boolean get() = palabras.isEmpty()

    /** La misma consulta sin la palabra [indice] (relajación: exigir todas menos una). */
    fun sinPalabra(indice: Int): ConsultaBusqueda =
        deEscritas(palabras.filterIndexed { i, _ -> i != indice }.map { it.escrita })

    companion object {
        /** Palabras que `busqueda.py` no indexa; ya normalizadas. */
        val PALABRAS_VACIAS: Set<String> =
            setOf("de", "del", "la", "el", "les", "los", "las", "d", "l", "s", "sa", "es", "en", "i", "y", "a", "al")

        /** La consulta principal de [texto]: la primera de [intentos]. */
        fun de(texto: String): ConsultaBusqueda = intentos(texto).first()

        /**
         * Consultas a probar, de más a menos exigente. Sin comas, una sola (limpia de portal, CP y abreviaturas). Con
         * dirección pegada ([LectorDireccion]): calle + municipio y, si no da nada, solo la calle.
         */
        fun intentos(texto: String): List<ConsultaBusqueda> {
            val direccion = LectorDireccion.leer(texto)
            val sola = deTexto(direccion.calle.ifEmpty { texto })
            val conMunicipio = direccion.municipio?.let { deTexto("${direccion.calle} $it") }
            return listOfNotNull(conMunicipio, sola).filter { !it.vacia }.ifEmpty { listOf(sola) }
        }

        private fun deTexto(texto: String): ConsultaBusqueda {
            val limpias = LimpiezaConsulta.sinDatosDePortal(LimpiezaConsulta.palabras(texto))
            return deEscritas(limpias.filter { it !in PALABRAS_VACIAS })
        }

        private fun deEscritas(escritas: List<String>): ConsultaBusqueda {
            val palabras = escritas.map { PalabraConsulta(it, alternativasDe(it)) }
            val categoria = escritas.singleOrNull()?.let { CATEGORIAS[it] }
            val pideCalle = palabras.any { p -> p.alternativas.any { it in PALABRAS_DE_CALLE } }
            return ConsultaBusqueda(palabras, categoria, pideCalle)
        }

        private fun alternativasDe(palabra: String): List<String> {
            val grupo = SINONIMOS[palabra] ?: return listOf(palabra)
            return listOf(palabra) + grupo.filter { it != palabra }
        }

        /**
         * Nombres propios que cambian entre castellano y catalán en los nombres de calle («Jaume»/«Jaime»,
         * «Colom»/«Colón»); ya normalizados. «Ramón», «Gabriel» o «Tomás» no están: normalizados son iguales.
         */
        private val NOMBRES_PROPIOS: List<List<String>> =
            listOf(
                listOf("sant", "san", "santa"),
                listOf("jaume", "jaime"),
                listOf("colom", "colon"),
                listOf("joan", "juan"),
                listOf("miquel", "miguel"),
                listOf("pere", "pedro"),
                listOf("antoni", "antonio"),
                listOf("josep", "jose"),
                listOf("francesc", "francisco"),
                listOf("rei", "rey"),
                listOf("bartomeu", "bartolome"),
                listOf("jordi", "jorge"),
                listOf("llorenc", "lorenzo"),
                listOf("mateu", "mateo"),
                listOf("andreu", "andres"),
                listOf("nicolau", "nicolas"),
                listOf("sebastia", "sebastian"),
                listOf("pau", "pablo"),
                listOf("esteve", "esteban"),
                listOf("maria", "mariano"),
                listOf("catalina", "caterina"),
            )

        /** Grupos de sinónimos ya normalizados (plaça -> placa, col·legi -> collegi, estación -> estacion). */
        private val GRUPOS: List<List<String>> =
            listOf(
                listOf("calle", "carrer"),
                listOf("avenida", "avinguda"),
                listOf("plaza", "placa"),
                listOf("paseo", "passeig"),
                listOf("camino", "cami"),
                listOf("castillo", "castell"),
                listOf("iglesia", "esglesia"),
                listOf("playa", "platja"),
                listOf("puerto", "port"),
                listOf("faro", "far"),
                listOf("monasterio", "monestir"),
                listOf("cuevas", "coves"),
                listOf("cueva", "cova"),
                listOf("mercado", "mercat"),
                listOf("ayuntamiento", "ajuntament"),
                listOf("colegio", "collegi"),
                listOf("estacion", "estacio"),
                listOf("museo", "museu"),
                listOf("aeropuerto", "aeroport"),
                listOf("aparcamiento", "aparcament"),
                listOf("restaurante", "restaurant"),
                listOf("supermercado", "supermercat"),
                listOf("fuente", "font"),
                listOf("isla", "illa"),
                listOf("gasolinera", "benzinera"),
            ) + NOMBRES_PROPIOS

        private val SINONIMOS: Map<String, List<String>> =
            buildMap { for (grupo in GRUPOS) for (p in grupo) put(p, grupo) }

        internal val PALABRAS_DE_CALLE: Set<String> =
            setOf(
                "calle",
                "carrer",
                "avenida",
                "avinguda",
                "plaza",
                "placa",
                "paseo",
                "passeig",
                "camino",
                "cami",
                "carretera",
            )

        /** Palabra de categoría -> `categoria` del índice (singular, plural, castellano y catalán). */
        private val CATEGORIAS: Map<String, String> =
            buildMap {
                fun alta(
                    categoria: String,
                    vararg palabras: String,
                ) = palabras.forEach { put(it, categoria) }
                alta("hospital", "hospital", "hospitales", "hospitals")
                alta("gasolinera", "gasolinera", "gasolineras", "benzinera", "benzineres")
                alta("farmacia", "farmacia", "farmacias", "farmacies")
                alta("supermercado", "supermercado", "supermercados", "supermercat", "supermercats")
                alta("playa", "playa", "playas", "platja", "platges")
                alta("cala", "cala", "calas", "cales")
                alta("castillo", "castillo", "castillos", "castell", "castells")
                alta("mirador", "mirador", "miradores", "miradors")
                alta("aparcamiento", "aparcamiento", "aparcamientos", "aparcament", "aparcaments", "parking")
                alta("restaurante", "restaurante", "restaurantes", "restaurant", "restaurants")
                alta("hotel", "hotel", "hoteles", "hotels")
                alta("museo", "museo", "museos", "museu", "museus")
                alta("faro", "faro", "faros", "far", "fars")
                alta("aeropuerto", "aeropuerto", "aeropuertos", "aeroport", "aeroports")
                alta("puerto", "puerto", "puertos", "port", "ports")
                alta("iglesia", "iglesia", "iglesias", "esglesia", "esglesies")
                alta("monasterio", "monasterio", "monasterios", "monestir", "monestirs")
                alta("cafeteria", "cafeteria", "cafeterias", "cafe", "cafes")
            }
    }
}
