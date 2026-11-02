package app.trombito.rallycopilot.ui.perfil

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.analisis.EntradaHistorial
import app.trombito.rallycopilot.motor.analisis.PerfilPiloto
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.mistramos.formatearDistanciaTramo
import app.trombito.rallycopilot.ui.mistramos.formatearFechaHora
import app.trombito.rallycopilot.ui.mistramos.formatearVelocidadKmh
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.LocalColoresRally
import app.trombito.rallycopilot.ui.tramos.colorDelta
import app.trombito.rallycopilot.ui.tramos.formatearDelta
import app.trombito.rallycopilot.ui.tramos.formatearTiempo
import app.trombito.rallycopilot.motor.analisis.ResumenTramo as ResumenTramoPerfil

const val ETIQUETA_MEJOR_PREFIJO = "perfil-mejor-"
const val ETIQUETA_HISTORIAL_PREFIJO = "perfil-historial-"
const val ETIQUETA_EVOLUCION_TITULO = "perfil-evolucion-titulo"

private val ALTO_FILA = 56.dp
private val GROSOR_SELECCION = 2.dp

/** Filas de «Mejores tiempos»: una por tramo; tocarla elige el tramo de la evolución. */
internal fun LazyListScope.mejoresTiempos(
    estado: PerfilUiState,
    acciones: AccionesPerfil,
) {
    item { Aviso(R.string.perfil_mejores_ayuda) }
    items(estado.mejores, key = { it.tramoId }) { tramo ->
        FilaMejor(tramo, tramo.tramoId == estado.seleccionado?.tramoId) { acciones.onSeleccionar(tramo.tramoId) }
    }
}

@Composable
private fun FilaMejor(
    tramo: ResumenTramoPerfil,
    seleccionado: Boolean,
    onClick: () -> Unit,
) {
    val colores = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        color = if (seleccionado) colores.primaryContainer else colores.surfaceContainerHigh,
        border = if (seleccionado) BorderStroke(GROSOR_SELECCION, colores.primary) else null,
        shape = MaterialTheme.shapes.small,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ALTO_FILA)
                .testTag(ETIQUETA_MEJOR_PREFIJO + tramo.tramoId)
                .semantics { selected = seleccionado },
    ) {
        Row(Modifier.padding(Espacio.m), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                Text(
                    text = tramo.nombre,
                    style = MaterialTheme.typography.titleMedium,
                    color = colores.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = detalleTramo(tramo),
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.onSurfaceVariant,
                )
            }
            Text(
                text = tramo.mejorMs?.let { formatearTiempo(it) }.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = colores.primary,
            )
        }
    }
}

@Composable
private fun detalleTramo(tramo: ResumenTramoPerfil): String {
    val pasadas = pluralStringResource(R.plurals.mistramos_pasadas, tramo.numeroPasadas, tramo.numeroPasadas)
    val base = "${formatearDistanciaTramo(tramo.distanciaM)} · $pasadas"
    return tramo.velMejorKmh?.let { "$base · ${formatearVelocidadKmh(it)}" } ?: base
}

/** Gráfico de evolución del tramo elegido. */
@Composable
internal fun EvolucionTramo(estado: PerfilUiState) {
    val tramo = estado.seleccionado ?: return
    Column(verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
        Text(
            text = tramo.nombre,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag(ETIQUETA_EVOLUCION_TITULO),
        )
        GraficoEvolucion(tramo.evolucion)
        LeyendaRecords()
    }
}

/** Historial reciente: tiempo y diferencia contra el mejor tiempo del tramo. */
internal fun LazyListScope.historial(perfil: PerfilPiloto) {
    if (perfil.historial.isEmpty()) {
        item { Aviso(R.string.perfil_historial_vacio) }
    }
    items(perfil.historial, key = { it.pasadaId }) { FilaHistorial(it) }
}

@Composable
private fun FilaHistorial(entrada: EntradaHistorial) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().testTag(ETIQUETA_HISTORIAL_PREFIJO + entrada.pasadaId),
    ) {
        Row(Modifier.padding(Espacio.m), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = entrada.nombre,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatearFechaHora(entrada.fechaMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatearTiempo(entrada.totalMs),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                DiferenciaHistorial(entrada)
            }
        }
    }
}

@Composable
private fun DiferenciaHistorial(entrada: EntradaHistorial) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (entrada.esRecord) {
            Icon(Iconos.Estrella, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(R.string.perfil_record).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = Espacio.s),
            )
        }
        Text(
            text =
                if (entrada.deltaMejorMs == 0L) {
                    stringResource(R.string.mistramos_mejor).uppercase()
                } else {
                    formatearDelta(entrada.deltaMejorMs)
                },
            style = MaterialTheme.typography.labelLarge,
            color = colorDelta(entrada.deltaMejorMs, LocalColoresRally.current, MaterialTheme.colorScheme.primary),
        )
    }
}
