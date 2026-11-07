package app.trombito.rallycopilot.ui.navegacion

import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.BotonIcono
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.LADO_TACTIL
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.LocalColoresRally
import app.trombito.rallycopilot.ui.theme.Medidas

const val ETIQUETA_EMPEZAR = "nav-empezar"
const val ETIQUETA_CANCELAR = "nav-cancelar"
const val ETIQUETA_TERMINAR = "nav-terminar"
const val ETIQUETA_BUSCAR_GUIANDO = "nav-buscar-guiando"
const val ETIQUETA_SALIR_MAPA = "nav-salir-mapa"

private val LADO_ACCION = 56.dp
private val ANCHO_EMPEZAR = 104.dp
private const val OPACIDAD_DESHABILITADO = 0.38f

/** Lupa de la hoja: guiando abre la búsqueda para añadir una parada o cambiar de destino; en libre va al campo. */
@Composable
fun BotonBuscarGuiando(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    etiqueta: String = ETIQUETA_BUSCAR_GUIANDO,
    @StringRes descripcion: Int = R.string.navegacion_ui_buscar_guiando,
) {
    BotonIcono(
        descripcion = stringResource(descripcion),
        onClick = onClick,
        modifier = modifier.testTag(etiqueta),
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

/**
 * Botón grande de la marca, «IR» con una flecha, de 56 dp de alto: empieza el guiado. Deshabilitado hasta tener ruta.
 */
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
        Row(
            modifier = Modifier.defaultMinSize(ANCHO_EMPEZAR, LADO_ACCION).padding(horizontal = Espacio.l),
            horizontalArrangement = Arrangement.spacedBy(Espacio.s, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.navegacion_ui_ir),
                style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.clearAndSetSemantics { },
            )
            Icon(Iconos.FlechaDerecha, contentDescription = null, modifier = Modifier.size(28.dp))
        }
    }
}

/**
 * ✕ de la hoja de conducción: termina el guiado (también cierra el aviso de llegada). Círculo de 48 dp con borde y sin
 * relleno (el rojo no es nunca un fondo); el borde es el de la hoja, no el de la marca. Con [armado] (esperando el
 * segundo toque que confirma terminar) el borde es ámbar y más grueso.
 */
@Composable
fun BotonTerminar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    armado: Boolean = false,
) {
    BotonCerrarHoja(
        stringResource(R.string.navegacion_terminar),
        onClick,
        modifier.testTag(ETIQUETA_TERMINAR),
        armado,
    )
}

/** ✕ de la hoja de conducción en el modo libre (sin destino): cierra el mapa, que para la sesión. */
@Composable
fun BotonSalirMapa(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    armado: Boolean = false,
) {
    BotonCerrarHoja(
        stringResource(R.string.navegacion_salir_mapa),
        onClick,
        modifier.testTag(ETIQUETA_SALIR_MAPA),
        armado,
    )
}

@Composable
private fun BotonCerrarHoja(
    descripcion: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    armado: Boolean = false,
) {
    val borde = if (armado) LocalColoresRally.current.aviso else MaterialTheme.colorScheme.outline
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(LADO_TACTIL)
                .clip(CircleShape)
                .border(if (armado) Medidas.bordeActivo * 2 else Medidas.bordeActivo, borde, CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = descripcion },
    ) {
        Icon(Iconos.Cerrar, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
    }
}
