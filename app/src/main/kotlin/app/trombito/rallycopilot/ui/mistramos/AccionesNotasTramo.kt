package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.reconocimiento.HojaReconocer
import app.trombito.rallycopilot.ui.theme.Espacio

private val ALTO_MINIMO_TACTIL = 48.dp

/**
 * «Reconocer» y «Editar notas». Reconocer no salta al mapa de golpe: primero enseña la hoja que explica las dos
 * pasadas ([HojaReconocer]) y solo con «Empezar» llama a [AccionesDetalleTramo.onReconocer].
 */
@Composable
internal fun AccionesNotasTramo(acciones: AccionesDetalleTramo) {
    var explicando by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = { explicando = true },
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
    }
    if (explicando) {
        HojaReconocer(
            onEmpezar = {
                explicando = false
                acciones.onReconocer()
            },
            onCancelar = { explicando = false },
        )
    }
}
