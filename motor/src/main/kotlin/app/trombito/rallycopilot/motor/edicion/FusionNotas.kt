package app.trombito.rallycopilot.motor.edicion

import app.trombito.rallycopilot.motor.notas.Aviso
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.FrasesNota
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.notas.Modificador
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.notas.limpiarTextoLibre
import kotlin.math.abs
import kotlin.math.max

/**
 * Fusión de la hoja automática de un tramo con las notas editadas por Joan: las editadas SIEMPRE prevalecen. Se
 * guardan las ediciones (no el resultado), así que al regenerar la hoja automática (otro mapa, otra calibración, la
 * geometría desplazada unos metros) basta volver a aplicarlas: cada edición busca la nota automática más cercana.
 */
object FusionNotas {
    /** Distancia máxima (m) entre una edición y el borde de la curva a la que afecta. */
    const val ALCANCE_M = 30.0

    /** Distancia máxima (m) entre la edición y el aviso suelto al que afecta [AccionEdicion.QuitarAviso]. */
    const val ALCANCE_AVISO_M = 15.0

    /** Una curva más corta que esto no se puede dividir; cada mitad conserva al menos [MARGEN_CORTE_M]. */
    const val LARGO_MIN_DIVISION_M = 2.0
    private const val MARGEN_CORTE_M = 1.0

    /** Una curva a menos de esto de la siguiente va «unida» (sin distancia), como en `GeneradorNotas`. */
    private const val HUECO_UNIDA_M = 20.0

    // Copia de las distancias del generador de notas (GeneradorNotas.DISTANCIAS_M, privada).
    private val DISTANCIAS_M =
        listOf(10, 20, 30, 40, 50, 60, 70, 80, 100, 120, 150, 200, 250, 300, 400, 500, 600, 700, 800)

    /**
     * Aplica [ediciones] (todas del mismo tramo, por [NotasEditadas.marcaTiempoMs]) sobre [hojaAutomatica]. La hoja
     * debe haberse generado sobre la traza del tramo; [posicionesEnTramo] convierte la posición de un elemento de la
     * hoja en metros del tramo (la identidad si ya coinciden) y solo se usa para emparejar y ordenar: las posiciones
     * de las notas automáticas no se tocan.
     *
     * - `Sustituir`: cambia la nota más cercana a [ALCANCE_M] o menos (conserva posición y avisos); sin ella, añade.
     * - `Quitar`: elimina la más cercana (sus avisos unidos quedan sueltos); sin ella, no hace nada.
     * - `Anadir`: inserta la nota en orden. `Cuidado`: marca la más cercana.
     * - `Fusionar`: une la más cercana con la siguiente. `Dividir`: parte la más cercana en dos.
     * - `AnadirAviso`: inserta un aviso suelto. `QuitarAviso`: quita el aviso suelto más cercano.
     *
     * Recalcula la distancia a la siguiente y la «y» de las notas tocadas y de la anterior a cada cambio, con el
     * hueco de curva a curva como `GeneradorNotas` (sin el criterio de 1 s, que necesita la velocidad); una distancia
     * dictada en la nota editada se respeta. El resto de la hoja queda tal cual.
     */
    fun aplicar(
        hojaAutomatica: HojaNotas,
        posicionesEnTramo: (Double) -> Double,
        ediciones: List<NotasEditadas>,
    ): HojaNotas {
        require(ediciones.map { it.tramoId }.distinct().size <= 1) { "Ediciones de varios tramos" }
        if (ediciones.isEmpty()) return hojaAutomatica
        val trabajo = Trabajo(hojaAutomatica.elementos.map { Item(it, posicionesEnTramo(it.posicionM)) })
        ediciones.sortedBy { it.marcaTiempoMs }.forEach { trabajo.aplicar(it) }
        return HojaNotas(trabajo.resultado())
    }

    /** Elemento de la hoja con su posición en el tramo y si una edición lo ha tocado. */
    private class Item(
        val elemento: ElementoHoja,
        val clave: Double,
        val tocado: Boolean = false,
        val dictada: Boolean = false,
    ) {
        val nota: Nota? get() = (elemento as? ElementoHoja.DeNota)?.nota
        val largo: Double get() = nota?.let { it.finM - it.posicionM } ?: 0.0

        /** Distancia de [posicionM] a la curva (0 si cae dentro). */
        fun distancia(posicionM: Double): Double =
            when {
                posicionM < clave -> clave - posicionM
                posicionM > clave + largo -> posicionM - (clave + largo)
                else -> 0.0
            }
    }

    /**
     * Nota que resulta de unir [a] y [b]: dirección de la primera, el grado más cerrado (la horquilla es el más
     * cerrado), «cuidado» si alguna lo lleva y la distancia dictada de la segunda. Los matices se suman: larga y muy
     * larga dan muy larga, cierra y abre se anulan y la escuadra solo se queda si lo eran las dos. Con texto libre en
     * alguna, el texto es el de las dos frases unidas por una coma.
     */
    private fun combinar(
        a: Nota,
        b: Nota,
    ): Nota {
        val grado = if (a.grado == null || b.grado == null) null else minOf(a.grado, b.grado)
        val conTexto = a.textoPropio != null || b.textoPropio != null
        val texto = if (conTexto) limpiarTextoLibre(listOf(a, b).joinToString(", ") { frasePropia(it) }) else null
        return a.copy(
            grado = grado,
            modificadores = modificadoresUnidos(a.modificadores, b.modificadores),
            distanciaSiguienteM = b.distanciaSiguienteM,
            unidaConSiguiente = b.unidaConSiguiente,
            cuidado = a.cuidado || b.cuidado,
            textoLibre = texto,
        )
    }

    private fun frasePropia(nota: Nota): String =
        FrasesNota.frase(nota.copy(distanciaSiguienteM = null, unidaConSiguiente = false))

    private fun modificadoresUnidos(
        a: Set<Modificador>,
        b: Set<Modificador>,
    ): Set<Modificador> {
        if (Modificador.ESCUADRA in a && Modificador.ESCUADRA in b) return setOf(Modificador.ESCUADRA)
        val juntos = a + b
        val largo =
            when {
                Modificador.MUY_LARGA in juntos -> setOf(Modificador.MUY_LARGA)
                Modificador.LARGA in juntos -> setOf(Modificador.LARGA)
                else -> emptySet()
            }
        val giro =
            when {
                Modificador.CIERRA in juntos && Modificador.ABRE in juntos -> emptySet()
                else -> juntos.intersect(setOf(Modificador.CIERRA, Modificador.ABRE))
            }
        return largo + giro
    }

    @Suppress("TooManyFunctions") // una función por acción de edición y sus auxiliares
    private class Trabajo(
        elementos: List<Item>,
    ) {
        private val items = elementos.toMutableList()
        private val quitadasM = mutableListOf<Double>()

        fun aplicar(edicion: NotasEditadas) {
            val p = edicion.posicionM
            when (val accion = edicion.accion) {
                is AccionEdicion.Anadir -> anadir(p, accion.nota)
                is AccionEdicion.Sustituir -> cercana(p)?.let { sustituir(it, accion.nota) } ?: anadir(p, accion.nota)
                AccionEdicion.Quitar -> cercana(p)?.let { quitar(it) }
                AccionEdicion.Cuidado -> cercana(p)?.let { marcarCuidado(it) }
                else -> aplicarEstructura(p, accion)
            }
        }

        /** Las acciones del editor completo: fusionar, dividir y avisos sueltos. */
        private fun aplicarEstructura(
            p: Double,
            accion: AccionEdicion,
        ) {
            when (accion) {
                AccionEdicion.Fusionar -> cercana(p)?.let { fusionar(it) }
                AccionEdicion.Dividir -> cercana(p)?.let { dividir(it, p) }
                is AccionEdicion.AnadirAviso -> anadirAviso(p, accion)
                AccionEdicion.QuitarAviso -> avisoCercano(p)?.let { items.removeAt(it) }
                else -> Unit
            }
        }

        private fun anadirAviso(
            posicionM: Double,
            accion: AccionEdicion.AnadirAviso,
        ) = insertar(Item(ElementoHoja.DeAviso(Aviso(posicionM, accion.tipo)), posicionM, tocado = true))

        private fun avisoCercano(posicionM: Double): Int? =
            items.indices
                .filter {
                    items[it].elemento is ElementoHoja.DeAviso && abs(items[it].clave - posicionM) <= ALCANCE_AVISO_M
                }.minByOrNull { abs(items[it].clave - posicionM) }

        /** Índice de la nota que va justo después de la de [indice] (por posición en el tramo), si la hay. */
        private fun siguienteNota(indice: Int): Int? =
            items.indices
                .filter { it != indice && items[it].nota != null && items[it].clave >= items[indice].clave }
                .minWithOrNull(compareBy<Int> { items[it].clave }.thenBy { it })

        private fun fusionar(indice: Int) {
            val sig = siguienteNota(indice) ?: return
            val primera = items[indice]
            val segunda = items[sig]
            val deLaPrimera = primera.elemento as ElementoHoja.DeNota
            val deLaSegunda = segunda.elemento as ElementoHoja.DeNota
            val largo = (segunda.clave + segunda.largo) - primera.clave
            val nota =
                combinar(deLaPrimera.nota, deLaSegunda.nota).copy(
                    posicionM = deLaPrimera.nota.posicionM,
                    finM = deLaPrimera.nota.posicionM + largo,
                )
            val fusionada =
                deLaPrimera.copy(
                    nota = nota,
                    avisosUnidos = deLaPrimera.avisosUnidos + deLaSegunda.avisosUnidos,
                    sobrePuente = deLaPrimera.sobrePuente ?: deLaSegunda.sobrePuente,
                )
            items[indice] = Item(fusionada, primera.clave, tocado = true, dictada = segunda.dictada)
            items.removeAt(sig)
        }

        private fun dividir(
            indice: Int,
            posicionM: Double,
        ) {
            val item = items[indice]
            if (item.largo < LARGO_MIN_DIVISION_M) return
            val elemento = item.elemento as ElementoHoja.DeNota
            val nota = elemento.nota
            val dentro = posicionM > item.clave && posicionM < item.clave + item.largo
            val corte =
                if (dentro) {
                    posicionM.coerceIn(item.clave + MARGEN_CORTE_M, item.clave + item.largo - MARGEN_CORTE_M)
                } else {
                    item.clave + item.largo / 2.0
                }
            val corteHoja = nota.posicionM + (corte - item.clave)
            val puente = elemento.sobrePuente
            val puenteEnPrimera = puente == null || puente.posicionM <= corteHoja
            val primera =
                elemento.copy(
                    nota = nota.copy(finM = corteHoja, unidaConSiguiente = true, distanciaSiguienteM = null),
                    sobrePuente = puente.takeIf { puenteEnPrimera },
                )
            val segunda =
                ElementoHoja.DeNota(
                    nota = nota.copy(posicionM = corteHoja, cuidado = false, textoLibre = null),
                    sobrePuente = puente.takeUnless { puenteEnPrimera },
                )
            items[indice] = Item(primera, item.clave, tocado = true)
            insertar(Item(segunda, corte, tocado = true, dictada = item.dictada))
        }

        private fun cercana(posicionM: Double): Int? =
            items.indices
                .filter { items[it].nota != null && items[it].distancia(posicionM) <= ALCANCE_M }
                .minWithOrNull(
                    compareBy<Int> { items[it].distancia(posicionM) }.thenBy { abs(items[it].clave - posicionM) },
                )

        private fun anadir(
            posicionM: Double,
            nota: Nota,
        ) {
            val colocada = nota.copy(posicionM = posicionM, finM = posicionM + max(0.0, nota.finM - nota.posicionM))
            val dictada = nota.distanciaSiguienteM != null
            insertar(Item(ElementoHoja.DeNota(colocada), posicionM, tocado = true, dictada = dictada))
        }

        private fun sustituir(
            indice: Int,
            nueva: Nota,
        ) {
            val actual = items[indice].elemento as ElementoHoja.DeNota
            val vieja = actual.nota
            val cambiada =
                nueva.copy(
                    posicionM = vieja.posicionM,
                    finM = vieja.finM,
                    unidaConSiguiente = vieja.unidaConSiguiente,
                )
            items[indice] =
                Item(
                    actual.copy(nota = cambiada),
                    items[indice].clave,
                    tocado = true,
                    dictada = nueva.distanciaSiguienteM != null,
                )
        }

        private fun quitar(indice: Int) {
            val item = items.removeAt(indice)
            val actual = item.elemento as ElementoHoja.DeNota
            quitadasM += item.clave
            val desplazamiento = item.clave - actual.posicionM
            (actual.avisosUnidos + listOfNotNull(actual.sobrePuente)).forEach {
                insertar(Item(ElementoHoja.DeAviso(it), it.posicionM + desplazamiento))
            }
        }

        private fun marcarCuidado(indice: Int) {
            val item = items[indice]
            val actual = item.elemento as ElementoHoja.DeNota
            items[indice] =
                Item(actual.copy(nota = actual.nota.copy(cuidado = true)), item.clave, item.tocado, item.dictada)
        }

        /** Añade el elemento; el orden final lo da [resultado] (a igual posición, el aviso antes que la nota). */
        private fun insertar(item: Item) {
            items.add(item)
        }

        fun resultado(): List<ElementoHoja> {
            val ordenados = items.sortedWith(compareBy<Item> { it.clave }.thenBy { it.elemento is ElementoHoja.DeNota })
            val notas = ordenados.filter { it.nota != null }
            val recalculadas = HashMap<Item, Nota>()
            notas.forEachIndexed { i, item ->
                val siguiente = notas.getOrNull(i + 1)
                val sucia =
                    item.tocado ||
                        siguiente?.tocado == true ||
                        quitadasM.any { it > item.clave && (siguiente == null || it < siguiente.clave) }
                if (sucia) recalculadas[item] = recalcular(item, siguiente)
            }
            return ordenados.map { item ->
                val nota = recalculadas[item]
                val actual = item.elemento
                if (nota != null && actual is ElementoHoja.DeNota) actual.copy(nota = nota) else actual
            }
        }

        private fun recalcular(
            item: Item,
            siguiente: Item?,
        ): Nota {
            val nota = item.nota ?: error("Solo se recalculan notas")
            val hueco = siguiente?.let { it.clave - (item.clave + item.largo) }
            return when {
                hueco == null -> nota.copy(distanciaSiguienteM = null, unidaConSiguiente = false)
                item.dictada && nota.distanciaSiguienteM != null -> nota.copy(unidaConSiguiente = false)
                hueco < HUECO_UNIDA_M -> nota.copy(distanciaSiguienteM = null, unidaConSiguiente = true)
                else -> nota.copy(distanciaSiguienteM = DISTANCIAS_M.last { it <= hueco }, unidaConSiguiente = false)
            }
        }
    }
}
