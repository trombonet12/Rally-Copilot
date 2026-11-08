package app.trombito.rallycopilot.motor.voz

import app.trombito.rallycopilot.motor.notas.DISTANCIAS_NOTA_M
import app.trombito.rallycopilot.motor.notas.ElementoHoja

/** Un grupo de la hoja que se canta en una sola frase: sus [elementos] originales y el [texto] resultante. */
internal class GrupoLlamada(
    val elementos: List<ElementoHoja>,
    val texto: String,
)

/**
 * Cadenas de notas de la hoja («izquierda tres y derecha cuatro»): cómo se parten en llamadas y qué cola lleva la
 * última nota de cada una. Lógica pura al servicio de [LlamadasNotas].
 */
internal object CadenasNotas {
    /**
     * Notas de la llamada que abre [nota], o `null` si [nota] no abre ninguna (es la 2.ª o 3.ª de su llamada). Una
     * nota que no está en [elementos] es una llamada de una sola nota.
     */
    fun grupoDe(
        elementos: List<ElementoHoja>,
        nota: ElementoHoja.DeNota,
        simplificada: Boolean,
    ): GrupoLlamada? {
        val indice = indiceDe(elementos, nota)
        return if (indice < 0) {
            GrupoLlamada(listOf(nota), nota.frase(simplificada))
        } else {
            grupoEnCadena(elementos, indice, simplificada)
        }
    }

    private fun grupoEnCadena(
        elementos: List<ElementoHoja>,
        indice: Int,
        simplificada: Boolean,
    ): GrupoLlamada? {
        val carrera =
            carreras(elementos, inicioDeCadena(elementos, indice), simplificada).firstOrNull { indice in it }
        val tramo = carrera?.let { c -> llamadas(elementos, c, simplificada).firstOrNull { c[it.first] == indice } }
        return if (carrera == null || tramo == null) {
            null
        } else {
            GrupoLlamada(
                tramo.map { elementos[carrera[it]] },
                textoDe(elementos, carrera, tramo.first, tramo.last + 1, simplificada),
            )
        }
    }

    /** Índice de la primera nota de la cadena a la que pertenece la de [indice]. */
    private fun inicioDeCadena(
        elementos: List<ElementoHoja>,
        indice: Int,
    ): Int {
        fun unida(k: Int) = (elementos[k] as? ElementoHoja.DeNota)?.nota?.unidaConSiguiente == true
        var inicio = indice
        while (inicio > 0 && unida(inicio - 1)) inicio--
        return inicio
    }

    /**
     * Rachas de notas que se cantan y van seguidas dentro de la cadena que empieza en [inicio] (índices en
     * [elementos]); una nota que no se canta, o un aviso suelto, corta la racha.
     */
    private fun carreras(
        elementos: List<ElementoHoja>,
        inicio: Int,
        simplificada: Boolean,
    ): List<List<Int>> {
        val carreras = ArrayList<List<Int>>()
        var actual = ArrayList<Int>()
        var k = inicio
        var sigue = true
        while (sigue && k < elementos.size) {
            val nota = elementos[k] as? ElementoHoja.DeNota
            sigue = nota != null && nota.nota.unidaConSiguiente
            if (nota != null && CandidatasNotas.seCanta(nota, simplificada)) {
                actual.add(k)
            } else if (actual.isNotEmpty()) {
                carreras.add(actual)
                actual = ArrayList()
            }
            k++
        }
        if (actual.isNotEmpty()) carreras.add(actual)
        return carreras
    }

    /** Las llamadas en que se parte una [carrera], como rangos de posiciones dentro de ella. */
    private fun llamadas(
        elementos: List<ElementoHoja>,
        carrera: List<Int>,
        simplificada: Boolean,
    ): List<IntRange> {
        val tramos = ArrayList<IntRange>()
        var desde = 0
        while (desde < carrera.size) {
            val hasta = finDeLlamada(elementos, carrera, desde, simplificada)
            tramos.add(desde until hasta)
            desde = hasta
        }
        return tramos
    }

    /** Fin (exclusivo, posición en [carrera]) de la llamada que empieza en [desde]: tope de notas y de palabras. */
    private fun finDeLlamada(
        elementos: List<ElementoHoja>,
        carrera: List<Int>,
        desde: Int,
        simplificada: Boolean,
    ): Int {
        var hasta = desde + 1
        while (hasta < carrera.size && hasta - desde < LlamadasNotas.MAX_NOTAS_POR_LLAMADA) {
            val texto = textoDe(elementos, carrera, desde, hasta + 1, simplificada)
            if (texto.split(' ', ',').count { it.isNotEmpty() } > LlamadasNotas.MAX_PALABRAS_POR_LLAMADA) return hasta
            hasta++
        }
        return hasta
    }

    /** Texto de las notas `carrera[desde until hasta]` unidas con « y »; la última lleva su cola ([cola]). */
    private fun textoDe(
        elementos: List<ElementoHoja>,
        carrera: List<Int>,
        desde: Int,
        hasta: Int,
        simplificada: Boolean,
    ): String =
        (desde until hasta).joinToString(" y ") { i ->
            val nota = elementos[carrera[i]] as ElementoHoja.DeNota
            if (i < hasta - 1) nota.frase(simplificada) else cola(elementos, carrera, i, simplificada)
        }

    /**
     * Frase de la última nota de una llamada: si la racha sigue tras ella (la llamada se cortó por el tope) o la nota
     * va unida a un aviso suelto, termina en « y»; si la siguiente nota de la hoja no se canta, la distancia es hasta
     * la siguiente que sí se canta (nada si no hay o si queda a menos de 10 m); en el resto, su frase.
     */
    private fun cola(
        elementos: List<ElementoHoja>,
        carrera: List<Int>,
        i: Int,
        simplificada: Boolean,
    ): String {
        val indice = carrera[i]
        val nota = elementos[indice] as ElementoHoja.DeNota
        val siguiente = elementos.getOrNull(indice + 1)
        return when {
            i < carrera.lastIndex || (nota.nota.unidaConSiguiente && siguiente is ElementoHoja.DeAviso) -> {
                nota.frase(simplificada) + " y"
            }

            siguiente is ElementoHoja.DeNota && !CandidatasNotas.seCanta(siguiente, simplificada) -> {
                val hacia = siguienteCantada(elementos, indice, simplificada)
                val distancia = hacia?.let { DISTANCIAS_NOTA_M.lastOrNull { d -> d <= it.posicionM - nota.nota.finM } }
                val sinCola = nota.nota.copy(distanciaSiguienteM = distancia, unidaConSiguiente = false)
                nota.copy(nota = sinCola).frase(simplificada)
            }

            else -> {
                nota.frase(simplificada)
            }
        }
    }

    /** La siguiente nota de la hoja tras [indice] que se canta (los avisos no cuentan), o `null`. */
    private fun siguienteCantada(
        elementos: List<ElementoHoja>,
        indice: Int,
        simplificada: Boolean,
    ): ElementoHoja.DeNota? =
        (indice + 1 until elementos.size)
            .asSequence()
            .map { elementos[it] }
            .filterIsInstance<ElementoHoja.DeNota>()
            .firstOrNull { CandidatasNotas.seCanta(it, simplificada) }

    /** Índice de [elemento] en [elementos] (ordenados por posición), o −1 si no está. */
    private fun indiceDe(
        elementos: List<ElementoHoja>,
        elemento: ElementoHoja,
    ): Int {
        var bajo = 0
        var alto = elementos.size
        while (bajo < alto) {
            val medio = (bajo + alto) ushr 1
            if (elementos[medio].posicionM < elemento.posicionM) bajo = medio + 1 else alto = medio
        }
        var i = bajo
        while (i < elementos.size && elementos[i].posicionM == elemento.posicionM && elementos[i] != elemento) i++
        return if (i < elementos.size && elementos[i] == elemento) i else -1
    }
}
