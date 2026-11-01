package app.trombito.rallycopilot.motor.alertas

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlin.math.cos

/**
 * Geometría de pruebas: una ruta recta hacia el norte desde [BASE] y puntos a "x metros al norte, y al este" de ella,
 * con la misma esfera que el motor para que los metros salgan exactos.
 */
object GeoPruebas {
    val BASE = PuntoGeo(39.60, 2.90)
    private const val M_POR_GRADO = PuntoGeo.RADIO_TIERRA_M * Math.PI / 180.0

    fun punto(
        norteM: Double,
        esteM: Double = 0.0,
        desde: PuntoGeo = BASE,
    ) = PuntoGeo(
        desde.lat + norteM / M_POR_GRADO,
        desde.lon + esteM / (M_POR_GRADO * cos(Math.toRadians(desde.lat))),
    )

    /** Ruta recta hacia el norte de [largoM] metros con un vértice cada [pasoM]. */
    fun rutaNorte(
        largoM: Double = 10_000.0,
        pasoM: Double = 500.0,
    ): List<PuntoGeo> = generateSequence(0.0) { it + pasoM }.takeWhile { it <= largoM }.map { punto(it) }.toList()
}
