package app.trombito.rallycopilot.ui.mapa

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Hueco de la pantalla (px) donde cabe un bocadillo: [anchoPantallaPx] y los márgenes que no puede pisar (el borde
 * izquierdo, la columna de botones de la derecha y la zona superior de la pantalla).
 */
internal data class LimitesGlobo(
    val anchoPantallaPx: Float,
    val margenIzquierdoPx: Float,
    val margenDerechoPx: Float,
    val margenSuperiorPx: Float,
)

/**
 * Dónde dibujar el bocadillo respecto a su marcador: [picoXPx] es la posición (desde el borde izquierdo del bocadillo)
 * del piquito, que apunta al marcador, y [debajo] si va bajo el marcador (con el piquito arriba) en vez de encima.
 */
internal data class ColocacionGlobo(
    val picoXPx: Float,
    val debajo: Boolean,
) {
    /** Cuánto correr el bocadillo (px, + a la derecha) desde su ancla centrada para que el piquito dé al marcador. */
    fun desplazamientoPx(anchoGloboPx: Float): Float = anchoGloboPx / 2 - picoXPx
}

/**
 * Coloca un bocadillo de [anchoPx] × [altoPx] (el cuerpo, sin el piquito) cuyo marcador está en [xPx], [yPx] de la
 * pantalla: lo más centrado posible sobre el marcador, pero dentro de [limites] (sin salirse ni quedar bajo los
 * botones; el piquito sigue apuntando al marcador y se queda a [margenPicoPx] del borde como poco). Si encima no cabe
 * (por [subidaPx] y el alto), va debajo. [pasoPx] redondea el piquito: así la imagen no cambia con cada pixel que se
 * mueve el mapa (el desplazamiento compensa el redondeo).
 */
@Suppress("LongParameterList") // las medidas del bocadillo, del marcador y de la pantalla
internal fun colocarGlobo(
    xPx: Float,
    yPx: Float,
    anchoPx: Float,
    altoPx: Float,
    subidaPx: Float,
    limites: LimitesGlobo,
    margenPicoPx: Float,
    pasoPx: Float,
): ColocacionGlobo {
    val minimo = limites.margenIzquierdoPx
    val maximo = max(minimo, limites.anchoPantallaPx - limites.margenDerechoPx - anchoPx)
    val izquierdo = (xPx - anchoPx / 2).coerceIn(minimo, maximo)
    val pico = (xPx - izquierdo).coerceIn(margenPicoPx, max(margenPicoPx, anchoPx - margenPicoPx))
    val redondeado = if (pasoPx > 0f) (pico / pasoPx).roundToInt() * pasoPx else pico
    val debajo = yPx - subidaPx - altoPx < limites.margenSuperiorPx
    return ColocacionGlobo(redondeado.coerceIn(margenPicoPx, max(margenPicoPx, anchoPx - margenPicoPx)), debajo)
}
