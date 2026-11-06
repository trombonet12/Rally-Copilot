package app.trombito.rallycopilot.motor.edicion

/**
 * Reconoce en un texto las órdenes de frase fija de [FrasesOrdenes]. Compara palabras normalizadas y sin relleno, así
 * que «oye copiloto, ¿cuánto falta por favor?» y «cuánto falta» son lo mismo. Con frases de dos órdenes distintas, o
 * con palabras que no son relleno ni desconocidas (una nota, otra orden), no hay orden: contradicción.
 */
internal object OrdenesDirectas {
    /** Lo que sale de buscar frases fijas: la [orden], o `null` si hay frases pero no valen (ruido, contradicción). */
    class Coincidencia(
        val orden: Orden?,
    )

    private class Directa(
        val palabras: List<String>,
        val orden: Orden,
    )

    private val DIRECTAS: List<Directa> =
        FrasesOrdenes.porOrden
            .flatMap { (orden, frases) ->
                frases.map { Directa(InterpreteOrdenes.sinRelleno(InterpreteOrdenes.palabras(it)), orden) }
            }.distinctBy { it.palabras to it.orden }
            // Las más largas primero: «modo satélite» antes que «satélite».
            .sortedByDescending { it.palabras.size }

    /**
     * Busca las frases fijas en las [utiles] (palabras sin relleno). `null` si no hay ninguna (se sigue con las notas y
     * las órdenes sueltas); si las hay, valen cuando son de una sola orden y lo que sobra es, como mucho,
     * [InterpreteOrdenes.RUIDO_MAX] palabras para las que [esDesconocida] da verdadero.
     */
    fun buscar(
        utiles: List<String>,
        esDesconocida: (String) -> Boolean,
    ): Coincidencia? {
        val exacta = DIRECTAS.firstOrNull { it.orden in FrasesOrdenes.EXACTAS && it.palabras == utiles }
        if (exacta != null) return Coincidencia(exacta.orden)
        val usadas = BooleanArray(utiles.size)
        val ordenes = LinkedHashSet<Orden>()
        for (frase in DIRECTAS) {
            if (frase.orden !in FrasesOrdenes.EXACTAS) marcarCoincidencias(utiles, frase, usadas)?.let { ordenes += it }
        }
        val resto = utiles.filterIndexed { i, _ -> !usadas[i] }
        val limpio = resto.size <= InterpreteOrdenes.RUIDO_MAX && resto.all(esDesconocida)
        return if (ordenes.isEmpty()) null else Coincidencia(ordenes.singleOrNull()?.takeIf { limpio })
    }

    /** Marca en [usadas] las palabras donde aparece [frase] sin pisar otra ya marcada; devuelve su orden si aparece. */
    private fun marcarCoincidencias(
        utiles: List<String>,
        frase: Directa,
        usadas: BooleanArray,
    ): Orden? {
        val n = frase.palabras.size
        var encontrada = false
        var i = 0
        while (i <= utiles.size - n) {
            val aqui = utiles.subList(i, i + n) == frase.palabras && (i until i + n).none { usadas[it] }
            if (aqui) {
                for (j in i until i + n) usadas[j] = true
                encontrada = true
                i += n
            } else {
                i++
            }
        }
        return frase.orden.takeIf { encontrada }
    }
}
