package app.trombito.rallycopilot.ui.comun

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.min

/** Esquina que se corta en diagonal (el sentido es el de la pantalla, no el de lectura). */
enum class EsquinaBiselada { SUPERIOR_IZQUIERDA, SUPERIOR_DERECHA, INFERIOR_DERECHA, INFERIOR_IZQUIERDA }

/**
 * Rectángulo de esquinas redondeadas ([radio]) con una esquina cortada en diagonal ([corte] a cada lado), como los
 * rótulos de retransmisión de rally.
 */
class FormaBiselada(
    private val corte: Dp = 16.dp,
    private val radio: Dp = 12.dp,
    private val esquina: EsquinaBiselada = EsquinaBiselada.SUPERIOR_DERECHA,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val mitad = min(size.width, size.height) / 2f
        val c = min(with(density) { corte.toPx() }, mitad)
        val r = min(with(density) { radio.toPx() }, mitad)
        return Outline.Generic(trazar(size.width, size.height, c, r))
    }

    /** Vértices en sentido horario; la esquina cortada se sustituye por sus dos extremos, sin redondear. */
    private fun trazar(
        w: Float,
        h: Float,
        c: Float,
        r: Float,
    ): Path {
        val vertices = mutableListOf<Pair<Offset, Float>>()

        fun anadir(
            actual: EsquinaBiselada,
            punto: Offset,
            antes: Offset,
            despues: Offset,
        ) {
            if (esquina == actual) {
                vertices += antes to 0f
                vertices += despues to 0f
            } else {
                vertices += punto to r
            }
        }
        anadir(EsquinaBiselada.SUPERIOR_IZQUIERDA, Offset(0f, 0f), Offset(0f, c), Offset(c, 0f))
        anadir(EsquinaBiselada.SUPERIOR_DERECHA, Offset(w, 0f), Offset(w - c, 0f), Offset(w, c))
        anadir(EsquinaBiselada.INFERIOR_DERECHA, Offset(w, h), Offset(w, h - c), Offset(w - c, h))
        anadir(EsquinaBiselada.INFERIOR_IZQUIERDA, Offset(0f, h), Offset(c, h), Offset(0f, h - c))
        return poligonoRedondeado(vertices)
    }

    private fun poligonoRedondeado(vertices: List<Pair<Offset, Float>>): Path {
        val n = vertices.size
        val camino = Path()
        for (i in 0 until n) {
            val (p, radioVertice) = vertices[i]
            val entrada = haciaVertice(p, vertices[(i + n - 1) % n].first, radioVertice)
            val salida = haciaVertice(p, vertices[(i + 1) % n].first, radioVertice)
            if (i == 0) camino.moveTo(entrada.x, entrada.y) else camino.lineTo(entrada.x, entrada.y)
            if (radioVertice > 0f) {
                camino.quadraticTo(p.x, p.y, salida.x, salida.y)
            } else {
                camino.lineTo(salida.x, salida.y)
            }
        }
        camino.close()
        return camino
    }

    /** Punto a [distancia] de [vertice] sobre el segmento que va hacia [otro] (sin pasar de la mitad). */
    private fun haciaVertice(
        vertice: Offset,
        otro: Offset,
        distancia: Float,
    ): Offset {
        val largo = hypot(otro.x - vertice.x, otro.y - vertice.y)
        if (largo == 0f || distancia == 0f) return vertice
        val t = min(distancia, largo / 2f) / largo
        return Offset(vertice.x + (otro.x - vertice.x) * t, vertice.y + (otro.y - vertice.y) * t)
    }
}
