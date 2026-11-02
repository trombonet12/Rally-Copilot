package app.trombito.rallycopilot.motor.edicion

import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.notas.Nota
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
            }
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
