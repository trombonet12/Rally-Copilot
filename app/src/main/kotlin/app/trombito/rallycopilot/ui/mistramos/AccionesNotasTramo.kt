package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.theme.Cuerpo
import app.trombito.rallycopilot.ui.theme.Espacio

private val ALTO_MINIMO_TACTIL = 48.dp
private val LADO_TACTIL = 48.dp

/** «Reconocer» y «Editar notas», con la explicación de Reconocer detrás del icono de información. */
@Composable
internal fun AccionesNotasTramo(acciones: AccionesDetalleTramo) {
    var ayuda by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = acciones.onReconocer,
            modifier = Modifier.weight(1f).heightIn(min = ALTO_MINIMO_TACTIL).testTag(ETIQUETA_DETALLE_RECONOCER),
        ) {
            Icon(Iconos.Empezar, contentDescription = null, modifier = Modifier.padding(end = Espacio.xs))
            Text(stringResource(R.string.detalle_reconocer), maxLines = 1)
        }
        OutlinedButton(
            onClick = acciones.onEditarNotas,
            modifier = Modifier.weight(1f).heightIn(min = ALTO_MINIMO_TACTIL).testTag(ETIQUETA_DETALLE_EDITAR),
        ) {
            Icon(Iconos.Editar, contentDescription = null, modifier = Modifier.padding(end = Espacio.xs))
            Text(stringResource(R.string.edicion_editar_notas), maxLines = 1)
        }
        IconButton(
            onClick = { ayuda = true },
            modifier = Modifier.size(LADO_TACTIL).testTag(ETIQUETA_DETALLE_AYUDA),
        ) {
            Icon(
                imageVector = Iconos.Informacion,
                contentDescription = stringResource(R.string.detalle_ayuda_reconocer),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (ayuda) AyudaReconocer { ayuda = false }
}

@Composable
private fun AyudaReconocer(onCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(stringResource(R.string.detalle_ayuda_reconocer_titulo)) },
        text = { Text(stringResource(R.string.detalle_reconocer_ayuda), style = Cuerpo) },
        confirmButton = {
            TextButton(onClick = onCerrar, modifier = Modifier.heightIn(min = ALTO_MINIMO_TACTIL)) {
                Text(stringResource(R.string.detalle_ayuda_cerrar))
            }
        },
    )
}
