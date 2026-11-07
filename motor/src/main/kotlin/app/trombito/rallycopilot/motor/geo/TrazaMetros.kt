package app.trombito.rallycopilot.motor.geo

import java.util.Arrays
import kotlin.math.hypot

/** Punto de la traza más cercano a un toque: sus [metros] desde la salida y la [distanciaM] al toque. */
class ProyeccionEnTraza(
    val metros: Double,
    val distanciaM: Double,
)

/**
 * La traza de un tramo con su distancia acumulada, para pasar de metros a posición geográfica ([puntoEn]) y de una
 * posición (un toque en el mapa) a metros ([proyectar]). Usa los mismos metros que las notas guardadas: la suma de las
 * distancias entre puntos de la traza.
 */
class TrazaMetros(
    private val traza: List<PuntoGeo>,
) {
    private val acumulada = DoubleArray(traza.size)

    init {
        for (i in 1 until traza.size) acumulada[i] = acumulada[i - 1] + traza[i - 1].distanciaA(traza[i])
    }

    val longitudM: Double get() = acumulada.lastOrNull() ?: 0.0

    /** Posición a [metros] de la salida, acotada a los extremos de la traza. */
    fun puntoEn(metros: Double): PuntoGeo {
        val d = metros.coerceIn(0.0, longitudM)
        val busqueda = Arrays.binarySearch(acumulada, d)
        val i = if (busqueda >= 0) busqueda else -busqueda - 2
        return if (traza.isEmpty()) {
            PuntoGeo(0.0, 0.0)
        } else if (i >= traza.lastIndex) {
            traza.last()
        } else {
            val largo = acumulada[i + 1] - acumulada[i]
            val t = if (largo > 0.0) (d - acumulada[i]) / largo else 0.0
            PuntoGeo(
                traza[i].lat + t * (traza[i + 1].lat - traza[i].lat),
                traza[i].lon + t * (traza[i + 1].lon - traza[i].lon),
            )
        }
    }

    /** Punto de la traza más cercano a [punto]; null si la traza no tiene al menos dos puntos. */
    fun proyectar(punto: PuntoGeo): ProyeccionEnTraza? {
        if (traza.size < 2) return null
        val plano = ProyeccionPlana(punto)
        var mejorDistancia = Double.MAX_VALUE
        var mejorMetros = 0.0
        for (i in 0 until traza.size - 1) {
            val ax = plano.x(traza[i])
            val ay = plano.y(traza[i])
            val bx = plano.x(traza[i + 1])
            val by = plano.y(traza[i + 1])
            val t = fraccionEnSegmento(0.0, 0.0, ax, ay, bx, by)
            val distancia = hypot(ax + t * (bx - ax), ay + t * (by - ay))
            if (distancia < mejorDistancia) {
                mejorDistancia = distancia
                mejorMetros = acumulada[i] + t * (acumulada[i + 1] - acumulada[i])
            }
        }
        return ProyeccionEnTraza(mejorMetros, mejorDistancia)
    }
}
