package app.trombito.rallycopilot.ui.ajustes

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.audio.PermisoIntercom
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.mapa.abrirAjustesDeLaApp
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Cuerpo
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Medidas
import app.trombito.rallycopilot.ui.theme.Radios

const val ETIQUETA_INTERCOM_ESTADO = "ajustes-intercom-estado"
const val ETIQUETA_INTERCOM_BOTON = "ajustes-intercom-boton"

/** Estado del permiso de Bluetooth del intercom del casco. */
internal enum class EstadoIntercom {
    CONCEDIDO,
    SIN_PERMISO,

    /** Denegado con «no volver a preguntar»: el sistema ya no enseña el diálogo y solo vale ir a los ajustes. */
    BLOQUEADO,
}

/**
 * Fila «Intercom del casco» de Ajustes > Voz: si el permiso de Bluetooth está concedido, y si no, el botón para
 * concederlo (o para abrir los ajustes del sistema si está bloqueado). Solo existe desde Android 12.
 */
@Composable
internal fun ControlIntercom() {
    if (!PermisoIntercom.hayQuePedir(Build.VERSION.SDK_INT)) return
    val contexto = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    var bloqueado by remember { mutableStateOf(false) }
    val lanzador =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
            // Tras negarse, si el sistema ya no ofrece explicación es que no volverá a preguntar.
            bloqueado = !concedido && contexto.buscarActividad()?.shouldShowRequestPermissionRationale(PERMISO) == false
            revision++
        }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { revision++ }
    // `revision` fuerza la relectura del sistema tras pedir o volver de los ajustes.
    val estado =
        when {
            revision >= 0 && contexto.permisoConcedido() -> EstadoIntercom.CONCEDIDO
            bloqueado -> EstadoIntercom.BLOQUEADO
            else -> EstadoIntercom.SIN_PERMISO
        }
    Opcion(R.string.ajustes_intercom_titulo, R.string.ajustes_intercom_detalle) {
        FilaIntercom(
            estado = estado,
            onConceder = { lanzador.launch(PERMISO) },
            onAbrirAjustes = { contexto.abrirAjustesDeLaApp() },
        )
    }
}

/** La fila sin lógica: el estado, y el botón que corresponde a él. */
@Composable
internal fun FilaIntercom(
    estado: EstadoIntercom,
    onConceder: () -> Unit,
    onAbrirAjustes: () -> Unit,
) {
    val colores = MaterialTheme.colorScheme
    val concedido = estado == EstadoIntercom.CONCEDIDO
    val texto =
        stringResource(
            if (concedido) R.string.ajustes_intercom_concedido else R.string.ajustes_intercom_sin_permiso,
        )
    // «Concedido» solo no dice de qué; «Sin permiso…» ya se explica solo.
    val descripcion = if (concedido) stringResource(R.string.ajustes_intercom_estado_concedido) else null
    Surface(
        color = if (concedido) colores.primaryContainer else colores.surfaceContainerHigh,
        shape = Radios.formaTarjeta,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Medidas.accionPrincipal)
                    .padding(horizontal = Espacio.l, vertical = Espacio.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacio.m),
        ) {
            Icon(
                imageVector = if (concedido) Iconos.Hecho else Iconos.Auricular,
                contentDescription = null,
                tint = colores.primary,
                modifier = Modifier.size(Medidas.iconoCabecera),
            )
            Text(
                text = texto,
                style = if (concedido) Cuerpo else Ayuda,
                color = if (concedido) colores.onSurface else colores.onSurfaceVariant,
                modifier =
                    Modifier
                        .weight(1f)
                        .testTag(ETIQUETA_INTERCOM_ESTADO)
                        .semantics { if (descripcion != null) contentDescription = descripcion },
            )
            when (estado) {
                EstadoIntercom.CONCEDIDO -> {
                    Unit
                }

                EstadoIntercom.SIN_PERMISO -> {
                    Button(
                        onClick = onConceder,
                        modifier = Modifier.heightIn(min = Medidas.zonaTactil).testTag(ETIQUETA_INTERCOM_BOTON),
                    ) { Text(stringResource(R.string.ajustes_intercom_conceder)) }
                }

                EstadoIntercom.BLOQUEADO -> {
                    OutlinedButton(
                        onClick = onAbrirAjustes,
                        modifier = Modifier.heightIn(min = Medidas.zonaTactil).testTag(ETIQUETA_INTERCOM_BOTON),
                    ) { Text(stringResource(R.string.ajustes_intercom_abrir_ajustes)) }
                }
            }
        }
    }
}

private const val PERMISO = PermisoIntercom.PERMISO

private fun Context.permisoConcedido(): Boolean =
    ContextCompat.checkSelfPermission(this, PERMISO) == PackageManager.PERMISSION_GRANTED

private fun Context.buscarActividad(): Activity? {
    var actual: Context? = this
    while (actual is ContextWrapper) {
        if (actual is Activity) return actual
        actual = actual.baseContext
    }
    return null
}
