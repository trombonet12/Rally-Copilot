package app.trombito.rallycopilot.ui.navegacion

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.BotonIcono
import app.trombito.rallycopilot.ui.comun.Iconos

const val ETIQUETA_EMPEZAR = "nav-empezar"
const val ETIQUETA_CANCELAR = "nav-cancelar"
const val ETIQUETA_TERMINAR = "nav-terminar"
const val ETIQUETA_BUSCAR_GUIANDO = "nav-buscar-guiando"

private val LADO_ACCION = 56.dp
private val ANCHO_EMPEZAR = 88.dp
private const val OPACIDAD_DESHABILITADO = 0.38f

/** Lupa del panel mientras se guía: abre la búsqueda para añadir una parada o cambiar de destino. */
@Composable
fun BotonBuscarGuiando(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BotonIcono(
        descripcion = stringResource(R.string.navegacion_ui_buscar_guiando),
        onClick = onClick,
        modifier = modifier.testTag(ETIQUETA_BUSCAR_GUIANDO),
    ) { Icon(Iconos.Lupa, contentDescription = null) }
}

/** Cancela la vista previa de rutas. */
@Composable
fun BotonCancelarRuta(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BotonIcono(
        descripcion = stringResource(R.string.navegacion_ui_cancelar),
        onClick = onClick,
        modifier = modifier.testTag(ETIQUETA_CANCELAR),
    ) { Icon(Iconos.Cerrar, contentDescription = null) }
}

/** Botón grande de la marca con el triángulo de play: empieza el guiado. Deshabilitado hasta tener ruta. */
@Composable
fun BotonEmpezar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
) {
    val descripcion = stringResource(R.string.navegacion_ui_empezar)
    Surface(
        shape = RoundedCornerShape(LADO_ACCION / 2),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier =
            modifier
                .defaultMinSize(ANCHO_EMPEZAR, LADO_ACCION)
                .alpha(if (habilitado) 1f else OPACIDAD_DESHABILITADO)
                .testTag(ETIQUETA_EMPEZAR)
                .clickable(enabled = habilitado, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = descripcion },
    ) {
        Box(Modifier.defaultMinSize(ANCHO_EMPEZAR, LADO_ACCION), contentAlignment = Alignment.Center) {
            Icon(Iconos.Empezar, contentDescription = null, modifier = Modifier.size(32.dp))
        }
    }
}

/** Bandera a cuadros: termina la conducción (también cierra el aviso de llegada). */
@Composable
fun BotonTerminar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val descripcion = stringResource(R.string.navegacion_terminar)
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier =
            modifier
                .defaultMinSize(LADO_ACCION, LADO_ACCION)
                .testTag(ETIQUETA_TERMINAR)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = descripcion },
    ) {
        Box(Modifier.defaultMinSize(LADO_ACCION, LADO_ACCION), contentAlignment = Alignment.Center) {
            Icon(Iconos.Bandera, contentDescription = null)
        }
    }
}
