package app.trombito.rallycopilot.ui.analisis

import app.trombito.rallycopilot.motor.analisis.PuntoSerie
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Escala de un gráfico de serie sobre distancia: el eje X va de 0 a [distanciaMaxM] y el Y de [minimo] (abajo) a
 * [maximo] (arriba). Las fracciones van de 0 a 1 dentro del área de dibujo (Y de arriba a abajo, como el lienzo).
 */
data class EscalaGrafico(
    val distanciaMaxM: Double,
    val minimo: Double,
    val maximo: Double,
) {
    /** Posición horizontal (0 a 1) de [distanciaM]; fuera del tramo se acota. */
    fun fraccionX(distanciaM: Double): Float =
        if (distanciaMaxM <= 0.0) 0f else (distanciaM / distanciaMaxM).coerceIn(0.0, 1.0).toFloat()

    /** Posición vertical (0 arriba, 1 abajo) de [valor]; fuera de la escala se acota. */
    fun fraccionY(valor: Double): Float =
        if (maximo <= minimo) CENTRO else (1.0 - (valor - minimo) / (maximo - minimo)).coerceIn(0.0, 1.0).toFloat()

    /** Distancia (m) bajo la fracción horizontal [fraccion] (acotada a 0..1). */
    fun distanciaEn(fraccion: Float): Double = fraccion.coerceIn(0f, 1f) * distanciaMaxM

    private companion object {
        const val CENTRO = 0.5f
    }
}

/** Velocidad mínima visible del gráfico de velocidad: evita escalas absurdas con trazas casi paradas. */
private const val VELOCIDAD_MINIMA_ESCALA_KMH = 40.0
private const val PASO_VELOCIDAD_KMH = 20.0
private const val DELTA_MINIMO_ESCALA_MS = 1_000.0
private const val OBJETIVO_MARCAS = 4
private const val EPSILON = 1e-9

/** Escala de la velocidad: de 0 al máximo de todas las [series] redondeado hacia arriba a múltiplos de 20 km/h. */
fun escalaVelocidad(
    series: List<List<PuntoSerie>>,
    distanciaMaxM: Double,
): EscalaGrafico {
    val maxima = series.flatten().maxOfOrNull { it.valor } ?: 0.0
    val techo = ceil(maxima / PASO_VELOCIDAD_KMH) * PASO_VELOCIDAD_KMH
    return EscalaGrafico(distanciaMaxM, 0.0, maxOf(techo, VELOCIDAD_MINIMA_ESCALA_KMH))
}

/**
 * Escala del delta (ms): simétrica alrededor del cero, con un techo "redondo" (1, 2, 5 × 10^n segundos) que abarca la
 * mayor diferencia, para que el cero quede siempre en el centro.
 */
fun escalaDelta(
    delta: List<PuntoSerie>,
    distanciaMaxM: Double,
): EscalaGrafico {
    val mayor = delta.maxOfOrNull { abs(it.valor) } ?: 0.0
    val techo = techoRedondo(maxOf(mayor, DELTA_MINIMO_ESCALA_MS))
    return EscalaGrafico(distanciaMaxM, -techo, techo)
}

/** El menor 1, 2, 5 o 10 × 10^n que es al menos [valor]. */
internal fun techoRedondo(valor: Double): Double {
    if (valor <= 0.0) return 1.0
    val potencia = 10.0.pow(floor(log10(valor)))
    return listOf(1.0, 2.0, 5.0, 10.0).map { it * potencia }.first { it >= valor - EPSILON }
}

/** Valor de la serie en [distanciaM], interpolado entre muestras y acotado a los extremos; null si está vacía. */
fun valorEn(
    serie: List<PuntoSerie>,
    distanciaM: Double,
): Double? {
    if (serie.isEmpty()) return null
    val i = serie.indexOfFirst { it.distanciaM >= distanciaM }
    return when {
        i < 0 -> {
            serie.last().valor
        }

        i == 0 -> {
            serie.first().valor
        }

        else -> {
            val a = serie[i - 1]
            val b = serie[i]
            val ancho = b.distanciaM - a.distanciaM
            if (ancho <= 0.0) b.valor else a.valor + (distanciaM - a.distanciaM) / ancho * (b.valor - a.valor)
        }
    }
}

/**
 * Marcas "redondas" (1, 2 o 5 × 10^n) entre [minimo] y [maximo], unas [objetivo]; siempre ascendentes. Sirve para los
 * ejes de distancia (km), velocidad (km/h) y delta (s).
 */
fun marcasEje(
    minimo: Double,
    maximo: Double,
    objetivo: Int = OBJETIVO_MARCAS,
): List<Double> {
    if (maximo <= minimo || objetivo < 1) return emptyList()
    val paso = techoRedondo((maximo - minimo) / objetivo)
    val primera = ceil(minimo / paso - EPSILON)
    val ultima = floor(maximo / paso + EPSILON)
    return (primera.toLong()..ultima.toLong()).map { it * paso }
}

/**
 * Distancia (m) bajo el toque en [x] px de un área que empieza en [izquierda] px y mide [ancho] px, sobre la
 * [escala]; el toque fuera del área se acota a los extremos.
 */
fun distanciaDeToque(
    x: Float,
    izquierda: Float,
    ancho: Float,
    escala: EscalaGrafico,
): Double = if (ancho <= 0f) 0.0 else escala.distanciaEn((x - izquierda) / ancho)
