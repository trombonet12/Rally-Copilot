package app.trombito.rallycopilot.ui.analisis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ALTO_GRAFICO = 150.dp
private val MARGEN_IZQUIERDO = 40.dp
private val MARGEN_DERECHO = 10.dp
private val MARGEN_SUPERIOR = 10.dp
private val MARGEN_INFERIOR = 22.dp
private const val OPACIDAD_GUIA = 0.6f
private const val OPACIDAD_CORTE = 0.7f
private const val GROSOR_GUIA_PX = 1.5f
private const val GROSOR_CURSOR_PX = 2.5f
private const val CORTE_DASH_PX = 10f
private const val TAMANO_ETIQUETA_SP = 11
private const val SEPARACION_ETIQUETA_PX = 6f
private const val MARCAS_DISTANCIA = 4

/** Rectángulo del área de dibujo (sin márgenes de los ejes), en píxeles del lienzo. */
internal data class AreaGrafico(
    val izquierda: Float,
    val arriba: Float,
    val ancho: Float,
    val alto: Float,
) {
    fun x(fraccion: Float): Float = izquierda + fraccion * ancho

    fun y(fraccion: Float): Float = arriba + fraccion * alto
}

internal fun areaDe(
    ancho: Float,
    alto: Float,
    densidad: Density,
): AreaGrafico =
    with(densidad) {
        val izquierda = MARGEN_IZQUIERDO.toPx()
        val arriba = MARGEN_SUPERIOR.toPx()
        AreaGrafico(
            izquierda = izquierda,
            arriba = arriba,
            ancho = (ancho - izquierda - MARGEN_DERECHO.toPx()).coerceAtLeast(0f),
            alto = (alto - arriba - MARGEN_INFERIOR.toPx()).coerceAtLeast(0f),
        )
    }

/** Lo que cambia entre un gráfico y otro: el eje Y y lo que se dibuja encima de la rejilla. */
internal class DefinicionGrafico(
    val escala: EscalaGrafico,
    val cortesM: List<Double>,
    val marcasY: List<Double>,
    val etiquetaY: (Double) -> String,
    val descripcion: String,
    val lectura: String,
)

/** Colores del lienzo: se resuelven fuera del `DrawScope` desde el tema. */
internal class ColoresGrafico(
    val fondo: Color,
    val guia: Color,
    val texto: Color,
    val corte: Color,
    val cursor: Color,
)

/**
 * Lienzo común de los gráficos de serie sobre distancia: fondo, rejilla con las marcas del eje Y, eje X en km, cortes
 * de sector, el cursor y lo que pinte [series]. Un toque o un arrastre horizontal pone el cursor y avisa con la
 * distancia en metros. El lienzo es una región con descripción y estado propios para TalkBack.
 */
@Composable
internal fun LienzoGrafico(
    definicion: DefinicionGrafico,
    cursorM: Double?,
    onCursor: (Double) -> Unit,
    modifier: Modifier = Modifier,
    series: DrawScope.(AreaGrafico) -> Unit,
) {
    val medidor = rememberTextMeasurer()
    val colores = MaterialTheme.colorScheme
    val paleta =
        ColoresGrafico(
            fondo = colores.surfaceContainerLowest,
            guia = colores.outlineVariant,
            texto = colores.onSurfaceVariant,
            corte = colores.primary.copy(alpha = OPACIDAD_CORTE),
            cursor = colores.onSurface,
        )
    val alAvisar = rememberUpdatedState(onCursor)
    val escala = definicion.escala
    Canvas(
        modifier
            .fillMaxWidth()
            .height(ALTO_GRAFICO)
            .background(paleta.fondo, MaterialTheme.shapes.small)
            .semantics {
                contentDescription = definicion.descripcion
                stateDescription = definicion.lectura
            }.pointerInput(escala) {
                val area = areaDe(size.width.toFloat(), size.height.toFloat(), this)
                val avisar = { x: Float -> alAvisar.value(distanciaDeToque(x, area.izquierda, area.ancho, escala)) }
                detectTapGestures { avisar(it.x) }
            }.pointerInput(escala) {
                val area = areaDe(size.width.toFloat(), size.height.toFloat(), this)
                val avisar = { x: Float -> alAvisar.value(distanciaDeToque(x, area.izquierda, area.ancho, escala)) }
                detectHorizontalDragGestures(onDragStart = { avisar(it.x) }) { cambio, _ ->
                    cambio.consume()
                    avisar(cambio.position.x)
                }
            },
    ) {
        val area = areaDe(size.width, size.height, this)
        dibujarRejilla(area, definicion, medidor, paleta)
        dibujarCortes(area, definicion.cortesM, escala, paleta)
        series(area)
        cursorM?.let { dibujarCursor(area, escala.fraccionX(it), paleta) }
    }
}

private fun DrawScope.dibujarRejilla(
    area: AreaGrafico,
    definicion: DefinicionGrafico,
    medidor: TextMeasurer,
    paleta: ColoresGrafico,
) {
    val estilo = TextStyle(color = paleta.texto, fontSize = TAMANO_ETIQUETA_SP.sp)
    val escala = definicion.escala
    definicion.marcasY.forEach { valor ->
        val y = area.y(escala.fraccionY(valor))
        drawLine(
            paleta.guia.copy(alpha = OPACIDAD_GUIA),
            Offset(area.izquierda, y),
            Offset(area.izquierda + area.ancho, y),
            GROSOR_GUIA_PX,
        )
        val texto = medidor.measure(definicion.etiquetaY(valor), estilo)
        val x = area.izquierda - texto.size.width - SEPARACION_ETIQUETA_PX
        // Dentro del área: la marca más baja no debe pisar las etiquetas del eje de distancia.
        val arriba =
            (y - texto.size.height / 2f).coerceIn(
                area.arriba,
                maxOf(
                    area.arriba,
                    area.arriba + area.alto - texto.size.height,
                ),
            )
        drawText(texto, topLeft = Offset(x, arriba))
    }
    val marcasKm = marcasEje(0.0, escala.distanciaMaxM / METROS_POR_KM, MARCAS_DISTANCIA)
    marcasKm.forEachIndexed { i, km ->
        val x = area.x(escala.fraccionX(km * METROS_POR_KM))
        val unidad = if (i == marcasKm.lastIndex) " km" else ""
        val texto = medidor.measure(etiquetaKm(km) + unidad, estilo)
        drawText(texto, topLeft = Offset(x - texto.size.width / 2f, area.arriba + area.alto + SEPARACION_ETIQUETA_PX))
    }
}

private fun DrawScope.dibujarCortes(
    area: AreaGrafico,
    cortesM: List<Double>,
    escala: EscalaGrafico,
    paleta: ColoresGrafico,
) {
    val trazo = PathEffect.dashPathEffect(floatArrayOf(CORTE_DASH_PX, CORTE_DASH_PX))
    cortesM.forEach { corte ->
        val x = area.x(escala.fraccionX(corte))
        drawLine(
            paleta.corte,
            Offset(x, area.arriba),
            Offset(x, area.arriba + area.alto),
            GROSOR_GUIA_PX,
            pathEffect = trazo,
        )
    }
}

private fun DrawScope.dibujarCursor(
    area: AreaGrafico,
    fraccionX: Float,
    paleta: ColoresGrafico,
) {
    val x = area.x(fraccionX)
    drawLine(paleta.cursor, Offset(x, area.arriba), Offset(x, area.arriba + area.alto), GROSOR_CURSOR_PX)
}

private const val METROS_POR_KM = 1000.0
