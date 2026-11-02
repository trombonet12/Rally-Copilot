package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.tramos.ResumenTramo

const val ETIQUETA_RENOMBRAR_CAMPO = "renombrar-campo"
const val ETIQUETA_RENOMBRAR_GUARDAR = "renombrar-guardar"
const val ETIQUETA_RENOMBRAR_CANCELAR = "renombrar-cancelar"

/**
 * Diálogo para poner nombre a un tramo. El campo empieza con el nombre propio (vacío si el tramo usa el automático,
 * que se ve de sugerencia): dejarlo vacío devuelve el nombre automático.
 */
@Composable
fun DialogoRenombrarTramo(
    tramo: ResumenTramo,
    onGuardar: (String) -> Unit,
    onCancelar: () -> Unit,
) {
    var texto by rememberSaveable(tramo.id) { mutableStateOf(tramo.nombrePropio.orEmpty()) }
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(stringResource(R.string.mistramos_renombrar_titulo)) },
        text = {
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it },
                singleLine = true,
                label = { Text(stringResource(R.string.mistramos_renombrar_campo)) },
                placeholder = { Text(tramo.nombre) },
                supportingText = { Text(stringResource(R.string.mistramos_renombrar_ayuda)) },
                modifier = Modifier.testTag(ETIQUETA_RENOMBRAR_CAMPO),
            )
        },
        confirmButton = {
            TextButton(onClick = { onGuardar(texto) }, modifier = Modifier.testTag(ETIQUETA_RENOMBRAR_GUARDAR)) {
                Text(stringResource(R.string.mistramos_renombrar_guardar))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar, modifier = Modifier.testTag(ETIQUETA_RENOMBRAR_CANCELAR)) {
                Text(stringResource(R.string.mistramos_renombrar_cancelar))
            }
        },
    )
}
