package app.trombito.rallycopilot.ui.tutorial

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.math.max

/** De qué lado del elemento señalado va el globo de la pista. */
enum class LadoPista { ARRIBA, ABAJO, IZQUIERDA, DERECHA }

/**
 * Dónde cae el globo: la esquina [origen] en la ventana, el punto [flecha] (en px dentro del globo, medido sobre el
 * eje paralelo al borde del elemento) al que apunta la flecha y el [lado] real donde quedó (puede ser el contrario
 * al pedido si no cabía).
 */
data class PosicionPista(
    val origen: IntOffset,
    val flecha: Int,
    val lado: LadoPista,
)

/**
 * Coloca un globo de [globo] px junto al elemento [ancla] (coordenadas de la ventana de [ventana] px) en el [lado]
 * pedido, con [hueco] px de separación (la flecha) y a [margen] px como mínimo de los bordes. Si el lado elegido no
 * cabe, usa el contrario. El globo se desliza a lo largo del borde para no salirse y la flecha sigue apuntando al
 * centro del elemento.
 */
@Suppress("LongParameterList") // ancla, ventana, globo, lado y las dos medidas: una geometría
fun posicionarPista(
    ancla: IntRect,
    ventana: IntSize,
    globo: IntSize,
    lado: LadoPista,
    hueco: Int,
    margen: Int,
): PosicionPista {
    val vertical = lado == LadoPista.ARRIBA || lado == LadoPista.ABAJO
    return if (vertical) {
        val cabeArriba = ancla.top - hueco - globo.height >= margen
        val cabeAbajo = ancla.bottom + hueco + globo.height <= ventana.height - margen
        val arriba = if (lado == LadoPista.ARRIBA) cabeArriba || !cabeAbajo else !cabeAbajo && cabeArriba
        val y = if (arriba) ancla.top - hueco - globo.height else ancla.bottom + hueco
        val x = deslizar(ancla.center.x - globo.width / 2, globo.width, ventana.width, margen)
        PosicionPista(
            IntOffset(x, y),
            (ancla.center.x - x).coerceIn(0, globo.width),
            if (arriba) LadoPista.ARRIBA else LadoPista.ABAJO,
        )
    } else {
        val cabeIzquierda = ancla.left - hueco - globo.width >= margen
        val cabeDerecha = ancla.right + hueco + globo.width <= ventana.width - margen
        val izquierda =
            if (lado ==
                LadoPista.IZQUIERDA
            ) {
                cabeIzquierda || !cabeDerecha
            } else {
                !cabeDerecha && cabeIzquierda
            }
        val x = if (izquierda) ancla.left - hueco - globo.width else ancla.right + hueco
        val y = deslizar(ancla.center.y - globo.height / 2, globo.height, ventana.height, margen)
        PosicionPista(
            IntOffset(x, y),
            (ancla.center.y - y).coerceIn(0, globo.height),
            if (izquierda) LadoPista.IZQUIERDA else LadoPista.DERECHA,
        )
    }
}

private fun deslizar(
    deseado: Int,
    tamano: Int,
    total: Int,
    margen: Int,
): Int = deseado.coerceIn(margen, max(margen, total - tamano - margen))
