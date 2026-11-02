package app.trombito.rallycopilot.ui.analisis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.analisis.PuntoSerie
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.LocalColoresRally
import app.trombito.rallycopilot.ui.tramos.colorDelta

const val ETIQUETA_GRAFICO_VELOCIDAD = "analisis-grafico-velocidad"
const val ETIQUETA_GRAFICO_DELTA = "analisis-grafico-delta"
const val ETIQUETA_LECTURA_CURSOR = "analisis-lectura"

private const val GROSOR_PROPIA_PX = 5f
private const val GROSOR_COMPARADA_PX = 3f
private const val OPACIDAD_RELLENO = 0.28f
private const val OPACIDAD_COMPARADA = 0.9f
private const val RADIO_PUNTO_PX = 8f
private const val MARCAS_VELOCIDAD = 4
private const val MS_POR_S = 1000.0

/** Lo que pintan los dos gráficos: series, cortes de sector y escalas. */
data class DatosGraficos(
    val velocidad: List<PuntoSerie>,
    val comparada: List<PuntoSerie>,
    val delta: List<PuntoSerie>,
    val cortesM: List<Double>,
    val distanciaMaxM: Double,
)

/**
 * Los dos gráficos: velocidad por distancia (la pasada analizada y la comparada superpuestas) y, debajo, el delta
 * acumulado. Comparten el cursor: tocar o arrastrar en cualquiera lo pone en ambos (y mueve la repetición del mapa);
 * debajo se lee el valor en ese punto.
 */
@Composable
fun SeccionGraficos(
    datos: DatosGraficos,
    cursorM: Double?,
    onCursor: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lectura = remember(datos, cursorM) { lecturaCursor(cursorM, datos.velocidad, datos.comparada, datos.delta) }
    val textoLectura = textoLectura(lectura)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
        Text(
            text = stringResource(R.string.analisis_graficos_titulo).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        LeyendaSeries(hayComparada = datos.comparada.isNotEmpty())
        GraficoVelocidad(datos, cursorM, textoLectura, onCursor)
        LecturaDelCursor(lectura)
        if (datos.delta.isNotEmpty()) {
            Text(
                text = stringResource(R.string.analisis_grafico_delta_titulo),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GraficoDelta(datos, cursorM, textoLectura, onCursor)
        }
    }
}

@Composable
private fun GraficoVelocidad(
    datos: DatosGraficos,
    cursorM: Double?,
    textoLectura: String,
    onCursor: (Double) -> Unit,
) {
    val escala = remember(datos) { escalaVelocidad(listOf(datos.velocidad, datos.comparada), datos.distanciaMaxM) }
    val marcas = remember(escala) { marcasEje(escala.minimo, escala.maximo, MARCAS_VELOCIDAD) }
    val definicion =
        DefinicionGrafico(
            escala = escala,
            cortesM = datos.cortesM,
            marcasY = marcas,
            etiquetaY = { formatearKmhCelda(it) },
            descripcion = stringResource(R.string.analisis_grafico_velocidad_descripcion),
            lectura = textoLectura,
        )
    val propia = MaterialTheme.colorScheme.primary
    val comparada = MaterialTheme.colorScheme.secondary
    LienzoGrafico(definicion, cursorM, onCursor, Modifier.testTag(ETIQUETA_GRAFICO_VELOCIDAD)) { area ->
        trazarSerie(datos.comparada, escala, area, comparada.copy(alpha = OPACIDAD_COMPARADA), GROSOR_COMPARADA_PX)
        trazarSerie(datos.velocidad, escala, area, propia, GROSOR_PROPIA_PX)
        cursorM?.let { puntoCursor(datos.comparada, it, escala, area, comparada) }
        cursorM?.let { puntoCursor(datos.velocidad, it, escala, area, propia) }
    }
}

@Composable
private fun GraficoDelta(
    datos: DatosGraficos,
    cursorM: Double?,
    textoLectura: String,
    onCursor: (Double) -> Unit,
) {
    val escala = remember(datos) { escalaDelta(datos.delta, datos.distanciaMaxM) }
    val marcas = remember(escala) { listOf(escala.maximo, 0.0, escala.minimo) }
    val definicion =
        DefinicionGrafico(
            escala = escala,
            cortesM = datos.cortesM,
            marcasY = marcas,
            etiquetaY = { etiquetaDeltaS(it / MS_POR_S) },
            descripcion = stringResource(R.string.analisis_grafico_delta_descripcion),
            lectura = textoLectura,
        )
    val colores = LocalColoresRally.current
    LienzoGrafico(definicion, cursorM, onCursor, Modifier.testTag(ETIQUETA_GRAFICO_DELTA)) { area ->
        trazarDelta(datos.delta, escala, area, colores.deltaMejor, colores.deltaPeor)
    }
}

/** Línea de la serie sobre el área; ignora series con menos de dos muestras. */
private fun DrawScope.trazarSerie(
    serie: List<PuntoSerie>,
    escala: EscalaGrafico,
    area: AreaGrafico,
    color: Color,
    grosorPx: Float,
) {
    if (serie.size < 2) return
    val camino = Path()
    serie.forEachIndexed { i, p ->
        val x = area.x(escala.fraccionX(p.distanciaM))
        val y = area.y(escala.fraccionY(p.valor))
        if (i == 0) camino.moveTo(x, y) else camino.lineTo(x, y)
    }
    drawPath(camino, color, style = Stroke(width = grosorPx, cap = StrokeCap.Round))
}

private fun DrawScope.puntoCursor(
    serie: List<PuntoSerie>,
    distanciaM: Double,
    escala: EscalaGrafico,
    area: AreaGrafico,
    color: Color,
) {
    val valor = valorEn(serie, distanciaM) ?: return
    drawCircle(color, RADIO_PUNTO_PX, Offset(area.x(escala.fraccionX(distanciaM)), area.y(escala.fraccionY(valor))))
}

/**
 * Delta acumulado: por encima del cero (rojo) se pierde tiempo contra la comparada, por debajo (verde) se gana. Cada
 * tramo entre muestras se rellena hasta la línea del cero y se traza con el color de su signo.
 */
private fun DrawScope.trazarDelta(
    serie: List<PuntoSerie>,
    escala: EscalaGrafico,
    area: AreaGrafico,
    mejor: Color,
    peor: Color,
) {
    if (serie.size < 2) return
    val cero = area.y(escala.fraccionY(0.0))
    serie.zipWithNext().forEach { (a, b) ->
        val color = if ((a.valor + b.valor) / 2.0 <= 0.0) mejor else peor
        val xa = area.x(escala.fraccionX(a.distanciaM))
        val xb = area.x(escala.fraccionX(b.distanciaM))
        val ya = area.y(escala.fraccionY(a.valor))
        val yb = area.y(escala.fraccionY(b.valor))
        val relleno =
            Path().apply {
                moveTo(xa, cero)
                lineTo(xa, ya)
                lineTo(xb, yb)
                lineTo(xb, cero)
                close()
            }
        drawPath(relleno, color.copy(alpha = OPACIDAD_RELLENO))
        drawLine(color, Offset(xa, ya), Offset(xb, yb), GROSOR_PROPIA_PX, StrokeCap.Round)
    }
}

@Composable
private fun LeyendaSeries(hayComparada: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(Espacio.l), verticalAlignment = Alignment.CenterVertically) {
        EntradaLeyenda(MaterialTheme.colorScheme.primary, stringResource(R.string.analisis_leyenda_esta))
        if (hayComparada) {
            EntradaLeyenda(MaterialTheme.colorScheme.secondary, stringResource(R.string.analisis_leyenda_comparada))
        }
    }
}

@Composable
private fun EntradaLeyenda(
    color: Color,
    texto: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Espacio.xs), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(texto, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun textoLectura(lectura: LecturaCursor?): String =
    if (lectura == null) {
        stringResource(R.string.analisis_lectura_ayuda)
    } else {
        buildString {
            append(stringResource(R.string.analisis_lectura_km, formatearKmDesdeM(lectura.distanciaM)))
            lectura.velocidadKmh?.let { append(". ").append(formatearKmhEntero(it)) }
            lectura.comparadaKmh?.let {
                append(". ").append(stringResource(R.string.analisis_lectura_comparada, formatearKmhEntero(it)))
            }
            lectura.deltaMs?.let { append(". ").append(formatearDeltaSegundos(it)) }
        }
    }

/** Línea bajo el gráfico de velocidad con los valores en el cursor (o la pista de que se puede tocar). */
@Composable
private fun LecturaDelCursor(lectura: LecturaCursor?) {
    val colores = LocalColoresRally.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(ETIQUETA_LECTURA_CURSOR)
                .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(Espacio.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (lectura == null) {
            Text(
                text = stringResource(R.string.analisis_lectura_ayuda),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = formatearKmDesdeM(lectura.distanciaM),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            lectura.velocidadKmh?.let {
                Text(
                    formatearKmhEntero(it),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            lectura.comparadaKmh?.let {
                Text(
                    text = formatearKmhEntero(it),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            lectura.deltaMs?.let {
                Text(
                    text = formatearDeltaSegundos(it),
                    style = MaterialTheme.typography.labelLarge,
                    color = colorDelta(it, colores, MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.padding(start = Espacio.xs),
                )
            }
        }
    }
}
