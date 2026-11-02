package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.ui.geometry.Offset
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlin.math.hypot

/**
 * La traza de un tramo puesta en un lienzo de [ancho] x [alto] píxeles con su distancia acumulada, para pasar de metros
 * del tramo a píxeles ([pixelEn]) y de un toque a metros ([proyectar]). Es el mismo encuadre que [proyectarTraza]
 * (norte
 * arriba, sin deformar), con todos los puntos (no se aligera) para que los metros sean exactos.
 */
class LienzoTramo(
    traza: List<PuntoGeo>,
    ancho: Float,
    alto: Float,
    margen: Float,
) {
    /** Punto de la traza sobre el que cae un toque: sus [metros] desde la salida y la [distanciaPx] al toque. */
    class Proyeccion(
        val metros: Double,
        val distanciaPx: Float,
    )

    private val puntos: List<Offset> = proyectarTraza(traza, ancho, alto, margen)
    private val acumulada = DoubleArray(traza.size)

    init {
        for (i in 1 until traza.size) acumulada[i] = acumulada[i - 1] + traza[i - 1].distanciaA(traza[i])
    }

    /** Los puntos de la traza en píxeles, para dibujarla. */
    val trazo: List<Offset> get() = puntos

    val longitudM: Double get() = acumulada.lastOrNull() ?: 0.0

    /** Píxel que corresponde a [metros] desde la salida (saturado a los extremos). */
    fun pixelEn(metros: Double): Offset {
        if (puntos.size < 2) return puntos.firstOrNull() ?: Offset.Zero
        val m = metros.coerceIn(0.0, longitudM)
        var i = acumulada.indexOfLast { it <= m }.coerceIn(0, puntos.size - 2)
        while (i < puntos.size - 2 && acumulada[i + 1] <= m) i++
        val largo = acumulada[i + 1] - acumulada[i]
        val t = if (largo > 0.0) ((m - acumulada[i]) / largo).toFloat() else 0f
        return Offset(
            puntos[i].x + (puntos[i + 1].x - puntos[i].x) * t,
            puntos[i].y + (puntos[i + 1].y - puntos[i].y) * t,
        )
    }

    /** El punto de la traza más cercano al toque [toque]; null si la traza no tiene al menos dos puntos. */
    fun proyectar(toque: Offset): Proyeccion? {
        if (puntos.size < 2) return null
        var mejorPx = Float.MAX_VALUE
        var mejorM = 0.0
        for (i in 0 until puntos.size - 1) {
            val ax = puntos[i].x
            val ay = puntos[i].y
            val dx = puntos[i + 1].x - ax
            val dy = puntos[i + 1].y - ay
            val largo2 = dx * dx + dy * dy
            val t = if (largo2 == 0f) 0f else (((toque.x - ax) * dx + (toque.y - ay) * dy) / largo2).coerceIn(0f, 1f)
            val d = hypot(toque.x - (ax + t * dx), toque.y - (ay + t * dy))
            if (d < mejorPx) {
                mejorPx = d
                mejorM = acumulada[i] + t * (acumulada[i + 1] - acumulada[i])
            }
        }
        return Proyeccion(mejorM, mejorPx)
    }
}
