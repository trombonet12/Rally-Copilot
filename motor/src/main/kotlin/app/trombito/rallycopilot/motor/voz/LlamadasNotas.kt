package app.trombito.rallycopilot.motor.voz

import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.notas.NivelAnticipacion
import app.trombito.rallycopilot.motor.notas.NotaVisible
import kotlin.math.max

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
 * cantada (`null` si aún no se cantó ninguna). [vMs] y [nivel] (la velocidad y el nivel de anticipación en uso)
 * dan el [margenEsperaM]: la distancia dentro de la cual una llamada ya no espera a la entrada.
 */
data class EstadoLlamadas(
    val posicionM: Double,
    val simplificada: Boolean,
    val ultimaCantadaHastaM: Double? = null,
    val vMs: Double = 0.0,
    val nivel: NivelAnticipacion = NivelAnticipacion.NORMAL,
) {
    /** `max(80 m, v · T_nivel)`: a más velocidad, antes se canta la siguiente llamada. */
    val margenEsperaM: Double get() = max(LlamadasNotas.MARGEN_ESPERA_M, vMs.coerceAtLeast(0.0) * nivel.segundos)
}

/**
 * Llamadas del copiloto: las curvas encadenadas («izquierda tres y derecha cuatro») se cantan en una sola frase y la
 * siguiente no se canta hasta entrar en la última curva de la anterior. Lógica pura: el estado (la entrada de la
 * última llamada cantada) lo pasa quien llama.
 */
object LlamadasNotas {
    /** Notas que caben en una llamada; una cadena más larga se parte en llamadas de este tamaño. */
    const val MAX_NOTAS_POR_LLAMADA = 3

    /** Palabras que caben en una llamada (aprox.); si se pasa, se corta antes aunque no se llegue a 3 notas. */
    const val MAX_PALABRAS_POR_LLAMADA = 12

    /** Margen mínimo (m): dentro de esta distancia una llamada ya no espera (el doble de la anticipación mínima). */
    const val MARGEN_ESPERA_M = 80.0

    /**
     * Llamadas que ya se pueden cantar entre las [visibles] de [hoja] según el [estado].
     *
     * - Cadena: notas consecutivas de la hoja con [app.trombito.rallycopilot.motor.notas.Nota.unidaConSiguiente]
     *   forman una llamada de como mucho [MAX_NOTAS_POR_LLAMADA] notas y [MAX_PALABRAS_POR_LLAMADA] palabras; una
     *   cadena más larga se parte desde su primera nota (la 4.ª abre la siguiente llamada), también si la ventana de
     *   [visibles] empieza a mitad de cadena. Una llamada cortada con la siguiente nota aún unida termina en « y».
     *   Un aviso suelto en medio corta la cadena. El texto une las frases de cada nota con « y » (con sus avisos
     *   unidos y la distancia final).
     * - Con [EstadoLlamadas.simplificada] (y siempre con la casi recta, ver [CandidatasNotas.esCasiRecta]) las notas
     *   que no se cantan cortan la cadena: la nota cantada anterior da la distancia hasta la siguiente nota que sí se
     *   canta (nunca una «y» por encima de una curva callada ni la distancia hasta ella). Si queda una sola, es una
     *   nota normal.
     * - Id de la llamada = [idDe] de su primera nota; [Frase.cubre] = las de todas (vacío si es una sola).
     * - Se canta cuando su primera nota está activa.
     * - Espera: si hay [EstadoLlamadas.ultimaCantadaHastaM] y la posición aún no ha llegado, una llamada no sale
     *   mientras su primera nota quede a más de [EstadoLlamadas.margenEsperaM] m. Un aviso suelto no espera, pero
     *   tampoco se canta antes que una nota anterior que sí espera: se para en la primera visible que espera.
     */
    fun de(
        hoja: HojaNotas,
        visibles: List<NotaVisible>,
        estado: EstadoLlamadas,
        idDe: (ElementoHoja) -> String = { "nota-${it.posicionM}" },
    ): List<LlamadaNota> {
        val activas = visibles.filter { it.activa }.map { it.elemento }
        val bloqueo = activas.indexOfFirst { espera(it, estado) && bloquea(hoja, it, estado) }
        return (if (bloqueo < 0) activas else activas.take(bloqueo))
            .filterNot { espera(it, estado) }
            .mapNotNull { elemento ->
                llamadaDe(hoja.elementos, elemento, estado.simplificada)?.let { llamada(it, elemento, idDe) }
            }
    }

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

    /** La nota que espera bloquea lo que viene detrás si abre una llamada nueva (no una ya cantada). */
    private fun bloquea(
        hoja: HojaNotas,
        elemento: ElementoHoja,
        estado: EstadoLlamadas,
    ): Boolean =
        elemento.posicionM > (estado.ultimaCantadaHastaM ?: Double.MAX_VALUE) &&
            llamadaDe(hoja.elementos, elemento, estado.simplificada) != null

    private fun llamada(
        grupo: GrupoLlamada,
        primero: ElementoHoja,
        idDe: (ElementoHoja) -> String,
    ): LlamadaNota {
        val ids = grupo.elementos.map(idDe)
        val frase =
            Frase(
                id = ids.first(),
                texto = grupo.texto,
                tipo = TipoFrase.NOTA,
                puntoM = primero.posicionM,
                cubre = if (ids.size > 1) ids else emptyList(),
            )
        return LlamadaNota(frase, grupo.elementos.last().posicionM)
    }

    /** El grupo que abre [elemento], o `null` si no abre ninguno (es de otra llamada o no se canta). */
    private fun llamadaDe(
        elementos: List<ElementoHoja>,
        elemento: ElementoHoja,
        simplificada: Boolean,
    ): GrupoLlamada? =
        when {
            !CandidatasNotas.seCanta(elemento, simplificada) -> null
            elemento is ElementoHoja.DeAviso -> GrupoLlamada(listOf(elemento), elemento.frase)
            elemento is ElementoHoja.DeNota -> CadenasNotas.grupoDe(elementos, elemento, simplificada)
            else -> null
        }
}
