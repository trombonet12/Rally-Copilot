package app.trombito.rallycopilot.motor.precarga

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Tesela XYZ de Web Mercator (origen arriba a la izquierda). */
data class Tesela(
    val z: Int,
    val x: Int,
    val y: Int,
) {
    /** Fila TMS (origen abajo), que es la que usan el TMS del IGN y la columna `tile_row` de MBTiles. */
    val filaTms: Int get() = (1 shl z) - 1 - y
}

/** Rectángulo en grados: oeste <= este, sur <= norte. */
data class Rectangulo(
    val oeste: Double,
    val sur: Double,
    val este: Double,
    val norte: Double,
) {
    init {
        require(oeste <= este && sur <= norte) { "Rectángulo invertido" }
    }

    fun interseca(otro: Rectangulo): Boolean =
        oeste <= otro.este && este >= otro.oeste && sur <= otro.norte && norte >= otro.sur

    fun recortar(otro: Rectangulo): Rectangulo? =
        if (interseca(otro)) {
            Rectangulo(max(oeste, otro.oeste), max(sur, otro.sur), min(este, otro.este), min(norte, otro.norte))
        } else {
            null
        }
}

/**
 * Teselas XYZ que cubren una zona: el corredor de una polilínea o un rectángulo. Solo se piden teselas que tocan el
 * [MALLORCA] (el resto de la caja del IGN son mar abierto y otras islas).
 */
object TeselasPrecarga {
    /** La misma caja que el paquete de datos (`satelite.py`): oeste, sur, este, norte. */
    val MALLORCA = Rectangulo(2.30, 39.26, 3.50, 39.97)

    const val ANCHO_CORREDOR_M = 150.0
    const val ZOOM_CORREDOR = 18
    const val ZOOM_MIN_ZONA = 18
    const val ZOOM_MAX_ZONA = 19

    private const val METROS_POR_GRADO_LAT = 111_320.0
    private const val SEPARACION_MUESTRAS_M = 50.0

    /**
     * Teselas de [zoom] a menos de [anchoM] metros (en caja, no en círculo) de la polilínea, sin duplicados y solo las
     * que tocan [limite]. Una polilínea vacía no da teselas.
     */
    fun corredor(
        polilinea: List<PuntoGeo>,
        zoom: Int,
        anchoM: Double = ANCHO_CORREDOR_M,
        limite: Rectangulo = MALLORCA,
    ): Set<Tesela> {
        require(anchoM > 0) { "El ancho debe ser positivo" }
        val claves = HashSet<Tesela>()
        muestrear(polilinea).forEach { p -> anadirCaja(claves, p, anchoM, zoom, limite) }
        return claves
    }

    /** Teselas de [zoom] que tocan [rectangulo] (recortado a [limite]), de izquierda a derecha y de arriba abajo. */
    fun rectangulo(
        rectangulo: Rectangulo,
        zoom: Int,
        limite: Rectangulo = MALLORCA,
    ): List<Tesela> {
        val zona = rectangulo.recortar(limite) ?: return emptyList()
        val r = rango(zona, zoom)
        val resultado = ArrayList<Tesela>((r.x1 - r.x0 + 1) * (r.y1 - r.y0 + 1))
        for (y in r.y0..r.y1) for (x in r.x0..r.x1) resultado += Tesela(zoom, x, y)
        return resultado
    }

    /** Número de teselas de [rectangulo] a [zoom] sin enumerarlas (para estimar antes de confirmar). */
    fun contarRectangulo(
        rectangulo: Rectangulo,
        zoom: Int,
        limite: Rectangulo = MALLORCA,
    ): Long {
        val zona = rectangulo.recortar(limite) ?: return 0
        val r = rango(zona, zoom)
        return (r.x1 - r.x0 + 1).toLong() * (r.y1 - r.y0 + 1)
    }

    private fun anadirCaja(
        destino: MutableSet<Tesela>,
        centro: PuntoGeo,
        anchoM: Double,
        zoom: Int,
        limite: Rectangulo,
    ) {
        val dLat = anchoM / METROS_POR_GRADO_LAT
        val dLon = anchoM / (METROS_POR_GRADO_LAT * cos(Math.toRadians(centro.lat)))
        val caja = Rectangulo(centro.lon - dLon, centro.lat - dLat, centro.lon + dLon, centro.lat + dLat)
        val zona = caja.recortar(limite) ?: return
        val r = rango(zona, zoom)
        for (y in r.y0..r.y1) for (x in r.x0..r.x1) destino += Tesela(zoom, x, y)
    }

    /** Puntos de la polilínea más los intermedios para que no haya más de [SEPARACION_MUESTRAS_M] entre ellos. */
    private fun muestrear(polilinea: List<PuntoGeo>): List<PuntoGeo> {
        if (polilinea.size < 2) return polilinea
        val salida = ArrayList<PuntoGeo>(polilinea.size)
        salida += polilinea.first()
        for (i in 1 until polilinea.size) {
            val a = polilinea[i - 1]
            val b = polilinea[i]
            val pasos = floor(a.distanciaA(b) / SEPARACION_MUESTRAS_M).toInt()
            for (k in 1..pasos) {
                val f = k.toDouble() / (pasos + 1)
                salida += PuntoGeo(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f)
            }
            salida += b
        }
        return salida
    }

    private class Rango(
        val x0: Int,
        val x1: Int,
        val y0: Int,
        val y1: Int,
    )

    private fun rango(
        zona: Rectangulo,
        zoom: Int,
    ): Rango {
        val n = 1 shl zoom
        return Rango(
            x0 = MercatorTesela.columna(zona.oeste, n),
            x1 = MercatorTesela.columna(zona.este, n),
            y0 = MercatorTesela.fila(zona.norte, n),
            y1 = MercatorTesela.fila(zona.sur, n),
        )
    }
}
