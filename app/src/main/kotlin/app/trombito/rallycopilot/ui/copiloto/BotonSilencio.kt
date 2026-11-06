package app.trombito.rallycopilot.ui.copiloto

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import app.trombito.rallycopilot.ui.comun.BotonRedondoMapa
import app.trombito.rallycopilot.ui.comun.Iconos

const val ETIQUETA_SILENCIO_BOTON = "copiloto-silencio-boton"

/**
 * El modo al que lleva el botón de silenciar: desde el silencio, el último modo con voz ([anterior]; el modo por
 * defecto si no hay ninguno) y desde cualquier otro, el silencio.
 */
internal fun modoAlAlternarSilencio(
    modo: ModoCopiloto,
    anterior: ModoCopiloto,
): ModoCopiloto =
    if (modo == ModoCopiloto.SILENCIO) {
        anterior.takeIf { it != ModoCopiloto.SILENCIO } ?: ModoCopiloto.POR_DEFECTO
    } else {
        ModoCopiloto.SILENCIO
    }

/**
 * Silencia el copiloto de un toque y lo devuelve al modo anterior con otro. Con el copiloto en [silenciado] el botón
 * está activo (borde ámbar) y el icono es el altavoz tachado; si no, el altavoz con ondas.
 */
@Composable
fun BotonSilencio(
    silenciado: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BotonRedondoMapa(
        descripcion =
            stringResource(if (silenciado) R.string.copiloto_silencio_activo else R.string.copiloto_silenciar),
        onClick = onClick,
        modifier = modifier.testTag(ETIQUETA_SILENCIO_BOTON),
        activo = silenciado,
        descripcionEstado = if (silenciado) stringResource(R.string.copiloto_silencio_estado) else null,
    ) {
        if (silenciado) {
            Icon(painterResource(R.drawable.ic_modo_silencio), contentDescription = null)
        } else {
            Icon(Iconos.Volumen, contentDescription = null)
        }
    }
}
