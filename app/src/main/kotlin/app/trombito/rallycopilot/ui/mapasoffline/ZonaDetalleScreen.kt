package app.trombito.rallycopilot.ui.mapasoffline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
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
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.precarga.TeselasPrecarga
import app.trombito.rallycopilot.ui.comun.BotonIcono
import app.trombito.rallycopilot.ui.comun.ChipIconoWrc
import app.trombito.rallycopilot.ui.comun.DatoGrandeWrc
import app.trombito.rallycopilot.ui.comun.EstadoBotonIcono
import app.trombito.rallycopilot.ui.comun.FormatoEs
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.MapaConPanelWrc
import app.trombito.rallycopilot.ui.comun.ParteDatoWrc
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_ZONA_CONFIRMAR = "zona-confirmar"
const val ETIQUETA_ZONA_LIMPIAR = "zona-limpiar"
const val ETIQUETA_ZONA_ESTIMACION = "zona-estimacion"
const val ETIQUETA_ZONA_ZOOM_PREFIJO = "zona-zoom-"

private const val ESPACIO_MILES = ' '
private const val DIGITOS_GRUPO = 3

/** Callbacks de la pantalla de zona. */
data class AccionesZonaDetalle(
    val onVolver: () -> Unit = {},
    val onZoom: (Int) -> Unit = {},
    val onLimpiar: () -> Unit = {},
    val onConfirmar: () -> Unit = {},
)

/** «1 240»: miles separados por un espacio duro (en español el separador de 4 cifras no se pone solo). */
internal fun agruparMiles(valor: Long): String {
    val digitos = valor.coerceAtLeast(0L).toString()
    return digitos
        .reversed()
        .chunked(DIGITOS_GRUPO)
        .joinToString(ESPACIO_MILES.toString())
        .reversed()
}

/** Las cifras del dato grande: «1 240 teselas · 86 MB» (el tamaño se parte en número y unidad). */
internal fun partesEstimacion(
    teselas: Long,
    bytes: Long,
    unidadTeselas: String,
): List<ParteDatoWrc> {
    val tamano = FormatoEs.tamano(bytes)
    val corte = tamano.lastIndexOf(' ')
    val partesTamano =
        if (corte < 0) ParteDatoWrc(tamano) else ParteDatoWrc(tamano.substring(0, corte), tamano.substring(corte + 1))
    return listOf(ParteDatoWrc(agruparMiles(teselas), unidadTeselas), partesTamano)
}

/**
 * Elegir la zona a descargar sobre la plantilla «mapa con panel»: el [mapa] a pantalla completa (recibe los toques
 * de las esquinas) y, en el panel de carbón, el dato grande («1 240 teselas · 86 MB»), la ayuda, el zoom (18 o 19)
 * y los iconos de limpiar las esquinas y confirmar. Sin lógica: la pone el ViewModel.
 */
@Composable
fun ZonaDetalleScreen(
    estado: ZonaDetalleUiState,
    acciones: AccionesZonaDetalle,
    mapa: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    MapaConPanelWrc(
        titulo = stringResource(R.string.zona_titulo),
        onVolver = acciones.onVolver,
        mapa = mapa,
        modifier = modifier,
    ) {
        PanelZona(estado, acciones)
    }
}

@Composable
private fun PanelZona(
    estado: ZonaDetalleUiState,
    acciones: AccionesZonaDetalle,
) {
    val unidadTeselas = stringResource(R.string.zona_unidad_teselas)
    Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
        estado.estimacion?.let { e ->
            Box(Modifier.testTag(ETIQUETA_ZONA_ESTIMACION)) {
                DatoGrandeWrc(partesEstimacion(e.teselas, e.bytes, unidadTeselas))
            }
        }
        Text(
            stringResource(if (estado.personalizada) R.string.zona_ayuda_esquinas else R.string.zona_ayuda_vista),
            style = Ayuda,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!estado.cabe) {
            Text(
                stringResource(R.string.zona_no_cabe),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        FilaAcciones(estado, acciones)
    }
}

@Composable
private fun FilaAcciones(
    estado: ZonaDetalleUiState,
    acciones: AccionesZonaDetalle,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (nivel in TeselasPrecarga.ZOOM_MIN_ZONA..TeselasPrecarga.ZOOM_MAX_ZONA) {
            ChipIconoWrc(
                icono = Iconos.Lupa,
                texto = stringResource(R.string.zona_zoom, nivel),
                seleccionado = estado.zoom == nivel,
                onClick = { acciones.onZoom(nivel) },
                modifier = Modifier.testTag(ETIQUETA_ZONA_ZOOM_PREFIJO + nivel),
            )
        }
        BotonIcono(
            descripcion = stringResource(R.string.zona_limpiar),
            onClick = acciones.onLimpiar,
            modifier = Modifier.testTag(ETIQUETA_ZONA_LIMPIAR),
            estado = EstadoBotonIcono(habilitado = estado.personalizada),
        ) { Icon(Iconos.Borrar, contentDescription = null) }
        Box(Modifier.weight(1f))
        val descripcionConfirmar = stringResource(R.string.zona_descargar)
        FilledIconButton(
            onClick = acciones.onConfirmar,
            enabled = estado.puedeDescargar,
            modifier =
                Modifier
                    .size(56.dp)
                    .testTag(ETIQUETA_ZONA_CONFIRMAR)
                    .semantics { contentDescription = descripcionConfirmar },
        ) { Icon(Iconos.Hecho, contentDescription = null) }
    }
}
