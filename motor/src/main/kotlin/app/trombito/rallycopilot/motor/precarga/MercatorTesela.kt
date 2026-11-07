package app.trombito.rallycopilot.motor.precarga

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/** Conversion entre grados y teselas XYZ de Web Mercator. */
object MercatorTesela {
    /** Columna de la longitud a un zoom con [n] = 2^zoom columnas. */
    fun columna(
        lon: Double,
        n: Int,
    ): Int = floor((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)

    /** Fila XYZ (origen arriba) de la latitud con [n] = 2^zoom filas. */
    fun fila(
        lat: Double,
        n: Int,
    ): Int {
        val r = Math.toRadians(lat)
        return floor((1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
    }

    /** Latitud del borde norte de la fila [y]. */
    fun latitudNorte(
        y: Int,
        zoom: Int,
    ): Double = Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * y / (1 shl zoom)))))

    /** Longitud del borde oeste de la columna [x]. */
    fun longitudOeste(
        x: Int,
        zoom: Int,
    ): Double = x.toDouble() / (1 shl zoom) * 360.0 - 180.0

    /** Metros de lado de una tesela a la [latitud] y [zoom]. */
    fun ladoTeselaM(
        latitud: Double,
        zoom: Int,
    ): Double = 2 * PI * PuntoGeo.RADIO_TIERRA_M * cos(Math.toRadians(latitud)) / (1 shl zoom)
}
