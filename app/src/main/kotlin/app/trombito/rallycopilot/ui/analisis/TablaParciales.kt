package app.trombito.rallycopilot.ui.analisis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.analisis.AnalisisPasada
import app.trombito.rallycopilot.motor.analisis.ParcialAnalisis
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.LocalColoresRally
import app.trombito.rallycopilot.ui.tramos.colorDelta
import app.trombito.rallycopilot.ui.tramos.formatearDelta
import app.trombito.rallycopilot.ui.tramos.formatearTiempo

const val ETIQUETA_FILA_SECTOR = "analisis-fila-sector-"
const val ETIQUETA_FILA_MICRO = "analisis-fila-micro-"
const val ETIQUETA_FILA_TOTAL = "analisis-fila-total"
const val ETIQUETA_TABLA = "analisis-tabla"

private val ALTO_FILA = 44.dp
private const val PESO_ETIQUETA = 1.25f
private const val PESO_TIEMPO = 1.35f
private const val PESO_DELTA = 1.05f
private const val PESO_VELOCIDAD = 0.9f
private const val MS_POR_S = 1000.0
private const val KMH_POR_M_S = 3.6

/** Un renglón de la tabla ya preparado para pintar. */
internal data class FilaTabla(
    val etiqueta: String,
    val rango: String?,
    val tiempoMs: Long,
    val diferenciaMs: Long?,
    val mediaKmh: Double,
    val maximaKmh: Double,
    val etiquetaPrueba: String,
)

/** Velocidad media del tramo entero, en km/h, a partir de la meta y el tiempo total. */
internal fun mediaTotalKmh(analisis: AnalisisPasada): Double {
    val metaM = analisis.sectores.lastOrNull()?.hastaM ?: return 0.0
    return if (analisis.totalMs > 0L) metaM / (analisis.totalMs / MS_POR_S) * KMH_POR_M_S else 0.0
}

/** Velocidad máxima del tramo entero, en km/h. */
internal fun maximaTotalKmh(analisis: AnalisisPasada): Double =
    (analisis.sectores + analisis.microsectores).maxOfOrNull { it.velMaxKmh } ?: 0.0

/**
 * Tabla de parciales: sectores y microsectores con su tiempo, el delta contra la pasada comparada (verde si se gana,
 * rojo si se pierde; «—» sin comparada), y las velocidades media y máxima. Termina con el total del tramo.
 */
@Composable
fun TablaParciales(
    analisis: AnalisisPasada,
    modifier: Modifier = Modifier,
) {
    val recursos = LocalContext.current.resources
    val sectores =
        analisis.sectores.map {
            filaDe(
                it,
                recursos.getString(R.string.analisis_sector_abreviado, it.numero),
                ETIQUETA_FILA_SECTOR + it.numero,
            )
        }
    // Con un solo microsector (tramo corto) repetiría el total: no se muestra.
    val micros =
        if (analisis.microsectores.size > 1) {
            analisis.microsectores.map {
                filaDe(
                    it,
                    recursos.getString(R.string.analisis_micro_abreviado, it.numero),
                    ETIQUETA_FILA_MICRO + it.numero,
                )
            }
        } else {
            emptyList()
        }
    val total =
        FilaTabla(
            etiqueta = recursos.getString(R.string.analisis_total),
            rango = null,
            tiempoMs = analisis.totalMs,
            diferenciaMs = analisis.deltaTotalMs,
            mediaKmh = mediaTotalKmh(analisis),
            maximaKmh = maximaTotalKmh(analisis),
            etiquetaPrueba = ETIQUETA_FILA_TOTAL,
        )
    Column(modifier.fillMaxWidth().testTag(ETIQUETA_TABLA), verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
        Titulo(R.string.analisis_sectores_titulo)
        Cabecera()
        sectores.forEach { Fila(it) }
        Fila(total, destacada = true)
        if (micros.isNotEmpty()) {
            Titulo(R.string.analisis_micros_titulo, Modifier.padding(top = Espacio.m))
            Cabecera()
            micros.forEach { Fila(it) }
        }
    }
}

private fun filaDe(
    parcial: ParcialAnalisis,
    etiqueta: String,
    prueba: String,
) = FilaTabla(
    etiqueta = etiqueta,
    rango = "${etiquetaKm(parcial.desdeM / METROS_POR_KM)}–${formatearKmDesdeM(parcial.hastaM)}",
    tiempoMs = parcial.tiempoMs,
    diferenciaMs = parcial.diferenciaMs,
    mediaKmh = parcial.velMediaKmh,
    maximaKmh = parcial.velMaxKmh,
    etiquetaPrueba = prueba,
)

private const val METROS_POR_KM = 1000.0

@Composable
private fun Titulo(
    texto: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(texto).uppercase(),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
private fun Cabecera() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Espacio.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Celda(stringResource(R.string.analisis_columna_parcial), PESO_ETIQUETA, estilo = EstiloCelda.ENCABEZADO)
        Celda(
            stringResource(R.string.analisis_columna_tiempo),
            PESO_TIEMPO,
            estilo = EstiloCelda.ENCABEZADO,
            alinear = TextAlign.End,
        )
        Celda(
            stringResource(R.string.analisis_columna_delta),
            PESO_DELTA,
            estilo = EstiloCelda.ENCABEZADO,
            alinear = TextAlign.End,
        )
        Celda(
            stringResource(R.string.analisis_columna_media),
            PESO_VELOCIDAD,
            estilo = EstiloCelda.ENCABEZADO,
            alinear = TextAlign.End,
        )
        Celda(
            stringResource(R.string.analisis_columna_maxima),
            PESO_VELOCIDAD,
            estilo = EstiloCelda.ENCABEZADO,
            alinear = TextAlign.End,
        )
    }
}

@Composable
private fun Fila(
    fila: FilaTabla,
    destacada: Boolean = false,
) {
    val colores = LocalColoresRally.current
    val neutro = MaterialTheme.colorScheme.onSurface
    val descripcion = descripcionFila(fila)
    val superficies = MaterialTheme.colorScheme
    val fondo = if (destacada) superficies.surfaceContainerHighest else superficies.surfaceContainerHigh
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ALTO_FILA)
                .background(fondo, MaterialTheme.shapes.small)
                .padding(horizontal = Espacio.s, vertical = Espacio.xs)
                .testTag(fila.etiquetaPrueba)
                .semantics(mergeDescendants = true) { contentDescription = descripcion },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(PESO_ETIQUETA)) {
            Text(fila.etiqueta, style = MaterialTheme.typography.titleSmall, color = neutro)
            fila.rango?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Celda(
            formatearTiempo(fila.tiempoMs),
            PESO_TIEMPO,
            color = neutro,
            alinear = TextAlign.End,
            estilo = EstiloCelda.NEGRITA,
        )
        val delta = fila.diferenciaMs
        Celda(
            texto = delta?.let { formatearDelta(it) } ?: stringResource(R.string.analisis_sin_delta),
            peso = PESO_DELTA,
            color = delta?.let { colorDelta(it, colores, neutro) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
            alinear = TextAlign.End,
            estilo = EstiloCelda.NEGRITA,
        )
        Celda(formatearKmhCelda(fila.mediaKmh), PESO_VELOCIDAD, color = neutro, alinear = TextAlign.End)
        Celda(formatearKmhCelda(fila.maximaKmh), PESO_VELOCIDAD, color = neutro, alinear = TextAlign.End)
    }
}

private enum class EstiloCelda { ENCABEZADO, NORMAL, NEGRITA }

@Composable
private fun RowScope.Celda(
    texto: String,
    peso: Float,
    estilo: EstiloCelda = EstiloCelda.NORMAL,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    alinear: TextAlign = TextAlign.Start,
) {
    val encabezado = estilo == EstiloCelda.ENCABEZADO
    Text(
        text = if (encabezado) texto.uppercase() else texto,
        style = if (encabezado) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium,
        fontWeight = if (estilo == EstiloCelda.NEGRITA) FontWeight.Bold else null,
        color = color,
        textAlign = alinear,
        maxLines = 1,
        modifier = Modifier.weight(peso),
    )
}

@Composable
private fun descripcionFila(fila: FilaTabla): String {
    val delta = fila.diferenciaMs
    return stringResource(
        R.string.analisis_fila_descripcion,
        fila.etiqueta,
        formatearTiempo(fila.tiempoMs),
        if (delta == null) stringResource(R.string.analisis_sin_comparada) else formatearDeltaSegundos(delta),
        formatearKmhEntero(fila.mediaKmh),
        formatearKmhEntero(fila.maximaKmh),
    )
}
