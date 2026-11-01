package app.trombito.rallycopilot.ui.copiloto

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import app.trombito.rallycopilot.ui.comun.BotonIcono
import app.trombito.rallycopilot.ui.comun.EstadoBotonIcono
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_MODO_BOTON = "copiloto-modo-boton"
const val ETIQUETA_MODO_PANEL = "copiloto-modo-panel"
const val ETIQUETA_MODO_PREFIJO = "copiloto-modo-opcion-"

private const val OPACIDAD_PANEL = 0.88f
private val RADIO_PANEL = 28.dp
private val LADO_ICONO = 28.dp

/**
 * Botón del panel negro inferior que muestra el modo actual del copiloto con su icono (sin texto) y abre el menú de
 * [PanelModoCopiloto]. TalkBack lee el nombre del modo y que al pulsarlo se cambia.
 */
@Composable
fun BotonModoCopiloto(
    modo: ModoCopiloto,
    abierto: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BotonIcono(
        descripcion = stringResource(R.string.copiloto_modo_boton, stringResource(modo.nombre())),
        onClick = onClick,
        modifier = modifier.testTag(ETIQUETA_MODO_BOTON),
        estado = EstadoBotonIcono(seleccionado = abierto),
    ) { IconoModo(modo) }
}

/** Menú pequeño con los cuatro modos como iconos; se pinta sobre el mapa, encima del panel inferior. */
@Composable
fun PanelModoCopiloto(
    modo: ModoCopiloto,
    onModo: (ModoCopiloto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val descripcion = stringResource(R.string.copiloto_modo_titulo)
    Surface(
        shape = RoundedCornerShape(RADIO_PANEL),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(OPACIDAD_PANEL),
        modifier = modifier.testTag(ETIQUETA_MODO_PANEL).semantics { contentDescription = descripcion },
    ) {
        Column(Modifier.padding(Espacio.xs), verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
            ModoCopiloto.entries.forEach { opcion ->
                val elegido = opcion == modo
                BotonIcono(
                    descripcion = stringResource(opcion.nombre()),
                    onClick = { onModo(opcion) },
                    modifier = Modifier.testTag(ETIQUETA_MODO_PREFIJO + opcion.name.lowercase()),
                    estado =
                        EstadoBotonIcono(
                            seleccionado = elegido,
                            descripcionEstado = if (elegido) stringResource(R.string.ajustes_seleccionado) else null,
                            rol = Role.RadioButton,
                        ),
                ) { IconoModo(opcion) }
            }
        }
    }
}

@Composable
internal fun IconoModo(
    modo: ModoCopiloto,
    modifier: Modifier = Modifier,
) {
    Icon(painterResource(modo.icono()), contentDescription = null, modifier = modifier.size(LADO_ICONO))
}
