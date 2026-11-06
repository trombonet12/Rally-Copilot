package app.trombito.rallycopilot.ui.comun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Medidas
import app.trombito.rallycopilot.ui.theme.TituloVacio

private const val OPACIDAD_ICONO_TENUE = 0.6f

/** Etiqueta de prueba del botón de acción de un estado vacío. */
const val ETIQUETA_ACCION_VACIO = "accion-vacio"

/**
 * Estado vacío del sistema WRC: icono de 48 dp en ámbar tenue, título de 18 sp, una frase que dice qué hacer y, si hay
 * [etiquetaAccion], un botón de 48 dp de alto. [etiqueta] es la etiqueta de prueba del conjunto.
 */
@Suppress("LongParameterList") // API de componente: icono, textos y acción opcional
@Composable
fun EstadoVacioWrc(
    icono: ImageVector,
    titulo: String,
    texto: String,
    etiqueta: String,
    modifier: Modifier = Modifier,
    etiquetaAccion: String? = null,
    onAccion: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Espacio.xl).testTag(etiqueta),
        verticalArrangement = Arrangement.spacedBy(Espacio.m, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icono,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = OPACIDAD_ICONO_TENUE),
            modifier = Modifier.size(Medidas.iconoVacio),
        )
        Text(
            text = titulo,
            style = TituloVacio,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = texto,
            style = Ayuda,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (etiquetaAccion != null && onAccion != null) {
            Button(
                onClick = onAccion,
                modifier = Modifier.heightIn(min = Medidas.zonaTactil).testTag(ETIQUETA_ACCION_VACIO),
            ) {
                Text(text = etiquetaAccion, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
