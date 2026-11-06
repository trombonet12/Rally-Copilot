package app.trombito.rallycopilot.motor.edicion

/**
 * Reconoce las órdenes que llevan un lugar detrás: «llévame a Sóller» / «vamos a Sóller» ([Orden.IrA]) y «añade parada
 * en Inca» ([Orden.ParadaEn]); «llévame a casa» es [Orden.VolverACasa]. Se mira antes que las frases fijas para que un
 * nombre con una palabra de orden («Son Salida») no se confunda con ella. El lugar es lo que queda tras el disparador,
 * sin el relleno del principio («a», «en») ni el del final («por favor»), normalizado como el resto de palabras.
 */
internal object OrdenesLugar {
    private class Disparador(
        val palabras: List<String>,
        val crear: (String) -> Orden,
    )

    private val DISPARADORES: List<Disparador> =
        listOf(
            Disparador(listOf("llevame")) { Orden.IrA(it) },
            Disparador(listOf("vamos")) { Orden.IrA(it) },
            Disparador(listOf("anade", "parada")) { Orden.ParadaEn(it) },
        )

    private const val CASA = "casa"

    /** La orden con lugar que dicen [palabras] (normalizadas); `null` sin disparador al principio o sin lugar. */
    fun interpretar(palabras: List<String>): Orden? {
        val inicio = palabras.dropWhile(InterpreteOrdenes::esRelleno)
        return DISPARADORES.firstNotNullOfOrNull { disparador ->
            val resto = despuesDe(disparador, inicio)
            val lugar =
                resto
                    ?.dropWhile(InterpreteOrdenes::esRelleno)
                    ?.dropLastWhile(InterpreteOrdenes::esRelleno)
                    ?.joinToString(" ")
            when {
                lugar.isNullOrEmpty() -> null
                lugar == CASA && disparador.palabras.first() != "anade" -> Orden.VolverACasa
                else -> disparador.crear(lugar)
            }
        }
    }

    /** Lo que sigue al disparador si [palabras] empieza por él (con relleno entre sus palabras); si no, `null`. */
    private fun despuesDe(
        disparador: Disparador,
        palabras: List<String>,
    ): List<String>? {
        var i = 0
        for (palabra in disparador.palabras) {
            while (i < palabras.size && InterpreteOrdenes.esRelleno(palabras[i]) && palabras[i] != palabra) i++
            if (i >= palabras.size || palabras[i] != palabra) return null
            i++
        }
        return palabras.drop(i)
    }
}
