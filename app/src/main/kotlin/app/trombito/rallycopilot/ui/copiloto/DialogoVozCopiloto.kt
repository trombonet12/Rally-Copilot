package app.trombito.rallycopilot.ui.copiloto

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.ui.comun.BotonIcono
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_VOZ_DIALOGO = "copiloto-voz-dialogo"
const val ETIQUETA_VOZ_ELEGIR = "copiloto-voz-elegir"
const val ETIQUETA_VOZ_OPCION_PREFIJO = "copiloto-voz-opcion-"
const val ETIQUETA_VOZ_PROBAR_PREFIJO = "copiloto-voz-probar-"

private val ALTO_MINIMO_TACTIL = 48.dp
private val ALTO_FILA = 56.dp

/**
 * Diálogo del primer uso: «Elige la voz del copiloto». Cada voz se puede probar con ▶ antes de elegirla. No se cierra
 * tocando fuera ni sin elegir: solo «Elegir» guarda la voz y atrás ([onPosponer]) lo deja para la próxima apertura.
 */
@Composable
fun DialogoVozCopiloto(
    onElegir: (VozCopiloto) -> Unit,
    onProbar: (VozCopiloto) -> Unit,
    onPosponer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var elegida by remember { mutableStateOf(VozCopiloto.entries.first()) }
    AlertDialog(
        onDismissRequest = onPosponer,
        properties = DialogProperties(dismissOnClickOutside = false),
        modifier = modifier.testTag(ETIQUETA_VOZ_DIALOGO),
        title = { Text(stringResource(R.string.copiloto_voz_dialogo_titulo)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
                Text(
                    text = stringResource(R.string.copiloto_voz_dialogo_texto),
                    style = MaterialTheme.typography.bodyMedium,
                )
                VozCopiloto.entries.forEach { voz ->
                    FilaVozCopiloto(voz, voz == elegida, onElegir = { elegida = voz }, onProbar = { onProbar(voz) })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onElegir(elegida) },
                modifier = Modifier.heightIn(min = ALTO_MINIMO_TACTIL).testTag(ETIQUETA_VOZ_ELEGIR),
            ) { Text(stringResource(R.string.copiloto_voz_elegir)) }
        },
    )
}

/**
 * Una voz: radio con su nombre amable («Voz 1») y, aparte, el botón ▶ que la prueba. Probar no la elige. Lo
 * comparten el diálogo del primer uso y Ajustes.
 */
@Composable
fun FilaVozCopiloto(
    voz: VozCopiloto,
    seleccionada: Boolean,
    onElegir: () -> Unit,
    onProbar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nombre = stringResource(voz.nombre())
    val clave = voz.name.lowercase()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .testTag(ETIQUETA_VOZ_OPCION_PREFIJO + clave)
                    .heightIn(min = ALTO_FILA)
                    .selectable(selected = seleccionada, role = Role.RadioButton, onClick = onElegir)
                    .padding(horizontal = Espacio.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacio.m),
        ) {
            RadioButton(selected = seleccionada, onClick = null)
            Text(text = nombre, style = MaterialTheme.typography.titleMedium)
        }
        BotonIcono(
            descripcion = stringResource(R.string.copiloto_voz_probar, nombre),
            onClick = onProbar,
            modifier = Modifier.testTag(ETIQUETA_VOZ_PROBAR_PREFIJO + clave),
        ) { Icon(Iconos.Empezar, contentDescription = null) }
    }
}
