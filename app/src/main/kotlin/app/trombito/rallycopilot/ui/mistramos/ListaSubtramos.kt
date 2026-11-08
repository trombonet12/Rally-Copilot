package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.FormatoEs
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.LocalColoresRally
import app.trombito.rallycopilot.ui.theme.Rotulo
import app.trombito.rallycopilot.ui.theme.Titulo
import app.trombito.rallycopilot.ui.tramos.colorDelta
import app.trombito.rallycopilot.ui.tramos.descripcionDelta
import app.trombito.rallycopilot.ui.tramos.descripcionTiempo
import app.trombito.rallycopilot.ui.tramos.formatearDelta
import app.trombito.rallycopilot.ui.tramos.formatearTiempo

const val ETIQUETA_SUBTRAMOS_TITULO = "detalle-subtramos-titulo"

/** Fila de un subtramo: `detalle-subtramo-0`, `detalle-subtramo-1`… */
const val ETIQUETA_SUBTRAMO_FILA_PREFIJO = "detalle-subtramo-"

private const val METROS_KM = 1000.0
private val ALTO_FILA_SUBTRAMO = 56.dp
private val CifraSubtramo = Rotulo.copy(fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = 0.sp)

/** La sección «Subtramos» del detalle: título y una tarjeta por subtramo; con la lista vacía no añade nada. */
fun LazyListScope.seccionSubtramos(subtramos: List<FilaSubtramo>) {
    if (subtramos.isEmpty()) return
    item(key = "subtramos-titulo") {
        Text(
            text = stringResource(R.string.detalle_subtramos_titulo),
            style = Titulo,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = Espacio.s).testTag(ETIQUETA_SUBTRAMOS_TITULO).semantics { heading() },
        )
    }
    items(subtramos, key = { "subtramo-${it.indice}" }) { fila -> TarjetaSubtramoGuardado(fila) }
}

/** Fecha, «km 1,2 → 3,6», tiempo y delta frente al mejor de entonces («Primera vez» si no lo había). */
@Composable
private fun TarjetaSubtramoGuardado(fila: FilaSubtramo) {
    val recursos = LocalContext.current.resources
    val desde = FormatoEs.decimal(fila.desdeM / METROS_KM)
    val hasta = FormatoEs.decimal(fila.hastaM / METROS_KM)
    val rango = stringResource(R.string.detalle_subtramo_rango, desde, hasta)
    val fecha = formatearFechaHora(fila.inicioMs)
    val cierre =
        fila.deltaMs?.let {
            recursos.getString(R.string.detalle_subtramo_delta_descripcion, descripcionDelta(recursos, it))
        } ?: recursos.getString(R.string.detalle_subtramo_primera_vez_descripcion)
    val descripcion =
        recursos.getString(
            R.string.detalle_subtramo_descripcion,
            desde,
            hasta,
            fecha,
            descripcionTiempo(recursos, fila.tiempoMs),
            cierre,
        )
    TarjetaWrc(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ALTO_FILA_SUBTRAMO)
                .testTag(ETIQUETA_SUBTRAMO_FILA_PREFIJO + fila.indice)
                .clearAndSetSemantics { contentDescription = descripcion },
        relleno = Espacio.m,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
            Column(Modifier.weight(1f)) {
                Text(text = fecha, style = Ayuda, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = rango,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = formatearTiempo(fila.tiempoMs),
                style = CifraSubtramo,
                color = MaterialTheme.colorScheme.onSurface,
            )
            DeltaSubtramo(fila.deltaMs)
        }
    }
}

@Composable
private fun DeltaSubtramo(deltaMs: Long?) {
    if (deltaMs == null) {
        Text(
            text = stringResource(R.string.detalle_subtramo_primera_vez),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Text(
            text = formatearDelta(deltaMs),
            style = MaterialTheme.typography.titleMedium,
            color = colorDelta(deltaMs, LocalColoresRally.current, MaterialTheme.colorScheme.onSurface),
        )
    }
}
