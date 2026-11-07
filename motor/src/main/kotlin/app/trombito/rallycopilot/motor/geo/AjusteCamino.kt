package app.trombito.rallycopilot.motor.geo

import app.trombito.rallycopilot.motor.velocidad.Fijacion
import kotlin.math.hypot

/** Sentido en que se recorre un camino respecto al orden de sus puntos. */
enum class SentidoCamino {
    /** Solo en el orden de los puntos (una ruta, una vía de sentido único dibujada a favor). */
    ADELANTE,

    /** Solo contra el orden de los puntos (vía de sentido único dibujada al revés). */
    ATRAS,

    /** Vía de doble sentido: vale el más cercano al rumbo (o al rumbo previo si no hay). */
    AMBOS,
}

/**
 * Posición a mostrar. [enCamino] es false cuando no se pudo ajustar: [punto] y [rumboGrados] son entonces los de la
 * fijación (salvo parado, que conserva el rumbo previo). [rumboGrados] puede ser null si no se conoce ninguno.
 */
data class PosicionAjustada(
    val punto: PuntoGeo,
    val rumboGrados: Double?,
    val enCamino: Boolean,
)

/**
 * Polilínea preparada para el ajuste: proyección plana local (equirectangular a la latitud media, igual que
 * `SeguidorRuta`) y metros acumulados de cada vértice. Se crea una vez por camino y se reutiliza fijación a fijación.
 */
class CaminoPlano(
    private val puntos: List<PuntoGeo>,
) {
    private val plano = ProyeccionPlana.deTraza(puntos)
    private val xs = DoubleArray(puntos.size) { plano.x(puntos[it]) }
    private val ys = DoubleArray(puntos.size) { plano.y(puntos[it]) }
    private val acumulada = DoubleArray(puntos.size)

    init {
        require(puntos.size >= 2) { "Un camino necesita al menos dos puntos" }
        for (i in 1 until puntos.size) acumulada[i] = acumulada[i - 1] + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
    }

    /** Longitud en metros (plano local). */
    val longitudM: Double get() = acumulada.last()

    /** Proyección sobre el camino: segmento [indice], fracción [t] (0..1), metros desde el inicio y distancia. */
    internal class Proyeccion(
        val indice: Int,
        val t: Double,
        val progresoM: Double,
        val distanciaM: Double,
    )

    /** Punto del camino más cercano a [punto] cuyo progreso está en [ventanaM]; null si ningún tramo lo está. */
    internal fun masCercano(
        punto: PuntoGeo,
        ventanaM: ClosedFloatingPointRange<Double>,
    ): Proyeccion? {
        val x = plano.x(punto)
        val y = plano.y(punto)
        var mejor: Proyeccion? = null
        for (i in 0 until acumulada.lastIndex) {
            val largo = acumulada[i + 1] - acumulada[i]
            if (largo > 0.0 && acumulada[i + 1] >= ventanaM.start && acumulada[i] <= ventanaM.endInclusive) {
                val dx = xs[i + 1] - xs[i]
                val dy = ys[i + 1] - ys[i]
                val tLibre = ((x - xs[i]) * dx + (y - ys[i]) * dy) / (largo * largo)
                val tMin = ((ventanaM.start - acumulada[i]) / largo).coerceIn(0.0, 1.0)
                val tMax = ((ventanaM.endInclusive - acumulada[i]) / largo).coerceIn(0.0, 1.0)
                val t = tLibre.coerceIn(tMin, tMax)
                val distancia = hypot(x - (xs[i] + t * dx), y - (ys[i] + t * dy))
                if (mejor == null || distancia < mejor.distanciaM) {
                    mejor = Proyeccion(i, t, acumulada[i] + t * largo, distancia)
                }
            }
        }
        return mejor
    }

    internal fun puntoEn(p: Proyeccion): PuntoGeo {
        val a = puntos[p.indice]
        val b = puntos[p.indice + 1]
        return PuntoGeo(a.lat + p.t * (b.lat - a.lat), a.lon + p.t * (b.lon - a.lon))
    }

    /**
     * Rumbo (grados desde el norte, sentido de los puntos) en [progresoM]: el de la cuerda entre [semiventanaM] metros
     * antes y después, para que no salte en los vértices. En los extremos la cuerda se acorta; si queda casi en un
     * punto se usa el rumbo del segmento.
     */
    internal fun rumboEn(
        p: Proyeccion,
        semiventanaM: Double,
    ): Double {
        val desde = (p.progresoM - semiventanaM).coerceAtLeast(0.0)
        val hasta = (p.progresoM + semiventanaM).coerceAtMost(longitudM)
        val (x0, y0) = xyEn(desde)
        val (x1, y1) = xyEn(hasta)
        val cuerdaUtil = hypot(x1 - x0, y1 - y0) >= CUERDA_MIN_M
        val dx = if (cuerdaUtil) x1 - x0 else xs[p.indice + 1] - xs[p.indice]
        val dy = if (cuerdaUtil) y1 - y0 else ys[p.indice + 1] - ys[p.indice]
        return Rumbo.deVector(dx, dy)
    }

    private fun xyEn(progresoM: Double): Pair<Double, Double> {
        var i = 0
        while (i < acumulada.size - 2 && acumulada[i + 1] < progresoM) i++
        val largo = acumulada[i + 1] - acumulada[i]
        val t = if (largo > 0.0) ((progresoM - acumulada[i]) / largo).coerceIn(0.0, 1.0) else 0.0
        return (xs[i] + t * (xs[i + 1] - xs[i])) to (ys[i] + t * (ys[i + 1] - ys[i]))
    }

    private companion object {
        const val CUERDA_MIN_M = 0.5
    }
}

/**
 * Ajusta una fijación al camino por el que se circula, para que el marcador y el giro del mapa (rumbo arriba) sigan
 * el camino y no el rumbo bruto del GPS, que a poca velocidad es ruido y en el móvil (fused) mezcla sensores mal
 * calibrados. Función pura; el rumbo previo lo guarda quien llama.
 *
 * Diseño:
 * - El camino es la ruta activa (sentido [SentidoCamino.ADELANTE]) o, en libre, la geometría de la vía actual.
 * - Se proyecta el punto sobre el camino (opcionalmente restringido a una ventana de progreso, para rutas que pasan
 *   dos veces por el mismo sitio). Se usa el camino si la fijación está a [RADIO_MAX_M] (25 m) o menos y, cuando hay
 *   rumbo bruto fiable, difiere menos de [UMBRAL_RUMBO_GRADOS] (60) de la tangente. Si no (salida de la vía, cambio
 *   de sentido, rumbo contrario), se devuelve la fijación tal cual con `enCamino = false`.
 * - Rumbo = tangente del camino en la proyección: cuerda de ±[SEMIVENTANA_M] (5 m, 10 m en total) para que no salte
 *   en los vértices. En doble sentido se toma el sentido más cercano al rumbo bruto, o al previo si no lo hay.
 * - Parado (velocidad < [VELOCIDAD_PARADO_MS], 1,5 m/s) el rumbo bruto es ruido: ni cuenta para validar ni cambia la
 *   salida; se mantiene el rumbo previo (con la posición ya proyectada). Velocidad desconocida cuenta como en marcha.
 */
object AjusteCamino {
    const val RADIO_MAX_M = 25.0
    const val UMBRAL_RUMBO_GRADOS = 60.0
    const val VELOCIDAD_PARADO_MS = 1.5
    const val SEMIVENTANA_M = 5.0

    private const val GRADOS_LLANO = 180.0

    /** Ventana de progreso sin restricción. */
    val SIN_VENTANA: ClosedFloatingPointRange<Double> = 0.0..Double.POSITIVE_INFINITY

    /**
     * @param rumboPrevio último rumbo mostrado (null si ninguno).
     * @param ventanaM tramo del camino (metros desde el inicio) donde buscar; por defecto todo.
     */
    fun ajustar(
        camino: CaminoPlano,
        sentido: SentidoCamino,
        fijacion: Fijacion,
        rumboPrevio: Double?,
        ventanaM: ClosedFloatingPointRange<Double> = SIN_VENTANA,
    ): PosicionAjustada {
        val parado = (fijacion.velocidadMs ?: Double.POSITIVE_INFINITY) < VELOCIDAD_PARADO_MS
        val bruto = fijacion.rumboGrados?.takeIf { it.isFinite() }?.let(Rumbo::normalizar)
        val fiable = bruto.takeUnless { parado }
        val cerca = camino.masCercano(fijacion.punto, ventanaM)?.takeIf { it.distanciaM <= RADIO_MAX_M }
        val tangente = cerca?.let { elegirSentido(camino.rumboEn(it, SEMIVENTANA_M), sentido, fiable ?: rumboPrevio) }
        val sirve = tangente != null && (fiable == null || Rumbo.diferencia(fiable, tangente) < UMBRAL_RUMBO_GRADOS)
        return if (sirve && cerca != null && tangente != null) {
            PosicionAjustada(camino.puntoEn(cerca), if (parado) rumboPrevio ?: tangente else tangente, true)
        } else {
            PosicionAjustada(fijacion.punto, if (parado) rumboPrevio ?: bruto else bruto, false)
        }
    }

    private fun elegirSentido(
        adelante: Double,
        sentido: SentidoCamino,
        referencia: Double?,
    ): Double {
        val atras = Rumbo.normalizar(adelante + GRADOS_LLANO)
        val prefiereAtras =
            when (sentido) {
                SentidoCamino.ADELANTE -> false
                SentidoCamino.ATRAS -> true
                SentidoCamino.AMBOS -> referencia != null && masCerca(referencia, atras, adelante)
            }
        return if (prefiereAtras) atras else adelante
    }

    /** El rumbo [a] está más cerca de [referencia] que [b]. */
    private fun masCerca(
        referencia: Double,
        a: Double,
        b: Double,
    ): Boolean = Rumbo.diferencia(referencia, a) < Rumbo.diferencia(referencia, b)
}
