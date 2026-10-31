package app.trombito.rallycopilot.ui.navegacion

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.maniobras.formatearDistanciaManiobra
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_VISTA_PREVIA = "nav-vista-previa"
const val ETIQUETA_RUTA_RAPIDA = "nav-ruta-rapida"
const val ETIQUETA_RUTA_SINUOSA = "nav-ruta-sinuosa"
const val ETIQUETA_SIN_POSICION = "nav-sin-posicion"

private const val OPACIDAD_SOBRE_MAPA = 0.94f
private const val OPACIDAD_NO_DISPONIBLE = 0.6f
private val ALTO_OPCION = 64.dp
private val ANCHO_MAXIMO_PANEL = 520.dp
private val GROSOR_SELECCION = 2.dp

/**
 * Panel inferior de la vista previa: las dos rutas (rayo = rápida, carretera con curvas = sinuosa) con tiempo y
 * distancia, cancelar y empezar. En apaisado todo va en una sola fila para ocupar poca altura.
 */
@Composable
fun PanelVistaPrevia(
    vista: VistaPrevia,
    acciones: AccionesNavegacion,
    horizontal: Boolean,
    modifier: Modifier = Modifier,
) {
    val descripcion = stringResource(R.string.navegacion_ui_rutas)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(OPACIDAD_SOBRE_MAPA),
        modifier =
            modifier
                .widthIn(max = ANCHO_MAXIMO_PANEL)
                .fillMaxWidth()
                .testTag(ETIQUETA_VISTA_PREVIA)
                .semantics { contentDescription = descripcion },
    ) {
        if (horizontal) {
            Row(
                modifier = Modifier.padding(Espacio.s),
                horizontalArrangement = Arrangement.spacedBy(Espacio.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BotonCancelarRuta(acciones.onCancelar)
                Opciones(vista, acciones.onPerfil, Modifier.weight(1f))
                BotonEmpezar(acciones.onEmpezar, habilitado = vista.puedeEmpezar)
            }
        } else {
            Column(Modifier.padding(Espacio.s), verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
                Opciones(vista, acciones.onPerfil, Modifier.fillMaxWidth())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BotonCancelarRuta(acciones.onCancelar)
                    BotonEmpezar(acciones.onEmpezar, habilitado = vista.puedeEmpezar)
                }
            }
        }
    }
}

@Composable
private fun Opciones(
    vista: VistaPrevia,
    onPerfil: (PerfilRuta) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (vista.sinPosicion) {
        Text(
            text = stringResource(R.string.navegacion_ui_sin_posicion),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = modifier.heightIn(min = ALTO_OPCION).padding(Espacio.s).testTag(ETIQUETA_SIN_POSICION),
        )
        return
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
        PerfilRuta.entries.forEach { perfil ->
            OpcionRutaTarjeta(
                perfil = perfil,
                opcion = vista.opciones[perfil] ?: OpcionRuta.Cargando,
                seleccionada = vista.perfil == perfil,
                onClick = { onPerfil(perfil) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun OpcionRutaTarjeta(
    perfil: PerfilRuta,
    opcion: OpcionRuta,
    seleccionada: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = MaterialTheme.colorScheme
    val rapida = perfil == PerfilRuta.MAS_RAPIDA
    val nombre = stringResource(if (rapida) R.string.navegacion_ui_ruta_rapida else R.string.navegacion_ui_ruta_sinuosa)
    val descripcion = stringResource(R.string.navegacion_ui_ruta_descripcion, nombre, resumenOpcion(opcion))
    val estado = if (seleccionada) stringResource(R.string.navegacion_ui_ruta_seleccionada) else null
    val disponible = opcion is OpcionRuta.Lista || opcion is OpcionRuta.Cargando
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (seleccionada) colores.primaryContainer else colores.surfaceContainerHighest,
        border = if (seleccionada) BorderStroke(GROSOR_SELECCION, colores.primary) else null,
        modifier =
            modifier
                .heightIn(min = ALTO_OPCION)
                .alpha(if (disponible) 1f else OPACIDAD_NO_DISPONIBLE)
                .testTag(if (rapida) ETIQUETA_RUTA_RAPIDA else ETIQUETA_RUTA_SINUOSA)
                .selectable(selected = seleccionada, enabled = opcion is OpcionRuta.Lista, role = Role.RadioButton) {
                    onClick()
                }.semantics(mergeDescendants = true) {
                    contentDescription = descripcion
                    estado?.let { stateDescription = it }
                },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Espacio.m, vertical = Espacio.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacio.s),
        ) {
            val icono: ImageVector = if (rapida) Iconos.Rayo else Iconos.Curvas
            Icon(icono, contentDescription = null, tint = if (seleccionada) colores.primary else colores.onSurface)
            ContenidoOpcion(opcion)
        }
    }
}

@Composable
private fun ContenidoOpcion(opcion: OpcionRuta) {
    when (opcion) {
        OpcionRuta.Cargando -> {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }

        is OpcionRuta.Lista -> {
            Column {
                Text(
                    text = formatearDuracionRuta(opcion.tiempoS),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = formatearDistanciaManiobra(opcion.distanciaM),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }

        OpcionRuta.SinDatos -> {
            TextoNoDisponible(R.string.navegacion_ui_ruta_sin_datos)
        }

        is OpcionRuta.Error -> {
            TextoNoDisponible(R.string.navegacion_ui_ruta_error)
        }
    }
}

@Composable
private fun TextoNoDisponible(
    @StringRes texto: Int,
) {
    Text(
        text = stringResource(texto),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** Lo que lee TalkBack tras el nombre de la ruta: «23 min, 18,4 km», «calculando», etc. */
@Composable
private fun resumenOpcion(opcion: OpcionRuta): String =
    when (opcion) {
        OpcionRuta.Cargando -> {
            stringResource(R.string.navegacion_ui_ruta_calculando)
        }

        is OpcionRuta.Lista -> {
            stringResource(
                R.string.navegacion_ui_ruta_datos,
                formatearDuracionRuta(opcion.tiempoS),
                formatearDistanciaManiobra(opcion.distanciaM),
            )
        }

        OpcionRuta.SinDatos -> {
            stringResource(R.string.navegacion_ui_ruta_sin_datos)
        }

        is OpcionRuta.Error -> {
            stringResource(R.string.navegacion_ui_ruta_error)
        }
    }
