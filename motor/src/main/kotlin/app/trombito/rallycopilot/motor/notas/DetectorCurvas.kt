package app.trombito.rallycopilot.motor.notas

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/** El radio central es la mediana sin un cuarto a cada lado; la entrada y la salida, un tercio del núcleo. */
private const val CUARTOS = 4
private const val TERCIOS = 3

/** Sentido de giro de una curva. */
enum class Direccion { IZQUIERDA, DERECHA }

/**
 * Curva detectada. Posiciones en metros desde el inicio de la polilínea; [anguloGrados] es el cambio de rumbo
 * total (positivo). Los radios (m) son medianas: [radioCentralM] del 50 % central, [radioEntradaM] y
 * [radioSalidaM] del primer y del último tercio del núcleo (sin la cola abierta); [radioMinM] es el mínimo puntual
 * (sobreestima la cerrazón).
 */
data class Curva(
    val inicioM: Double,
    val finM: Double,
    val direccion: Direccion,
    val anguloGrados: Double,
    val radioCentralM: Double,
    val radioEntradaM: Double,
    val radioSalidaM: Double,
    val radioMinM: Double,
)

/**
 * Segmenta una traza en curvas por el rumbo, sin exigir un radio mínimo: una curva es un tramo continuo del mismo
 * sentido con un giro acumulado de al menos [anguloMinGrados], también si es muy abierta (R de 500–1000 m o más).
 *
 * 1. El rumbo (acumulado, de la traza suavizada) se parte en tramos monótonos con un zigzag: el sentido solo
 *    cambia si el rumbo da marcha atrás al menos [inversionGrados]; las oscilaciones menores (geometría OSM en
 *    zigzag, ruido del GPS) se absorben.
 * 2. Dentro de cada tramo, las muestras con una tasa de giro (grados por metro, en ±2 muestras) menor de
 *    [rectaGradosPorM] o de la veinteava parte de la mayor de su entorno de ±60 m son «casi rectas»; una racha de
 *    [rectaMuestras] o más parte el tramo en curvas distintas (una compuesta con una recta de unos 40 m o más entre
 *    medias son dos curvas) y recorta las rectas de los extremos.
 * 3. Se descartan las de menos de [anguloMinGrados] (ruido).
 *
 * El radio (medianas del circunradio local) no decide si hay curva, solo las medidas de [Curva]; [radioEntradaM] es el
 * radio por debajo del cual una muestra es del núcleo al comparar entrada y salida. Determinista y sin estado.
 */
class DetectorCurvas(
    private val radioEntradaM: Double = 400.0,
    private val inversionGrados: Double = 4.0,
    private val rectaGradosPorM: Double = 0.0125,
    private val rectaMuestras: Int = 3,
    private val anguloMinGrados: Double = 5.0,
) {
    init {
        require(radioEntradaM > 0.0) { "Radio de entrada inválido: $radioEntradaM" }
        require(inversionGrados > 0.0 && rectaGradosPorM >= 0.0 && rectaMuestras >= 1) { "Umbrales inválidos" }
    }

    /** Atajo: remuestrea [puntos] con los valores por defecto y detecta las curvas. */
    fun detectar(puntos: List<PuntoGeo>): List<Curva> = detectar(Remuestreo.remuestrear(puntos))

    /** Detecta las curvas de una traza ya remuestreada y suavizada. */
    fun detectar(traza: TrazaPlana): List<Curva> {
        if (traza.size < MUESTRAS_MIN) return emptyList()
        val k = Curvatura.calcular(traza)
        val rumbo = RumboDeTraza.acumulado(traza)
        return RumboDeTraza
            .tramosMonotonos(rumbo, inversionGrados)
            .flatMap { partirEnRectas(it, rumbo) }
            .map { construir(it, k, traza) }
            .filter { it.anguloGrados >= anguloMinGrados }
    }

    /** Rango de muestras de una curva y su giro en grados (con la ventana de rumbo incluida, dentro de su tramo). */
    private class Trozo(
        val desde: Int,
        val hasta: Int,
        val izquierda: Boolean,
        val giroGrados: Double,
    )

    /** Parte [tramo] en trozos activos separados por rachas de [rectaMuestras] o más muestras casi rectas. */
    private fun partirEnRectas(
        tramo: TramoMonotono,
        h: DoubleArray,
    ): List<Trozo> {
        val tasa = DoubleArray(h.size) { tasaDeGiro(it, tramo.signo, h) }
        val trozos = mutableListOf<Trozo>()
        var inicio = -1
        var ultimo = -1
        for (i in tramo.desde..tramo.hasta) {
            if (!casiRecta(i, tasa)) {
                if (inicio < 0) inicio = i
                ultimo = i
            } else if (inicio >= 0 && i - ultimo >= rectaMuestras) {
                trozos += trozo(inicio, ultimo, tramo, h)
                inicio = -1
            }
        }
        if (inicio >= 0) trozos += trozo(inicio, ultimo, tramo, h)
        return trozos
    }

    private fun trozo(
        desde: Int,
        hasta: Int,
        tramo: TramoMonotono,
        h: DoubleArray,
    ): Trozo {
        val giro = h[minOf(tramo.hasta, hasta + VENTANA_MUESTRAS)] - h[maxOf(tramo.desde, desde - VENTANA_MUESTRAS)]
        return Trozo(desde, hasta, tramo.signo > 0, abs(giro))
    }

    /** Grados por metro que avanza el rumbo en la muestra [i] en el sentido [signo], en ±[VENTANA_MUESTRAS]. */
    private fun tasaDeGiro(
        i: Int,
        signo: Int,
        h: DoubleArray,
    ): Double {
        val a = maxOf(0, i - VENTANA_MUESTRAS)
        val b = minOf(h.lastIndex, i + VENTANA_MUESTRAS)
        return (h[b] - h[a]) * signo / ((b - a) * Remuestreo.PASO_M)
    }

    /**
     * `true` si la tasa de giro de la muestra [i] es menor que [rectaGradosPorM] o que [RECTA_FRACCION_PICO] veces la
     * mayor de su entorno de ±[ENTORNO_MUESTRAS]: así un tramo recto entre dos curvas cerradas se nota aunque el
     * suavizado reparta su rumbo, y una curva abierta no se parte por tener un tramo algo más suave.
     */
    private fun casiRecta(
        i: Int,
        tasa: DoubleArray,
    ): Boolean {
        val entorno = maxOf(0, i - ENTORNO_MUESTRAS)..minOf(tasa.lastIndex, i + ENTORNO_MUESTRAS)
        val pico = entorno.maxOf { tasa[it] }
        return tasa[i] < maxOf(rectaGradosPorM, RECTA_FRACCION_PICO * pico)
    }

    private fun construir(
        t: Trozo,
        k: DoubleArray,
        traza: TrazaPlana,
    ): Curva {
        val radios = DoubleArray(t.hasta - t.desde + 1) { minOf(RADIO_MAX_M, 1.0 / abs(k[t.desde + it])) }
        val n = radios.size
        val nucleo = nucleo(radios)
        val m = nucleo.size
        return Curva(
            inicioM = traza.s[t.desde],
            finM = traza.s[t.hasta],
            direccion = if (t.izquierda) Direccion.IZQUIERDA else Direccion.DERECHA,
            anguloGrados = t.giroGrados,
            radioCentralM = mediana(radios, n / CUARTOS, n - n / CUARTOS),
            radioEntradaM = mediana(nucleo, 0, m / TERCIOS),
            radioSalidaM = mediana(nucleo, m - m / TERCIOS, m),
            radioMinM = radios.min(),
        )
    }

    /**
     * Recorta por ambos extremos las muestras con R > [radioEntradaM] (la cola abierta, que sesgaría la comparación
     * entrada/salida). Si no queda ninguna, devuelve [radios] entero.
     */
    private fun nucleo(radios: DoubleArray): DoubleArray {
        val primera = radios.indexOfFirst { it <= radioEntradaM }
        val ultima = radios.indexOfLast { it <= radioEntradaM }
        return if (primera < 0) radios else radios.copyOfRange(primera, ultima + 1)
    }

    /** Mediana de `radios[desde until hasta]`; si el tramo queda vacío usa la mediana de todo el rango. */
    private fun mediana(
        radios: DoubleArray,
        desde: Int,
        hasta: Int,
    ): Double {
        val orden = (if (hasta > desde) radios.copyOfRange(desde, hasta) else radios).sorted()
        val m = orden.size / 2
        return if (orden.size % 2 == 1) orden[m] else (orden[m - 1] + orden[m]) / 2.0
    }

    private companion object {
        /** Semiventana (muestras) para medir la tasa de giro de un punto y recuperar el giro de los bordes. */
        const val VENTANA_MUESTRAS = 2

        /** Semientorno (muestras) en el que se busca la tasa de giro máxima al decidir si un punto es casi recto. */
        const val ENTORNO_MUESTRAS = 12
        const val RECTA_FRACCION_PICO = 0.05
        const val MUESTRAS_MIN = 3

        /** Tope del radio de una muestra (con curvatura nula daría infinito). */
        const val RADIO_MAX_M = 10_000.0
    }
}

/** Tramo de rumbo monótono (índices de muestra, ambos incluidos) con su sentido: +1 izquierda, −1 derecha. */
private class TramoMonotono(
    val desde: Int,
    val hasta: Int,
    val signo: Int,
)

/** Rumbo de una traza plana y su partición en tramos monótonos. */
private object RumboDeTraza {
    /** Rumbo en grados de cada muestra (tangente por diferencias centrales), desenrollado para que sea continuo. */
    fun acumulado(t: TrazaPlana): DoubleArray {
        val h = DoubleArray(t.size)
        for (i in 1 until t.size - 1) h[i] = atan2(t.y[i + 1] - t.y[i - 1], t.x[i + 1] - t.x[i - 1])
        h[0] = h[1]
        h[t.size - 1] = h[t.size - 2]
        for (i in 1 until h.size) {
            var d = h[i] - h[i - 1]
            if (d > PI) d -= 2 * PI
            if (d < -PI) d += 2 * PI
            h[i] = h[i - 1] + d
        }
        return DoubleArray(h.size) { Math.toDegrees(h[it]) }
    }

    /** Zigzag del rumbo: los tramos entre extremos sucesivos separados al menos [inversionGrados]. */
    fun tramosMonotonos(
        h: DoubleArray,
        inversionGrados: Double,
    ): List<TramoMonotono> {
        val extremos = mutableListOf(0)
        var signo = 0
        for (i in 1 until h.size) {
            val desvio = h[i] - h[extremos.last()]
            when {
                signo != 0 && desvio * signo > 0 -> {
                    extremos[extremos.lastIndex] = i
                }

                abs(desvio) >= inversionGrados -> {
                    signo = if (desvio > 0) 1 else -1
                    extremos += i
                }
            }
        }
        return extremos.zipWithNext().map { (a, b) -> TramoMonotono(a, b, if (h[b] > h[a]) 1 else -1) }
    }
}
