package app.trombito.rallycopilot.motor.voz

import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.notas.NotaVisible

/**
 * Una [frase] de la hoja lista para cantar. [entradaM] es el inicio (m) de la última nota que canta: hasta llegar ahí
 * la siguiente llamada espera (ver [LlamadasNotas.de]). El que canta guarda el [entradaM] de la última llamada dicha.
 */
data class LlamadaNota(
    val frase: Frase,
    val entradaM: Double,
)

/**
 * Dónde está el coche y qué se cantó ya. [ultimaCantadaHastaM] es el [LlamadaNota.entradaM] de la última llamada
 * cantada (`null` si aún no se cantó ninguna); [margenEsperaM] es la distancia dentro de la cual una llamada ya no
 * espera a la entrada.
 */
data class EstadoLlamadas(
    val posicionM: Double,
    val simplificada: Boolean,
    val ultimaCantadaHastaM: Double? = null,
    val margenEsperaM: Double = LlamadasNotas.MARGEN_ESPERA_M,
)

/**
 * Llamadas del copiloto: las curvas encadenadas («izquierda tres y derecha cuatro») se cantan en una sola frase y la
 * siguiente no se canta hasta entrar en la última curva de la anterior. Lógica pura: el estado (la entrada de la
 * última llamada cantada) lo pasa quien llama.
 */
object LlamadasNotas {
    /** Notas que caben en una llamada; una cadena más larga se parte en llamadas de este tamaño. */
    const val MAX_NOTAS_POR_LLAMADA = 3

    /** Por defecto, dentro de esta distancia (m) una llamada ya no espera (el doble de la anticipación mínima). */
    const val MARGEN_ESPERA_M = 80.0

    /**
     * Llamadas que ya se pueden cantar entre las [visibles] de [hoja] según el [estado].
     *
     * - Cadena: notas consecutivas de la hoja con [app.trombito.rallycopilot.motor.notas.Nota.unidaConSiguiente]
     *   forman una llamada de como mucho [MAX_NOTAS_POR_LLAMADA] notas; una cadena más larga se parte desde su
     *   primera nota (la 4.ª abre la siguiente llamada), también si la ventana de [visibles] empieza a mitad de
     *   cadena. Un aviso suelto en medio corta la cadena. El texto une las frases de cada nota con « y » (con sus
     *   avisos unidos y la distancia final). Con [EstadoLlamadas.simplificada] las notas que el modo simplificado
     *   quita no entran (ver [CandidatasNotas.seCantaSimplificada]); si queda una sola, es una nota normal.
     * - Id de la llamada = [idDe] de su primera nota; [Frase.cubre] = las de todas (vacío si es una sola).
     * - Se canta cuando su primera nota está activa.
     * - Espera: si hay [EstadoLlamadas.ultimaCantadaHastaM] y la posición aún no ha llegado, una llamada no sale
     *   mientras su primera nota quede a más de [EstadoLlamadas.margenEsperaM] m. Un aviso suelto no espera nunca.
     */
    fun de(
        hoja: HojaNotas,
        visibles: List<NotaVisible>,
        estado: EstadoLlamadas,
        idDe: (ElementoHoja) -> String = { "nota-${it.posicionM}" },
    ): List<LlamadaNota> =
        visibles
            .filter { it.activa && !espera(it.elemento, estado) }
            .mapNotNull { llamadaDe(hoja.elementos, it.elemento, estado.simplificada, idDe) }

    /** `true` si [elemento] es una nota que aún no puede cantarse porque no se ha entrado en la curva anterior. */
    private fun espera(
        elemento: ElementoHoja,
        estado: EstadoLlamadas,
    ): Boolean {
        val hasta = estado.ultimaCantadaHastaM
        return elemento is ElementoHoja.DeNota &&
            hasta != null &&
            estado.posicionM < hasta &&
            elemento.posicionM - estado.posicionM > estado.margenEsperaM
    }

    private fun llamadaDe(
        elementos: List<ElementoHoja>,
        elemento: ElementoHoja,
        simplificada: Boolean,
        idDe: (ElementoHoja) -> String,
    ): LlamadaNota? {
        val grupo =
            if (simplificada && !CandidatasNotas.seCantaSimplificada(elemento)) {
                null
            } else {
                when (elemento) {
                    is ElementoHoja.DeAviso -> listOf(elemento)
                    is ElementoHoja.DeNota -> grupoDe(elementos, elemento, simplificada)
                }
            }
        return grupo?.let {
            val ids = it.map(idDe)
            val frase =
                Frase(
                    id = ids.first(),
                    texto = it.joinToString(" y ") { e -> textoDe(e, simplificada) },
                    tipo = TipoFrase.NOTA,
                    puntoM = elemento.posicionM,
                    cubre = if (it.size > 1) ids else emptyList(),
                )
            LlamadaNota(frase, it.last().posicionM)
        }
    }

    private fun textoDe(
        elemento: ElementoHoja,
        simplificada: Boolean,
    ): String =
        when (elemento) {
            is ElementoHoja.DeNota -> elemento.frase(simplificada)
            is ElementoHoja.DeAviso -> elemento.frase
        }

    /**
     * Notas de la llamada que abre [nota], o `null` si [nota] no abre ninguna (es la 2.ª o 3.ª de su llamada o la
     * quita el modo simplificado).
     */
    private fun grupoDe(
        elementos: List<ElementoHoja>,
        nota: ElementoHoja.DeNota,
        simplificada: Boolean,
    ): List<ElementoHoja>? {
        val indice = indiceDe(elementos, nota)
        if (indice < 0) return listOf(nota)
        val cadena = cadenaDesde(elementos, inicioDeCadena(elementos, indice), simplificada)
        val orden = cadena.indexOf(nota)
        return if (orden < 0 || orden % MAX_NOTAS_POR_LLAMADA != 0) {
            null
        } else {
            cadena.subList(orden, minOf(cadena.size, orden + MAX_NOTAS_POR_LLAMADA)).toList()
        }
    }

    /** Índice de la primera nota de la cadena a la que pertenece la de [indice]. */
    private fun inicioDeCadena(
        elementos: List<ElementoHoja>,
        indice: Int,
    ): Int {
        var inicio = indice
        while (inicio > 0 && uneConSiguiente(elementos[inicio - 1])) inicio--
        return inicio
    }

    /** Notas de la cadena que empieza en [inicio], sin las que quita el modo simplificado. */
    private fun cadenaDesde(
        elementos: List<ElementoHoja>,
        inicio: Int,
        simplificada: Boolean,
    ): List<ElementoHoja.DeNota> {
        val cadena = ArrayList<ElementoHoja.DeNota>()
        var k = inicio
        while (k < elementos.size && elementos[k] is ElementoHoja.DeNota) {
            val actual = elementos[k] as ElementoHoja.DeNota
            if (!simplificada || CandidatasNotas.seCantaSimplificada(actual)) cadena.add(actual)
            k = if (actual.nota.unidaConSiguiente) k + 1 else elementos.size
        }
        return cadena
    }

    private fun uneConSiguiente(elemento: ElementoHoja): Boolean =
        elemento is ElementoHoja.DeNota && elemento.nota.unidaConSiguiente

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
        while (i < elementos.size && elementos[i].posicionM == elemento.posicionM) {
            if (elementos[i] == elemento) return i
            i++
        }
        return -1
    }
}
