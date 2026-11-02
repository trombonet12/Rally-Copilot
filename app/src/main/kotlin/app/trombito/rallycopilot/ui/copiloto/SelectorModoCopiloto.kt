package app.trombito.rallycopilot.ui.copiloto

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import app.trombito.rallycopilot.ui.comun.BotonIcono
import app.trombito.rallycopilot.ui.comun.EstadoBotonIcono
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_MODO_BOTON = "copiloto-modo-boton"
const val ETIQUETA_MODO_PREFIJO = "copiloto-modo-opcion-"
const val ETIQUETA_MODO_SELECTOR = "copiloto-modo-selector"
const val ETIQUETA_MODO_DETALLE = "copiloto-modo-detalle"

private val LADO_ICONO = 28.dp

/**
 * El botón del copiloto en el panel negro inferior: muestra el modo actual con su icono (sin texto) y abre la hoja con
 * el selector de modo y los ajustes rápidos. TalkBack lee el nombre del modo y que al pulsarlo se abre la hoja.
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

/**
 * Los cuatro modos como iconos en una fila (arriba de la hoja del copiloto) y, debajo, el nombre del elegido con lo
 * que hace: el cambio se ve y se lee al tocar.
 */
@Composable
fun SelectorModoCopiloto(
    modo: ModoCopiloto,
    onModo: (ModoCopiloto) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.testTag(ETIQUETA_MODO_SELECTOR), verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s), verticalAlignment = Alignment.CenterVertically) {
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
        val nombre = stringResource(modo.nombre())
        val detalle = stringResource(modo.detalle())
        Text(
            text = nombre,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = detalle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(ETIQUETA_MODO_DETALLE),
        )
    }
}

@Composable
internal fun IconoModo(
    modo: ModoCopiloto,
    modifier: Modifier = Modifier,
) {
    Icon(painterResource(modo.icono()), contentDescription = null, modifier = modifier.size(LADO_ICONO))
}
