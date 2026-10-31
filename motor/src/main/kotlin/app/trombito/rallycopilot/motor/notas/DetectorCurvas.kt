package app.trombito.rallycopilot.motor.notas

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/** Sentido de giro de una curva. */
enum class Direccion { IZQUIERDA, DERECHA }

/**
 * Curva detectada. Posiciones en metros desde el inicio de la polilínea; [anguloGrados] es el cambio de rumbo
 * total (positivo). Los radios (m) son medianas: [radioCentralM] del 50 % central, [radioEntradaM] y
 * [radioSalidaM] del primer y del último tercio; [radioMinM] es el mínimo puntual (sobreestima la cerrazón).
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
 * Segmenta una traza en curvas con histéresis: una curva entra con R < [radioEntradaM] y sale con
 * R > [radioSalidaM] o al cambiar el signo de la curvatura; las del mismo sentido separadas menos de [fusionM]
 * metros se fusionan. Determinista y sin estado.
 */
class DetectorCurvas(
    private val radioEntradaM: Double = 250.0,
    private val radioSalidaM: Double = 400.0,
    private val fusionM: Double = 20.0,
) {
    init {
        require(radioEntradaM > 0.0 && radioSalidaM >= radioEntradaM) { "Umbrales de radio inválidos" }
        require(fusionM >= 0.0) { "Fusión inválida: $fusionM" }
    }

    /** Atajo: remuestrea [puntos] con los valores por defecto y detecta las curvas. */
    fun detectar(puntos: List<PuntoGeo>): List<Curva> = detectar(Remuestreo.remuestrear(puntos))

    /** Detecta las curvas de una traza ya remuestreada y suavizada. */
    fun detectar(traza: TrazaPlana): List<Curva> {
        val k = Curvatura.calcular(traza)
        return fusionar(segmentar(k), k, traza).map { construir(it, k, traza) }
    }

    private fun segmentar(k: DoubleArray): List<IntRange> {
        val entra = 1.0 / radioEntradaM
        val sale = 1.0 / radioSalidaM
        val rangos = mutableListOf<IntRange>()
        var inicio = -1
        var signo = 0.0
        for (i in k.indices) {
            if (inicio >= 0 && (abs(k[i]) < sale || Math.signum(k[i]) != signo)) {
                rangos += inicio until i
                inicio = -1
            }
            if (inicio < 0 && abs(k[i]) > entra) {
                inicio = i
                signo = Math.signum(k[i])
            }
        }
        if (inicio >= 0) rangos += inicio..k.lastIndex
        return rangos
    }

    private fun fusionar(
        rangos: List<IntRange>,
        k: DoubleArray,
        traza: TrazaPlana,
    ): List<IntRange> {
        val fusionados = mutableListOf<IntRange>()
        for (r in rangos) {
            val previo = fusionados.lastOrNull()
            if (previo != null && direccion(previo, k) == direccion(r, k) &&
                traza.s[r.first] - traza.s[previo.last] < fusionM
            ) {
                fusionados[fusionados.lastIndex] = previo.first..r.last
            } else {
                fusionados += r
            }
        }
        return fusionados
    }

    private fun direccion(
        r: IntRange,
        k: DoubleArray,
    ): Direccion = if (r.sumOf { k[it] } >= 0.0) Direccion.IZQUIERDA else Direccion.DERECHA

    private fun construir(
        r: IntRange,
        k: DoubleArray,
        traza: TrazaPlana,
    ): Curva {
        val radios = DoubleArray(r.last - r.first + 1) { 1.0 / abs(k[r.first + it]) }
        val n = radios.size
        return Curva(
            inicioM = traza.s[r.first],
            finM = traza.s[r.last],
            direccion = direccion(r, k),
            anguloGrados = angulo(r, traza),
            radioCentralM = mediana(radios, n / 4, n - n / 4),
            radioEntradaM = mediana(radios, 0, n / 3),
            radioSalidaM = mediana(radios, n - n / 3, n),
            radioMinM = radios.min(),
        )
    }

    /** Cambio de rumbo total entre las tangentes (diferencias centrales) de la primera y la última muestra. */
    private fun angulo(
        r: IntRange,
        traza: TrazaPlana,
    ): Double {
        val primera = r.first.coerceIn(1, traza.size - 2)
        val ultima = r.last.coerceIn(1, traza.size - 2)
        var total = 0.0
        var previo = rumbo(traza, primera)
        for (i in primera + 1..ultima) {
            val actual = rumbo(traza, i)
            var d = actual - previo
            if (d > PI) d -= 2 * PI
            if (d < -PI) d += 2 * PI
            total += d
            previo = actual
        }
        return Math.toDegrees(abs(total))
    }

    private fun rumbo(
        t: TrazaPlana,
        i: Int,
    ): Double = atan2(t.y[i + 1] - t.y[i - 1], t.x[i + 1] - t.x[i - 1])

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
}
