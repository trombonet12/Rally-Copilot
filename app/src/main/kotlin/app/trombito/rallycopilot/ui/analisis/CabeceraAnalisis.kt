package app.trombito.rallycopilot.ui.analisis

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.BotonVolver
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.mistramos.formatearFechaCorta
import app.trombito.rallycopilot.ui.mistramos.formatearFechaHora
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.tramos.PildoraDelta
import app.trombito.rallycopilot.ui.tramos.formatearTiempo

const val ETIQUETA_ANALISIS_TITULO = "analisis-titulo"
const val ETIQUETA_ANALISIS_FECHA = "analisis-fecha"
const val ETIQUETA_ANALISIS_TOTAL = "analisis-total"
const val ETIQUETA_ANALISIS_DELTA = "analisis-delta"
const val ETIQUETA_ANALISIS_COMPARADA = "analisis-comparada"
const val ETIQUETA_COMPARAR_NINGUNA = "analisis-comparar-ninguna"
const val ETIQUETA_COMPARAR_PREFIJO = "analisis-comparar-"

private val ALTO_BARRA = 56.dp

/** Barra superior: volver, nombre del tramo y fecha de la pasada analizada. */
@Composable
fun BarraAnalisis(
    estado: AnalisisUiState,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = ALTO_BARRA).padding(horizontal = Espacio.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BotonVolver(onVolver)
        Column(Modifier.weight(1f).padding(horizontal = Espacio.xs)) {
            Text(
                text = (estado.tramoNombre ?: stringResource(R.string.analisis_titulo)).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(ETIQUETA_ANALISIS_TITULO).semantics { heading() },
            )
            if (estado.analisis != null) {
                Text(
                    text = formatearFechaHora(estado.inicioMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(ETIQUETA_ANALISIS_FECHA),
                )
            }
        }
    }
}

/** Tiempo total grande y su delta contra la pasada comparada (pastilla verde o roja), con el tiempo de la comparada. */
@Composable
fun ResumenAnalisis(
    estado: AnalisisUiState,
    modifier: Modifier = Modifier,
) {
    val analisis = estado.analisis ?: return
    val comparada = estado.comparada
    val delta = analisis.deltaTotalMs
    val descripcion =
        stringResource(R.string.analisis_resumen_descripcion, formatearTiempo(analisis.totalMs)) +
            if (delta !=
                null
            ) {
                " " + stringResource(R.string.analisis_resumen_descripcion_delta, formatearDeltaSegundos(delta))
            } else {
                ""
            }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = Espacio.l)
                .semantics(mergeDescendants = true) { contentDescription = descripcion },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        Text(
            text = formatearTiempo(analisis.totalMs),
            style = estiloCifras(TAMANO_TOTAL_SP),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag(ETIQUETA_ANALISIS_TOTAL),
        )
        if (delta != null) PildoraDelta(delta, TAMANO_DELTA_SP.sp, 72.dp, 5.dp, ETIQUETA_ANALISIS_DELTA)
        Text(
            text =
                if (comparada != null) {
                    stringResource(R.string.analisis_contra, formatearTiempo(comparada.totalMs))
                } else {
                    stringResource(
                        if (estado.opciones.isEmpty()) R.string.analisis_sin_otras else R.string.analisis_sin_comparar,
                    )
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).testTag(ETIQUETA_ANALISIS_COMPARADA),
        )
    }
}

/** «Comparar con»: una ficha por cada otra pasada (la mejor primero) y «Ninguna»; la elegida va marcada. */
@Composable
fun SelectorComparada(
    estado: AnalisisUiState,
    onComparar: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (estado.opciones.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
        Text(
            text = stringResource(R.string.analisis_comparar_con).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Espacio.s),
        ) {
            FilterChip(
                selected = estado.comparadaId == null,
                onClick = { onComparar(null) },
                label = { Text(stringResource(R.string.analisis_comparar_ninguna)) },
                modifier = Modifier.testTag(ETIQUETA_COMPARAR_NINGUNA),
            )
            val mejor = stringResource(R.string.mistramos_mejor)
            estado.opciones.forEach { opcion ->
                FilterChip(
                    selected = estado.comparadaId == opcion.id,
                    onClick = { onComparar(opcion.id) },
                    label = { Text("${formatearTiempo(opcion.totalMs)} · ${formatearFechaCorta(opcion.inicioMs)}") },
                    leadingIcon =
                        if (opcion.esMejor) {
                            { Icon(Iconos.Estrella, mejor, Modifier.size(FilterChipDefaults.IconSize)) }
                        } else {
                            null
                        },
                    modifier = Modifier.testTag(ETIQUETA_COMPARAR_PREFIJO + opcion.id),
                )
            }
        }
    }
}

private const val TAMANO_TOTAL_SP = 36
private const val TAMANO_DELTA_SP = 24

private fun estiloCifras(tamanoSp: Int) =
    TextStyle(
        fontSize = tamanoSp.sp,
        fontWeight = FontWeight.Black,
        fontStyle = FontStyle.Italic,
        fontFeatureSettings = "tnum",
    )
