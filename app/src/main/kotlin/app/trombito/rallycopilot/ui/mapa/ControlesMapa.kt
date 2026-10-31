package app.trombito.rallycopilot.ui.mapa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.ajustes.Orientacion
import app.trombito.rallycopilot.domain.datos.CapaMapa
import app.trombito.rallycopilot.ui.comun.BotonIcono
import app.trombito.rallycopilot.ui.comun.EstadoBotonIcono
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_CAPAS_BOTON = "mapa-capas-boton"
const val ETIQUETA_CAPAS_PANEL = "mapa-capas-panel"
const val ETIQUETA_RELIEVE = "mapa-relieve"
const val ETIQUETA_ORIENTACION = "mapa-orientacion"

private const val OPACIDAD_PANEL = 0.88f
private val RADIO_PANEL = 28.dp

/**
 * Controles redondos del mapa, solo con iconos: capas (que despliega base Mapa/Satélite e interruptor de relieve) y
 * orientación (rumbo arriba / norte arriba).
 */
@Composable
internal fun ControlesMapa(
    estado: MapaUiState,
    acciones: AccionesMapa,
    modifier: Modifier = Modifier,
) {
    var capasAbiertas by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        BotonIcono(
            descripcion = stringResource(R.string.mapa_capas_boton),
            onClick = { capasAbiertas = !capasAbiertas },
            modifier = Modifier.testTag(ETIQUETA_CAPAS_BOTON),
            estado = EstadoBotonIcono(seleccionado = capasAbiertas),
        ) { Icon(Iconos.Capas, contentDescription = null) }
        if (capasAbiertas) PanelCapas(estado, acciones)
        BotonOrientacion(estado.orientacion, acciones.onOrientacion)
    }
}

@Composable
private fun PanelCapas(
    estado: MapaUiState,
    acciones: AccionesMapa,
) {
    val descripcion = stringResource(R.string.mapa_capa_selector)
    Surface(
        shape = RoundedCornerShape(RADIO_PANEL),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(OPACIDAD_PANEL),
        modifier = Modifier.testTag(ETIQUETA_CAPAS_PANEL).semantics { contentDescription = descripcion },
    ) {
        Column(Modifier.padding(Espacio.xs), verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
            BotonCapa(CapaMapa.VECTORIAL, estado, acciones.onCapa)
            BotonCapa(CapaMapa.SATELITE, estado, acciones.onCapa)
            BotonRelieve(estado.relieve, acciones.onRelieve)
        }
    }
}

@Composable
private fun BotonCapa(
    capa: CapaMapa,
    estado: MapaUiState,
    onCapa: (CapaMapa) -> Unit,
) {
    val satelite = capa == CapaMapa.SATELITE
    val elegida = estado.capa == capa
    val disponible = !satelite || estado.puedeSatelite
    val textoEstado =
        when {
            !disponible -> stringResource(R.string.mapa_capa_satelite_no_instalado)
            elegida -> stringResource(R.string.mapa_estado_seleccionado)
            else -> null
        }
    BotonIcono(
        descripcion = stringResource(if (satelite) R.string.mapa_capa_satelite else R.string.mapa_capa_mapa),
        onClick = { onCapa(capa) },
        modifier = Modifier.testTag(if (satelite) ETIQUETA_CAPA_SATELITE else ETIQUETA_CAPA_MAPA),
        estado =
            EstadoBotonIcono(
                seleccionado = elegida,
                habilitado = disponible,
                descripcionEstado = textoEstado,
                rol = Role.RadioButton,
            ),
    ) { Icon(if (satelite) Iconos.Satelite else Iconos.Mapa, contentDescription = null) }
}

@Composable
private fun BotonRelieve(
    activo: Boolean,
    onRelieve: (Boolean) -> Unit,
) {
    BotonIcono(
        descripcion = stringResource(R.string.mapa_relieve),
        onClick = { onRelieve(!activo) },
        modifier = Modifier.testTag(ETIQUETA_RELIEVE),
        estado =
            EstadoBotonIcono(
                seleccionado = activo,
                descripcionEstado =
                    stringResource(if (activo) R.string.mapa_estado_activado else R.string.mapa_estado_desactivado),
                rol = Role.Switch,
            ),
    ) { Icon(Iconos.Relieve, contentDescription = null) }
}

@Composable
private fun BotonOrientacion(
    orientacion: Orientacion,
    onOrientacion: (Orientacion) -> Unit,
) {
    val rumbo = orientacion == Orientacion.RUMBO
    val icono: ImageVector = if (rumbo) Iconos.Rumbo else Iconos.Brujula
    BotonIcono(
        descripcion = stringResource(if (rumbo) R.string.mapa_orientacion_rumbo else R.string.mapa_orientacion_norte),
        onClick = { onOrientacion(if (rumbo) Orientacion.NORTE else Orientacion.RUMBO) },
        modifier = Modifier.testTag(ETIQUETA_ORIENTACION),
    ) { Icon(icono, contentDescription = null) }
}
