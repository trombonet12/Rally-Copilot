package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.ui.geometry.Offset
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlin.math.cos
import kotlin.math.min

private const val GRADOS_A_RADIANES = Math.PI / 180.0

/**
 * Pone una traza en un lienzo de [ancho] x [alto] píxeles: proyección equirectangular con la longitud corregida por
 * la latitud media (no se deforma), escala común a los dos ejes, centrada y con [margen] alrededor. El norte queda
 * arriba. Un único punto, o todos iguales, queda en el centro.
 */
fun proyectarTraza(
    puntos: List<PuntoGeo>,
    ancho: Float,
    alto: Float,
    margen: Float,
): List<Offset> {
    if (puntos.isEmpty()) return emptyList()
    val factorLon = cos(puntos.sumOf { it.lat } / puntos.size * GRADOS_A_RADIANES)
    val xs = puntos.map { it.lon * factorLon }
    val ys = puntos.map { -it.lat }
    val minX = xs.min()
    val minY = ys.min()
    val anchoDatos = xs.max() - minX
    val altoDatos = ys.max() - minY
    val utilX = (ancho - 2 * margen).coerceAtLeast(1f)
    val utilY = (alto - 2 * margen).coerceAtLeast(1f)
    val escalaX = if (anchoDatos > 0.0) utilX / anchoDatos.toFloat() else Float.MAX_VALUE
    val escalaY = if (altoDatos > 0.0) utilY / altoDatos.toFloat() else Float.MAX_VALUE
    val escala = min(escalaX, escalaY).takeIf { it != Float.MAX_VALUE } ?: 1f
    val sobraX = (utilX - anchoDatos.toFloat() * escala) / 2f
    val sobraY = (utilY - altoDatos.toFloat() * escala) / 2f
    return puntos.indices.map { i ->
        Offset(
            x = margen + sobraX + (xs[i] - minX).toFloat() * escala,
            y = margen + sobraY + (ys[i] - minY).toFloat() * escala,
        )
    }
}

/** Reduce [puntos] a como mucho [maximo] conservando siempre el primero y el último. */
fun <T> aligerar(
    puntos: List<T>,
    maximo: Int,
): List<T> {
    if (puntos.size <= maximo || maximo < 2) return puntos
    val paso = (puntos.size - 1).toDouble() / (min(maximo, puntos.size) - 1)
    return (0 until maximo).map { puntos[Math.round(it * paso).toInt().coerceAtMost(puntos.lastIndex)] }
}
