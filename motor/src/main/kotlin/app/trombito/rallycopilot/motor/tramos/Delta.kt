package app.trombito.rallycopilot.motor.tramos

import app.trombito.rallycopilot.motor.voz.Frase
import app.trombito.rallycopilot.motor.voz.TipoFrase
import kotlin.math.abs

/**
 * Parcial de un sector (o microsector) frente a la mejor pasada. [diferenciaMs] es lo que se ganó (negativo) o perdió
 * (positivo) en este sector; [deltaAcumuladoMs], la diferencia con la mejor acumulada hasta su final.
 */
data class Parcial(
    val numero: Int,
    val desdeM: Double,
    val hastaM: Double,
    val actualMs: Long,
    val mejorMs: Long,
    val deltaAcumuladoMs: Long,
) {
    val diferenciaMs: Long get() = actualMs - mejorMs
}

/**
 * Diferencias de tiempo contra la mejor pasada, interpoladas por distancia: delta(d) = t_actual(d) − t_mejor(d)
 * (negativo = vamos más rápido), y su locución en estilo copiloto.
 */
object Delta {
    /** Por debajo de esto (ms) el copiloto dice «igual». */
    const val IGUAL_MS = 50L

    private const val DECIMAS_MAX = 100L
    private const val MS_POR_DECIMA = 100.0
    private const val MS_POR_SEGUNDO = 1000.0
    private const val DECIMAS_POR_SEGUNDO = 10L

    private val UNIDADES =
        listOf(
            "cero",
            "uno",
            "dos",
            "tres",
            "cuatro",
            "cinco",
            "seis",
            "siete",
            "ocho",
            "nueve",
            "diez",
            "once",
            "doce",
            "trece",
            "catorce",
            "quince",
            "dieciséis",
            "diecisiete",
            "dieciocho",
            "diecinueve",
            "veinte",
            "veintiuno",
            "veintidós",
            "veintitrés",
            "veinticuatro",
            "veinticinco",
            "veintiséis",
            "veintisiete",
            "veintiocho",
            "veintinueve",
        )
    private val DECENAS =
        mapOf(
            3 to "treinta",
            4 to "cuarenta",
            5 to "cincuenta",
            6 to "sesenta",
            7 to "setenta",
            8 to "ochenta",
            9 to "noventa",
        )

    /** Delta (ms) en [distanciaM] entre [actual] y [mejor]; null si alguna no cubre esa distancia. */
    fun enDistancia(
        actual: Pasada,
        mejor: Pasada,
        distanciaM: Double,
    ): Long? {
        val a = actual.tiempoEn(distanciaM)
        val m = mejor.tiempoEn(distanciaM)
        return if (a == null || m == null) null else Math.round(a - m)
    }

    /** Delta en vivo: [tMs] transcurrido a [distanciaM] frente a lo que tardó la [mejor]; null si no la cubre. */
    fun enVivo(
        mejor: Pasada,
        distanciaM: Double,
        tMs: Long,
    ): Long? = mejor.tiempoEn(distanciaM)?.let { Math.round(tMs - it) }

    /**
     * Parciales de cada sector delimitado por los [cortes] (más el inicio y [longitudM]). Sectores que alguna de las
     * pasadas no cubre no se incluyen.
     */
    fun parciales(
        cortes: List<Double>,
        longitudM: Double,
        actual: Pasada,
        mejor: Pasada,
    ): List<Parcial> {
        val limites = listOf(0.0) + cortes + longitudM
        return limites.zipWithNext().mapIndexedNotNull { i, (desde, hasta) ->
            val t = listOf(actual.tiempoEn(desde), actual.tiempoEn(hasta), mejor.tiempoEn(desde), mejor.tiempoEn(hasta))
            val v = t.filterNotNull().takeIf { it.size == t.size } ?: return@mapIndexedNotNull null
            Parcial(i + 1, desde, hasta, Math.round(v[1] - v[0]), Math.round(v[3] - v[2]), Math.round(v[1] - v[3]))
        }
    }

    /**
     * Locución del delta: «más dos décimas», «menos uno con tres», «más cuatro segundos». Decimales hasta 10 s,
     * segundos enteros por encima; «igual» si |Δ| < [IGUAL_MS]. Negativo = más rápido = «menos».
     */
    fun texto(deltaMs: Long): String {
        val signo = if (deltaMs < 0) "menos" else "más"
        val decimas = Math.round(abs(deltaMs) / MS_POR_DECIMA)
        return if (abs(deltaMs) < IGUAL_MS) {
            "igual"
        } else if (decimas >= DECIMAS_MAX) {
            "$signo ${segundos(Math.round(abs(deltaMs) / MS_POR_SEGUNDO).toInt())}"
        } else {
            val enteros = (decimas / DECIMAS_POR_SEGUNDO).toInt()
            val resto = (decimas % DECIMAS_POR_SEGUNDO).toInt()
            val cuerpo =
                when {
                    enteros == 0 -> if (resto == 1) "una décima" else "${palabra(resto)} décimas"
                    resto == 0 -> segundos(enteros)
                    else -> "${palabra(enteros)} con ${palabra(resto)}"
                }
            "$signo $cuerpo"
        }
    }

    /** Frase CRONO al cerrar el sector [numero] de la pasada [idPasada]; sin posición (se dice al cruzar el corte). */
    fun fraseSector(
        tramoId: String,
        idPasada: String,
        numero: Int,
        deltaMs: Long,
    ) = Frase("crono:$tramoId:$idPasada:sector:$numero", texto(deltaMs), TipoFrase.CRONO, null)

    /** Frase CRONO al cruzar la meta de la pasada [idPasada]; sin posición. */
    fun fraseMeta(
        tramoId: String,
        idPasada: String,
        deltaMs: Long,
    ) = Frase("crono:$tramoId:$idPasada:meta", texto(deltaMs), TipoFrase.CRONO, null)

    private fun segundos(n: Int): String = if (n == 1) "un segundo" else "${apocopar(palabra(n))} segundos"

    private fun apocopar(texto: String): String =
        when {
            texto == "veintiuno" -> "veintiún"
            texto.endsWith("uno") -> texto.dropLast(1)
            else -> texto
        }.let { if (it == "uno") "un" else it }

    /** Número 0..99 en palabras; por encima, en cifras. */
    private fun palabra(n: Int): String =
        when {
            n < UNIDADES.size -> UNIDADES[n]
            n < 100 -> DECENAS.getValue(n / 10).let { if (n % 10 == 0) it else "$it y ${UNIDADES[n % 10]}" }
            else -> n.toString()
        }
}
