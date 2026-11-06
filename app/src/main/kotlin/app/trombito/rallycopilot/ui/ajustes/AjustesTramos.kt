package app.trombito.rallycopilot.ui.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.LocalColoresRally

const val ETIQUETA_AJUSTES_CRONOMETRAR = "ajustes-cronometrar"
const val ETIQUETA_AJUSTES_SALIDA_MANUAL = "ajustes-salida-manual"
const val ETIQUETA_PREVISTA_CRONO = "prevista-crono"

/**
 * Tramos: los interruptores «Cronometrar trayectos» y «Salida manual» (cada uno con su línea de explicación).
 * Cronometrar activado: cada trayecto se guarda como tramo con sus pasadas; la vista previa enseña el cronómetro con
 * su delta, y desactivado dice qué deja de hacerse (las notas y el guiado siguen).
 */
@Composable
internal fun SeccionTramos(
    cronometrar: Boolean,
    onCronometrar: (Boolean) -> Unit,
    salidaManual: Boolean,
    onSalidaManual: (Boolean) -> Unit,
) {
    TarjetaSeccion(
        clave = "tramos",
        icono = Iconos.Cronometro,
        titulo = R.string.ajustes_seccion_tramos,
        detalle = R.string.ajustes_seccion_tramos_detalle,
        vistaPrevia = { PrevistaCrono(cronometrar) },
    ) {
        FilaInterruptor(
            titulo = stringResource(R.string.ajustes_cronometrar),
            marcado = cronometrar,
            onCambio = onCronometrar,
            etiqueta = ETIQUETA_AJUSTES_CRONOMETRAR,
            detalle = stringResource(R.string.ajustes_cronometrar_detalle),
        )
        FilaInterruptor(
            titulo = stringResource(R.string.ajustes_salida_manual),
            marcado = salidaManual,
            onCambio = onSalidaManual,
            etiqueta = ETIQUETA_AJUSTES_SALIDA_MANUAL,
            detalle = stringResource(R.string.ajustes_salida_manual_detalle),
        )
    }
}

@Composable
private fun PrevistaCrono(cronometrar: Boolean) {
    val descripcion =
        stringResource(
            if (cronometrar) R.string.ajustes_prevista_crono_activo else R.string.ajustes_prevista_crono_inactivo,
        )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(ETIQUETA_PREVISTA_CRONO)
                .semantics { contentDescription = descripcion },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        Icon(
            Iconos.Cronometro,
            contentDescription = null,
            tint = if (cronometrar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(32.dp),
        )
        if (cronometrar) {
            Text(
                text = stringResource(R.string.ajustes_prevista_crono_tiempo),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Text(
                text = stringResource(R.string.ajustes_prevista_crono_delta),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = LocalColoresRally.current.deltaMejor,
                modifier = Modifier.weight(1f).clearAndSetSemantics {},
            )
        } else {
            Text(
                text = stringResource(R.string.ajustes_prevista_crono_inactivo),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).clearAndSetSemantics {},
            )
        }
    }
}
