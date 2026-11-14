package app.trombito.rallycopilot.motor.busqueda

/**
 * Limpieza del texto de una consulta antes de buscar (pura): palabras normalizadas con las abreviaturas de vía
 * expandidas («c/», «avda.», «pza»…) y sin los datos de portal que el índice no tiene (número, código postal, piso y
 * puerta) cuando van al final.
 */
internal object LimpiezaConsulta {
    private val ABREVIATURAS: Map<String, String> =
        buildMap {
            for (a in listOf("c", "cl", "cr")) put(a, "calle")
            for (a in listOf("avda", "av", "avd")) put(a, "avenida")
            for (a in listOf("pza", "pl", "pc")) put(a, "plaza")
            for (a in listOf("ctra", "cra")) put(a, "carretera")
            for (a in listOf("pso", "pg")) put(a, "paseo")
            for (a in listOf("cno", "cmno")) put(a, "camino")
        }

    /** Piso y puerta, ya normalizados. */
    private val PISO_Y_PUERTA: Set<String> =
        (
            "bajos bajo bjs bj entlo entresuelo pral principal atico izq izda izquierda " +
                "dcha der derecha puerta pta piso esc escalera bis"
        ).split(' ').toSet()

    /** «nº 5», «num 5»: la marca que precede al número de portal. */
    private val MARCAS_NUMERO = setOf("n", "no", "nº", "n°", "num", "numero")

    /** Códigos de carretera: «Ma-13», «PM-1032»: el número es parte del nombre. */
    private val CODIGOS_DE_VIA = setOf("ma", "pm", "ap", "cm", "pma")

    private val NUMERO = Regex("""\d{1,5}[ºª°]?[a-z]?""")
    private val LETRA_SUELTA = Regex("""[a-z]""")

    /** Palabras normalizadas, sin signos en los bordes, con `/` como separador y las abreviaturas de vía expandidas. */
    fun palabras(texto: String): List<String> =
        NormalizadorNombre
            .normalizar(texto.replace('/', ' '))
            .split(' ')
            .map { palabra -> palabra.trim { !it.isLetterOrDigit() } }
            .filter { it.isNotEmpty() }
            .map { ABREVIATURAS[it] ?: it }

    /**
     * Quita del final número de portal, código postal, piso y puerta («5», «3-B», «07002», «bajos», «2ºA»). Si la
     * consulta acaba en municipio, lo respeta y limpia lo anterior («Colom 5 Palma de Mallorca» -> «colom palma»).
     * No toca códigos de carretera («Ma-13») ni deja la consulta sin nombre.
     */
    fun sinDatosDePortal(palabras: List<String>): List<String> {
        val municipio = MunicipiosMallorca.cola(palabras)
        if (municipio == null) return recortar(palabras)
        val (cuantas, oficial) = municipio
        val cuerpo = recortar(palabras.dropLast(cuantas))
        return cuerpo + NormalizadorNombre.normalizar(oficial).split(' ')
    }

    private fun recortar(palabras: List<String>): List<String> {
        var fin = palabras.size
        var quitadoNumero = false
        while (fin > 0 && puedeQuitarse(palabras, fin, quitadoNumero)) {
            if (esNumero(palabras[fin - 1])) quitadoNumero = true
            fin--
        }
        return palabras.subList(0, fin)
    }

    private fun puedeQuitarse(
        palabras: List<String>,
        fin: Int,
        quitadoNumero: Boolean,
    ): Boolean {
        val ultima = palabras[fin - 1]
        val anterior = palabras.getOrNull(fin - 2)
        val quitable =
            when {
                esNumero(ultima) -> anterior == null || anterior !in CODIGOS_DE_VIA
                ultima in PISO_Y_PUERTA -> true
                LETRA_SUELTA.matches(ultima) -> anterior != null && esNumero(anterior)
                else -> ultima in MARCAS_NUMERO && quitadoNumero
            }
        return quitable && quedaNombre(palabras.subList(0, fin - 1))
    }

    private fun esNumero(palabra: String) = NUMERO.matches(palabra)

    /** Que tras quitar algo siga habiendo una palabra con letras que no sea solo «calle», «carrer»… ni «de». */
    private fun quedaNombre(resto: List<String>): Boolean =
        resto.any { p ->
            p.any { it.isLetter() } &&
                p !in ConsultaBusqueda.PALABRAS_DE_CALLE &&
                p !in ConsultaBusqueda.PALABRAS_VACIAS
        }
}
