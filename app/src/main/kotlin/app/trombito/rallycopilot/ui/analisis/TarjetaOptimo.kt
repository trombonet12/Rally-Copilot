package app.trombito.rallycopilot.ui.analisis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.mistramos.formatearFechaCorta
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.LocalColoresRally
import app.trombito.rallycopilot.ui.tramos.colorDelta
import app.trombito.rallycopilot.ui.tramos.formatearDelta
import app.trombito.rallycopilot.ui.tramos.formatearTiempo

const val ETIQUETA_OPTIMO = "analisis-optimo"
const val ETIQUETA_OPTIMO_TOTAL = "analisis-optimo-total"
const val ETIQUETA_OPTIMO_GANANCIA = "analisis-optimo-ganancia"
const val ETIQUETA_OPTIMO_SECTOR_PREFIJO = "analisis-optimo-sector-"

private val ALTO_FILA_OPTIMO = 44.dp
private const val TAMANO_TOTAL_OPTIMO_SP = 30

/**
 * Tarjeta «Óptimo teórico»: el tiempo que saldría juntando tu mejor sector de cada pasada, lo que ganarías frente a la
 * mejor pasada completa (en verde, como un delta a favor) y, por cada sector, su tiempo y de qué pasada (fecha) es.
 */
@Composable
fun TarjetaOptimo(
    optimo: OptimoUi,
    modifier: Modifier = Modifier,
) {
    val ganancia = optimo.gananciaMs
    val yaOptima = ganancia != null && ganancia <= 0L
    val total = formatearTiempo(optimo.totalMs)
    val resumen =
        when {
            ganancia == null -> {
                ""
            }

            yaOptima -> {
                stringResource(R.string.analisis_optimo_descripcion_ya_optima)
            }

            else -> {
                stringResource(
                    R.string.analisis_optimo_descripcion_ganancia,
                    formatearDeltaSegundos(-ganancia),
                )
            }
        }
    val descripcion = stringResource(R.string.analisis_optimo_descripcion, total, resumen).trim()
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
                .padding(Espacio.m)
                .testTag(ETIQUETA_OPTIMO),
        verticalArrangement = Arrangement.spacedBy(Espacio.xs),
    ) {
        Text(
            text = stringResource(R.string.analisis_optimo_titulo).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        Column(Modifier.clearAndSetSemantics { contentDescription = descripcion }) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Espacio.m),
            ) {
                Text(
                    text = total,
                    style = estiloCifrasOptimo(),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.testTag(ETIQUETA_OPTIMO_TOTAL),
                )
                if (ganancia != null) Ganancia(ganancia, yaOptima, Modifier.weight(1f))
            }
        }
        Text(
            text = stringResource(R.string.analisis_optimo_ayuda),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        optimo.sectores.forEach { FilaSectorOptimo(it) }
    }
}

@Composable
private fun Ganancia(
    gananciaMs: Long,
    yaOptima: Boolean,
    modifier: Modifier,
) {
    val colores = LocalColoresRally.current
    val neutro = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        Text(
            text =
                if (yaOptima) {
                    stringResource(R.string.analisis_optimo_ya_optima)
                } else {
                    "${formatearDelta(-gananciaMs)} s"
                },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (yaOptima) neutro else colorDelta(-gananciaMs, colores, neutro),
            modifier = Modifier.testTag(ETIQUETA_OPTIMO_GANANCIA),
        )
        if (!yaOptima) {
            Text(
                text = stringResource(R.string.analisis_optimo_ganancia),
                style = MaterialTheme.typography.labelSmall,
                color = neutro,
            )
        }
    }
}

@Composable
private fun FilaSectorOptimo(sector: SectorOptimoUi) {
    val etiqueta = stringResource(R.string.analisis_sector_abreviado, sector.numero)
    val fecha = formatearFechaCorta(sector.pasadaInicioMs)
    val tiempo = formatearTiempo(sector.tiempoMs)
    val descripcion = stringResource(R.string.analisis_optimo_sector_descripcion, etiqueta, tiempo, fecha)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ALTO_FILA_OPTIMO)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.small)
                .padding(horizontal = Espacio.s, vertical = Espacio.xs)
                .testTag(ETIQUETA_OPTIMO_SECTOR_PREFIJO + sector.numero)
                .clearAndSetSemantics { contentDescription = descripcion },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(etiqueta, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(
            text = tiempo,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = Espacio.m),
        )
        Text(
            text = stringResource(R.string.analisis_optimo_sector_pasada, fecha),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun estiloCifrasOptimo() =
    TextStyle(
        fontSize = TAMANO_TOTAL_OPTIMO_SP.sp,
        fontWeight = FontWeight.Black,
        fontStyle = FontStyle.Italic,
        fontFeatureSettings = "tnum",
    )
