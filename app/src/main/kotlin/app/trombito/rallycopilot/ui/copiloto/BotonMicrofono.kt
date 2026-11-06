package app.trombito.rallycopilot.ui.copiloto

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.BotonIcono
import app.trombito.rallycopilot.ui.comun.BotonRedondoMapa
import app.trombito.rallycopilot.ui.comun.EstadoBotonIcono

const val ETIQUETA_MICROFONO_BOTON = "copiloto-microfono-boton"

private val LADO_ICONO = 28.dp
private val LADO_ICONO_GRANDE = 48.dp
private val LADO_GRANDE = 88.dp
private const val PULSO_MS = 600
private const val ALFA_PULSO_MIN = 0.35f

/**
 * Botón del micrófono del panel de conducción: pulsarlo hace que el copiloto escuche una orden. Mientras escucha
 * ([escuchando]) se resalta y el icono late. La primera vez (sin permiso) explica para qué se usa el micrófono y
 * lo pide. Con [grande] (reconocimiento, donde se dicta todo el rato) el botón mide 88 dp, fácil de acertar.
 */
@Composable
fun BotonMicrofono(
    escuchando: Boolean,
    onEscuchar: () -> Unit,
    modifier: Modifier = Modifier,
    grande: Boolean = false,
) {
    val solicitar = rememberSolicitudMicrofono(alConcedido = onEscuchar)
    val alfa =
        if (escuchando) {
            rememberInfiniteTransition(label = "microfono")
                .animateFloat(
                    initialValue = 1f,
                    targetValue = ALFA_PULSO_MIN,
                    animationSpec = infiniteRepeatable(tween(PULSO_MS), RepeatMode.Reverse),
                    label = "microfono-alfa",
                ).value
        } else {
            1f
        }
    val descripcion = stringResource(if (escuchando) R.string.microfono_escuchando else R.string.microfono_boton)
    val icono: @Composable () -> Unit = {
        Icon(
            painterResource(R.drawable.ic_microfono),
            contentDescription = null,
            modifier = Modifier.size(if (grande) LADO_ICONO_GRANDE else LADO_ICONO).alpha(alfa),
        )
    }
    if (grande) {
        BotonIcono(
            descripcion = descripcion,
            onClick = { if (!escuchando) solicitar() },
            modifier = modifier.size(LADO_GRANDE).testTag(ETIQUETA_MICROFONO_BOTON),
            estado = EstadoBotonIcono(seleccionado = escuchando),
            contenido = icono,
        )
    } else {
        BotonRedondoMapa(
            descripcion = descripcion,
            onClick = { if (!escuchando) solicitar() },
            modifier = modifier.testTag(ETIQUETA_MICROFONO_BOTON),
            activo = escuchando,
            contenido = icono,
        )
    }
}

/**
 * Devuelve una acción que ejecuta [alConcedido] si ya hay permiso de micrófono; si no, enseña antes (en español)
 * para qué se usa y lo pide, y ejecuta [alConcedido] solo si se concede.
 */
@Composable
fun rememberSolicitudMicrofono(alConcedido: () -> Unit): () -> Unit {
    val contexto = LocalContext.current
    var explicando by rememberSaveable { mutableStateOf(false) }
    val accion by rememberUpdatedState(alConcedido)
    val lanzador =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
            if (concedido) accion()
        }
    if (explicando) {
        AlertDialog(
            onDismissRequest = { explicando = false },
            title = { Text(stringResource(R.string.microfono_permiso_titulo)) },
            text = { Text(stringResource(R.string.microfono_permiso_texto)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        explicando = false
                        lanzador.launch(Manifest.permission.RECORD_AUDIO)
                    },
                ) { Text(stringResource(R.string.microfono_permiso_continuar)) }
            },
            dismissButton = {
                TextButton(onClick = { explicando = false }) {
                    Text(stringResource(R.string.microfono_permiso_ahora_no))
                }
            },
        )
    }
    return {
        val concedido =
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (concedido) accion() else explicando = true
    }
}
