package app.trombito.rallycopilot.motor.geo

import kotlin.math.cos
import kotlin.math.hypot

/**
 * Proyección plana local (equirectangular): metros este ([x]) y norte ([y]) respecto a [origen], con la escala
 * horizontal fijada a la latitud [latReferencia]. Válida para trazas de unas decenas de km que no cruzan el
 * antimeridiano. Es la única forma de pasar de grados a metros planos en `:motor`.
 */
class ProyeccionPlana(
    val origen: PuntoGeo,
    latReferencia: Double = origen.lat,
) {
    private val escalaX = PuntoGeo.RADIO_TIERRA_M * cos(Math.toRadians(latReferencia))

    /** Metros hacia el este de [p] respecto al origen. */
    fun x(p: PuntoGeo): Double = xDeLon(p.lon)

    /** Metros hacia el norte de [p] respecto al origen. */
    fun y(p: PuntoGeo): Double = yDeLat(p.lat)

    /** Metros hacia el este de la longitud [lon] (grados) respecto al origen. */
    fun xDeLon(lon: Double): Double = Math.toRadians(lon - origen.lon) * escalaX

    /** Metros hacia el norte de la latitud [lat] (grados) respecto al origen. */
    fun yDeLat(lat: Double): Double = Math.toRadians(lat - origen.lat) * PuntoGeo.RADIO_TIERRA_M

    /** Punto geográfico de las coordenadas planas ([x], [y]) (inversa de [x] e [y]); sin altitud. */
    fun aPunto(
        x: Double,
        y: Double,
    ): PuntoGeo =
        PuntoGeo(
            (origen.lat + Math.toDegrees(y / PuntoGeo.RADIO_TIERRA_M)).coerceIn(-LAT_MAX, LAT_MAX),
            (origen.lon + Math.toDegrees(x / escalaX)).coerceIn(-LON_MAX, LON_MAX),
        )

    /** Distancia plana (m) entre dos puntos. */
    fun distancia(
        a: PuntoGeo,
        b: PuntoGeo,
    ): Double = hypot(x(b) - x(a), y(b) - y(a))

    /**
     * Distancia (m) de [punto] a la polilínea [linea] (al punto si solo tiene uno; infinita si está vacía), en el
     * plano de esta proyección.
     */
    fun distanciaAPolilinea(
        punto: PuntoGeo,
        linea: List<PuntoGeo>,
    ): Double {
        val px = x(punto)
        val py = y(punto)
        var mejor = if (linea.size == 1) hypot(x(linea[0]) - px, y(linea[0]) - py) else Double.POSITIVE_INFINITY
        for (i in 1 until linea.size) {
            mejor =
                minOf(
                    mejor,
                    distanciaASegmento(px, py, x(linea[i - 1]), y(linea[i - 1]), x(linea[i]), y(linea[i])),
                )
        }
        return mejor
    }

    companion object {
        private const val LAT_MAX = 90.0
        private const val LON_MAX = 180.0

        /** Origen en el primer punto de [puntos] y escala a su latitud media (la lista no puede estar vacía). */
        fun deTraza(puntos: List<PuntoGeo>): ProyeccionPlana {
            require(puntos.isNotEmpty()) { "Una proyección necesita al menos un punto" }
            return ProyeccionPlana(puntos.first(), puntos.sumOf { it.lat } / puntos.size)
        }
    }
}

/** Fracción 0..1 del segmento (ax, ay)–(bx, by) más cercana al punto (px, py), en coordenadas planas. */
@Suppress("LongParameterList") // un punto y los dos extremos de un segmento, en coordenadas planas
fun fraccionEnSegmento(
    px: Double,
    py: Double,
    ax: Double,
    ay: Double,
    bx: Double,
    by: Double,
): Double {
    val dx = bx - ax
    val dy = by - ay
    val largo2 = dx * dx + dy * dy
    return if (largo2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / largo2).coerceIn(0.0, 1.0)
}

/** Distancia (m) del punto (px, py) al segmento (ax, ay)–(bx, by), en coordenadas planas; vale con extremos iguales. */
@Suppress("LongParameterList") // un punto y los dos extremos de un segmento, en coordenadas planas
fun distanciaASegmento(
    px: Double,
    py: Double,
    ax: Double,
    ay: Double,
    bx: Double,
    by: Double,
): Double {
    val t = fraccionEnSegmento(px, py, ax, ay, bx, by)
    return hypot(px - (ax + t * (bx - ax)), py - (ay + t * (by - ay)))
}
