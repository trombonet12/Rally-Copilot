package app.trombito.rallycopilot.ui.analisis

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.ChipIconoWrc
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.mistramos.formatearFechaCorta
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Tiempo
import app.trombito.rallycopilot.ui.tramos.PildoraDelta
import app.trombito.rallycopilot.ui.tramos.formatearTiempo

const val ETIQUETA_ANALISIS_TOTAL = "analisis-total"
const val ETIQUETA_ANALISIS_DELTA = "analisis-delta"
const val ETIQUETA_ANALISIS_COMPARADA = "analisis-comparada"
const val ETIQUETA_COMPARAR_NINGUNA = "analisis-comparar-ninguna"
const val ETIQUETA_COMPARAR_PREFIJO = "analisis-comparar-"

private const val TAMANO_DELTA_SP = 24

/** Tiempo total grande y su delta contra la pasada comparada (pastilla verde o roja), con el tiempo de la comparada. */
@Composable
fun ResumenAnalisis(
    estado: AnalisisUiState,
    modifier: Modifier = Modifier,
    onComparar: (Long?) -> Unit = {},
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
            style = Tiempo,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag(ETIQUETA_ANALISIS_TOTAL),
        )
        if (delta != null) PildoraDelta(delta, TAMANO_DELTA_SP.sp, 72.dp, 5.dp, ETIQUETA_ANALISIS_DELTA)
        if (comparada != null) {
            Text(
                text = stringResource(R.string.analisis_contra, formatearTiempo(comparada.totalMs)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).testTag(ETIQUETA_ANALISIS_COMPARADA),
            )
        } else {
            // Con otras pasadas la ficha es un botón que compara con la mejor; sin ellas solo informa.
            val mejor = estado.opciones.firstOrNull { it.esMejor } ?: estado.opciones.firstOrNull()
            FichaSinComparar(if (mejor != null) ({ onComparar(mejor.id) }) else null)
        }
    }
}

/**
 * Ficha «Comparar» con icono (compara con la mejor pasada de un toque, de 48 dp de alto) cuando hay otras pasadas, o la
 * ficha informativa «Sin comparar» cuando no hay contra qué medirse ([onComparar] nulo).
 */
@Composable
private fun FichaSinComparar(onComparar: (() -> Unit)?) {
    ChipIconoWrc(
        icono = Iconos.Grafico,
        texto = stringResource(if (onComparar != null) R.string.analisis_comparar else R.string.analisis_sin_comparar),
        modifier = Modifier.testTag(ETIQUETA_ANALISIS_COMPARADA),
        onClick = onComparar,
    )
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
            ChipIconoWrc(
                icono = Iconos.Cerrar,
                texto = stringResource(R.string.analisis_comparar_ninguna),
                seleccionado = estado.comparadaId == null,
                onClick = { onComparar(null) },
                modifier = Modifier.testTag(ETIQUETA_COMPARAR_NINGUNA),
            )
            val mejor = stringResource(R.string.mistramos_mejor)
            estado.opciones.forEach { opcion ->
                val texto = "${formatearTiempo(opcion.totalMs)} · ${formatearFechaCorta(opcion.inicioMs)}"
                ChipIconoWrc(
                    icono = if (opcion.esMejor) Iconos.Estrella else Iconos.Cronometro,
                    texto = texto,
                    seleccionado = estado.comparadaId == opcion.id,
                    onClick = { onComparar(opcion.id) },
                    modifier =
                        Modifier
                            .testTag(ETIQUETA_COMPARAR_PREFIJO + opcion.id)
                            .then(
                                if (opcion.esMejor) {
                                    Modifier.semantics { contentDescription = "$mejor: $texto" }
                                } else {
                                    Modifier
                                },
                            ),
                )
            }
        }
    }
}
