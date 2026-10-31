package app.trombito.rallycopilot.ui.navegacion

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.ui.comun.BotonIcono
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_LLEGADA = "nav-llegada"
const val ETIQUETA_ERROR = "nav-error"
const val ETIQUETA_ERROR_CERRAR = "nav-error-cerrar"

private const val OPACIDAD_SOBRE_MAPA = 0.94f
private val ANCHO_MAXIMO_AVISO = 520.dp

/** Barra «¿A dónde?» con el botón de conducir sin destino a su lado; va dentro de la fila de la cabecera. */
@Composable
fun RowScope.CabeceraBusqueda(
    estado: NavegacionUiState,
    acciones: AccionesNavegacion,
) {
    Spacer(Modifier.width(Espacio.s))
    BarraDestino(
        texto = estado.texto,
        onTexto = acciones.onTexto,
        onLimpiar = acciones.onCancelar,
        modifier = Modifier.weight(1f),
    )
    // Con una vista previa abierta ya hay destino: el modo libre no pinta nada.
    if (estado.vistaPrevia == null) {
        Spacer(Modifier.width(Espacio.s))
        BotonConducirLibre(acciones.onConducirLibre)
    }
}

/** Lo que va abajo en el mapa según la fase: vista previa, aviso de llegada o aviso de error. */
@Composable
fun PanelInferiorNavegacion(
    estado: NavegacionUiState,
    acciones: AccionesNavegacion,
    horizontal: Boolean,
    modifier: Modifier = Modifier,
) {
    when (val control = estado.control) {
        is EstadoNavegacion.Inactiva -> {
            estado.vistaPrevia?.let { PanelVistaPrevia(it, acciones, horizontal, modifier) }
        }

        is EstadoNavegacion.Llegada -> {
            AvisoNavegacion(
                texto = stringResource(R.string.navegacion_ui_llegada, control.destino.nombre),
                etiqueta = ETIQUETA_LLEGADA,
                modifier = modifier,
            ) { BotonTerminar(acciones.onTerminar) }
        }

        is EstadoNavegacion.Error -> {
            AvisoNavegacion(texto = control.motivo, etiqueta = ETIQUETA_ERROR, modifier = modifier) {
                BotonIcono(
                    descripcion = stringResource(R.string.mapa_aviso_cerrar),
                    onClick = acciones.onTerminar,
                    modifier = Modifier.testTag(ETIQUETA_ERROR_CERRAR),
                ) { Icon(Iconos.Cerrar, contentDescription = null) }
            }
        }

        else -> {
            Unit
        }
    }
}

@Composable
private fun AvisoNavegacion(
    texto: String,
    etiqueta: String,
    modifier: Modifier = Modifier,
    accion: @Composable () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(OPACIDAD_SOBRE_MAPA),
        modifier = modifier.widthIn(max = ANCHO_MAXIMO_AVISO).fillMaxWidth().testTag(etiqueta),
    ) {
        Row(
            modifier = Modifier.padding(Espacio.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = texto,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            )
            accion()
        }
    }
}
