package app.trombito.rallycopilot.ui.mapasoffline

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.precarga.TeselasPrecarga
import app.trombito.rallycopilot.ui.comun.BarraSuperior
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_ZONA_CONFIRMAR = "zona-confirmar"
const val ETIQUETA_ZONA_LIMPIAR = "zona-limpiar"
const val ETIQUETA_ZONA_ESTIMACION = "zona-estimacion"
const val ETIQUETA_ZONA_ZOOM_PREFIJO = "zona-zoom-"

/** Callbacks de la pantalla de zona. */
data class AccionesZonaDetalle(
    val onVolver: () -> Unit = {},
    val onZoom: (Int) -> Unit = {},
    val onLimpiar: () -> Unit = {},
    val onConfirmar: () -> Unit = {},
)

/**
 * Elegir la zona a descargar: el [mapa] a pantalla completa (recibe los toques de las esquinas) y, abajo, el zoom
 * (18 o 19), el tamaño estimado y los iconos de limpiar las esquinas y confirmar. Sin lógica: la pone el ViewModel.
 */
@Composable
fun ZonaDetalleScreen(
    estado: ZonaDetalleUiState,
    acciones: AccionesZonaDetalle,
    mapa: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        mapa(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = FONDO_PANEL),
                modifier = Modifier.fillMaxWidth(),
            ) { BarraSuperior(titulo = stringResource(R.string.zona_titulo), onVolver = acciones.onVolver) }
            Box(Modifier.weight(1f))
            PanelZona(estado, acciones)
        }
    }
}

@Composable
private fun PanelZona(
    estado: ZonaDetalleUiState,
    acciones: AccionesZonaDetalle,
) {
    val contexto = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = FONDO_PANEL),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(Espacio.s),
    ) {
        Column(Modifier.padding(Espacio.m), verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
            Text(
                stringResource(if (estado.personalizada) R.string.zona_ayuda_esquinas else R.string.zona_ayuda_vista),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            estado.estimacion?.let { e ->
                Text(
                    stringResource(
                        R.string.zona_estimacion,
                        e.teselas,
                        Formatter.formatShortFileSize(contexto, e.bytes),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag(ETIQUETA_ZONA_ESTIMACION),
                )
            }
            if (!estado.cabe) {
                Text(
                    stringResource(R.string.zona_no_cabe),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Espacio.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (nivel in TeselasPrecarga.ZOOM_MIN_ZONA..TeselasPrecarga.ZOOM_MAX_ZONA) {
                    FilterChip(
                        selected = estado.zoom == nivel,
                        onClick = { acciones.onZoom(nivel) },
                        label = { Text(stringResource(R.string.zona_zoom, nivel)) },
                        modifier = Modifier.testTag(ETIQUETA_ZONA_ZOOM_PREFIJO + nivel),
                    )
                }
                IconButton(
                    onClick = acciones.onLimpiar,
                    enabled = estado.personalizada,
                    modifier = Modifier.size(48.dp).testTag(ETIQUETA_ZONA_LIMPIAR),
                ) { Icon(Iconos.Borrar, stringResource(R.string.zona_limpiar)) }
                Box(Modifier.weight(1f))
                FilledIconButton(
                    onClick = acciones.onConfirmar,
                    enabled = estado.puedeDescargar,
                    modifier = Modifier.size(56.dp).testTag(ETIQUETA_ZONA_CONFIRMAR),
                ) { Icon(Iconos.Hecho, stringResource(R.string.zona_descargar)) }
            }
        }
    }
}

private const val FONDO_PANEL = 0.92f
