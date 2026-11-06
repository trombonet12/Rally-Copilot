package app.trombito.rallycopilot.ui.comun

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Medidas
import app.trombito.rallycopilot.ui.theme.Radios

/** Etiqueta de prueba del asa de la hoja. */
const val ETIQUETA_ASA_HOJA = "asa-hoja"

/**
 * Hoja inferior del sistema WRC: radio de 16 dp arriba, asa centrada, fondo opaco `surfaceContainerHigh` e interior
 * de 16 dp. Es solo el contenedor: para una hoja modal, úsala como contenido de `ModalBottomSheet` con
 * `shape = Radios.formaHoja`, `containerColor = Color.Transparent` y `dragHandle = null`.
 */
@Composable
fun HojaInferiorWrc(
    modifier: Modifier = Modifier,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = Radios.formaHoja,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.navigationBarsPadding().padding(horizontal = Espacio.l).padding(bottom = Espacio.l),
        ) {
            Box(
                Modifier
                    .padding(vertical = Espacio.m)
                    .size(Medidas.asaAncho, Medidas.asaAlto)
                    .background(MaterialTheme.colorScheme.outline, Radios.formaPildora)
                    .testTag(ETIQUETA_ASA_HOJA)
                    .clearAndSetSemantics {},
            )
            Column(Modifier.fillMaxWidth(), content = contenido)
        }
    }
}
