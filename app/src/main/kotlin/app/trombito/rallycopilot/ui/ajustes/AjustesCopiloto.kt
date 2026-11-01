package app.trombito.rallycopilot.ui.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import app.trombito.rallycopilot.ui.copiloto.FilaVozCopiloto
import app.trombito.rallycopilot.ui.copiloto.IconoModo
import app.trombito.rallycopilot.ui.copiloto.detalle
import app.trombito.rallycopilot.ui.copiloto.nombre
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_AJUSTES_MODO_PREFIJO = "ajustes-modo-"
const val ETIQUETA_AJUSTES_VOZ_SIN_ELEGIR = "ajustes-voz-sin-elegir"

private val ALTO_FILA = 56.dp

/** Modo del copiloto (cuatro opciones con icono y texto) y voz (dos opciones con ▶ para probarlas). */
@Composable
internal fun SeccionCopiloto(
    ajustes: AjustesConduccion,
    acciones: AccionesAjustes,
) {
    Seccion(R.string.copiloto_modo_titulo) {
        Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
            ModoCopiloto.entries.forEach { modo ->
                FilaModo(
                    modo = modo,
                    seleccionado = modo == ajustes.modoCopiloto,
                    onElegir = { acciones.onModoCopiloto(modo) },
                )
            }
        }
    }
    Seccion(R.string.copiloto_voz_titulo) {
        Column(verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
            if (ajustes.voz == null) {
                Text(
                    text = stringResource(R.string.copiloto_voz_sin_elegir),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(ETIQUETA_AJUSTES_VOZ_SIN_ELEGIR),
                )
            }
            VozCopiloto.entries.forEach { voz ->
                FilaVozCopiloto(
                    voz = voz,
                    seleccionada = voz == ajustes.voz,
                    onElegir = { acciones.onVozCopiloto(voz) },
                    onProbar = { acciones.onProbarVoz(voz) },
                )
            }
        }
    }
}

@Composable
private fun FilaModo(
    modo: ModoCopiloto,
    seleccionado: Boolean,
    onElegir: () -> Unit,
) {
    val colores = MaterialTheme.colorScheme
    Surface(
        color = if (seleccionado) colores.primaryContainer else colores.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(ETIQUETA_AJUSTES_MODO_PREFIJO + modo.name.lowercase())
                    .selectable(selected = seleccionado, role = Role.RadioButton, onClick = onElegir)
                    .heightIn(min = ALTO_FILA)
                    .padding(horizontal = Espacio.l, vertical = Espacio.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacio.m),
        ) {
            IconoModo(modo)
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(modo.nombre()),
                    style = MaterialTheme.typography.titleMedium,
                    color = colores.onSurface,
                )
                Text(
                    text = stringResource(modo.detalle()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colores.onSurfaceVariant,
                )
            }
            RadioButton(selected = seleccionado, onClick = null)
        }
    }
}
