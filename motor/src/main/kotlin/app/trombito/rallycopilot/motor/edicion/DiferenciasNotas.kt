package app.trombito.rallycopilot.motor.edicion

import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.HojaNotas
import kotlin.math.abs

/** Un cambio entre dos hojas de notas, situado en [posicionM] (metros sobre el tramo). */
sealed interface CambioNota {
    val posicionM: Double

    /** La nota [antes] se ha cantado de otra forma: ahora es [despues] (frases tal como las lee el copiloto). */
    data class Cambiada(
        override val posicionM: Double,
        val antes: String,
        val despues: String,
    ) : CambioNota

    /** Nota o aviso nuevo, [despues]. */
    data class Anadida(
        override val posicionM: Double,
        val despues: String,
    ) : CambioNota

    /** Nota o aviso que ya no está, [antes]. */
    data class Quitada(
        override val posicionM: Double,
        val antes: String,
    ) : CambioNota
}

/**
 * Qué ha cambiado entre dos hojas de notas del mismo tramo (la de antes y la de después de una pasada de
 * reconocimiento). Empareja cada elemento con el más cercano del mismo tipo (nota con nota, aviso con aviso) a
 * [TOLERANCIA_M] o menos, sin reutilizar ninguno; lo emparejado con otra frase es un [CambioNota.Cambiada], lo que
 * sobra en la nueva es [CambioNota.Anadida] y lo que falta, [CambioNota.Quitada].
 */
object DiferenciasNotas {
    /** Distancia máxima (m) entre un elemento de la hoja de antes y su pareja en la de después. */
    const val TOLERANCIA_M = 25.0

    /** Los cambios de [antes] a [despues], por posición. Hojas iguales dan una lista vacía. */
    fun entre(
        antes: HojaNotas,
        despues: HojaNotas,
        toleranciaM: Double = TOLERANCIA_M,
    ): List<CambioNota> {
        val a = antes.elementos
        val d = despues.elementos
        val parejaDeA = emparejar(a, d, toleranciaM)
        val usadosD = parejaDeA.filterNotNull().toSet()
        val cambios = mutableListOf<CambioNota>()
        a.forEachIndexed { i, viejo ->
            val j = parejaDeA[i]
            if (j == null) {
                cambios += CambioNota.Quitada(viejo.posicionM, viejo.frase)
            } else if (d[j].frase != viejo.frase) {
                cambios += CambioNota.Cambiada(d[j].posicionM, viejo.frase, d[j].frase)
            }
        }
        d.forEachIndexed { j, nuevo ->
            if (j !in usadosD) cambios += CambioNota.Anadida(nuevo.posicionM, nuevo.frase)
        }
        return cambios.sortedBy { it.posicionM }
    }

    /** Para cada elemento de [a], el índice de su pareja en [d] (o null): primero las parejas más cercanas. */
    private fun emparejar(
        a: List<ElementoHoja>,
        d: List<ElementoHoja>,
        toleranciaM: Double,
    ): List<Int?> {
        val candidatas = mutableListOf<Triple<Double, Int, Int>>()
        a.forEachIndexed { i, viejo ->
            d.forEachIndexed { j, nuevo ->
                val distancia = abs(viejo.posicionM - nuevo.posicionM)
                if (mismoTipo(viejo, nuevo) && distancia <= toleranciaM) candidatas += Triple(distancia, i, j)
            }
        }
        val pareja = arrayOfNulls<Int>(a.size)
        val ocupadas = mutableSetOf<Int>()
        candidatas
            .sortedWith(compareBy({ it.first }, { it.second }, { it.third }))
            .forEach { (_, i, j) ->
                if (pareja[i] == null && j !in ocupadas) {
                    pareja[i] = j
                    ocupadas += j
                }
            }
        return pareja.toList()
    }

    private fun mismoTipo(
        a: ElementoHoja,
        b: ElementoHoja,
    ) = (a is ElementoHoja.DeNota) == (b is ElementoHoja.DeNota)
}
