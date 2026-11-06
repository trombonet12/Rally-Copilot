package app.trombito.rallycopilot.ui.tutorial

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.mapa.abrirAjustesDeLaApp
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Cuerpo
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Medidas

/** Etiqueta de prueba de cada fila de permiso (más la clave del [TipoPermiso]) y de su botón. */
const val ETIQUETA_PERMISO_PREFIJO = "bienvenida-permiso-"
const val ETIQUETA_PERMISO_BOTON_PREFIJO = "bienvenida-permiso-boton-"

/** Los tres permisos que pide la bienvenida. */
enum class TipoPermiso(
    val clave: String,
    @StringRes val titulo: Int,
    @StringRes val para: Int,
    @StringRes val denegado: Int,
) {
    UBICACION(
        "ubicacion",
        R.string.tutorial_permiso_ubicacion,
        R.string.tutorial_permiso_ubicacion_para,
        R.string.tutorial_permiso_ubicacion_denegado,
    ),
    MICROFONO(
        "microfono",
        R.string.tutorial_permiso_microfono,
        R.string.tutorial_permiso_microfono_para,
        R.string.tutorial_permiso_microfono_denegado,
    ),
    NOTIFICACIONES(
        "notificaciones",
        R.string.tutorial_permiso_notificaciones,
        R.string.tutorial_permiso_notificaciones_para,
        R.string.tutorial_permiso_notificaciones_denegado,
    ),
}

/** Estado de un permiso en la bienvenida. */
enum class EstadoPermiso { PENDIENTE, CONCEDIDO, DENEGADO }

/** Un permiso con su estado y lo que hacen sus botones: pedirlo, o abrir los ajustes si se denegó. */
class PermisoBienvenida(
    val tipo: TipoPermiso,
    val estado: EstadoPermiso,
    val onPedir: () -> Unit,
    val onAbrirAjustes: () -> Unit,
)

/**
 * Los tres permisos con el flujo del sistema (un diálogo por permiso). El estado se relee al volver de los ajustes;
 * lo que se pidió y no está concedido es «denegado». Antes de Android 13 las notificaciones no piden nada.
 */
@Composable
fun rememberPermisosBienvenida(): List<PermisoBienvenida> {
    val contexto = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    var pedidos by remember { mutableStateOf(emptySet<TipoPermiso>()) }
    val lanzador =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { revision++ }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { revision++ }
    return TipoPermiso.entries.map { tipo ->
        // `revision` fuerza la relectura del sistema tras pedir o volver de los ajustes.
        val concedido = revision >= 0 && contexto.concedido(tipo)
        val estado =
            when {
                concedido -> EstadoPermiso.CONCEDIDO
                tipo in pedidos -> EstadoPermiso.DENEGADO
                else -> EstadoPermiso.PENDIENTE
            }
        PermisoBienvenida(
            tipo = tipo,
            estado = estado,
            onPedir = {
                pedidos = pedidos + tipo
                lanzador.launch(tipo.permisosDeAndroid())
            },
            onAbrirAjustes = { contexto.abrirAjustesDeLaApp() },
        )
    }
}

private fun TipoPermiso.permisosDeAndroid(): Array<String> =
    when (this) {
        TipoPermiso.UBICACION -> {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        TipoPermiso.MICROFONO -> {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }

        TipoPermiso.NOTIFICACIONES -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                emptyArray()
            }
        }
    }

/** La ubicación vale con precisa o aproximada; el resto, con todo lo que pida (nada = concedido). */
private fun Context.concedido(tipo: TipoPermiso): Boolean {
    val necesarios = tipo.permisosDeAndroid().map { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    return if (tipo == TipoPermiso.UBICACION) necesarios.any { it } else necesarios.all { it }
}

/** Una tarjeta por permiso: qué es, para qué sirve y su botón; si se deniega, qué deja de funcionar. */
@Composable
internal fun ListaPermisos(
    permisos: List<PermisoBienvenida>,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
        permisos.forEach { FilaPermiso(it) }
    }
}

@Composable
private fun FilaPermiso(permiso: PermisoBienvenida) {
    val tipo = permiso.tipo
    val titulo = stringResource(tipo.titulo)
    TarjetaWrc(
        modifier = Modifier.fillMaxWidth().testTag(ETIQUETA_PERMISO_PREFIJO + tipo.clave),
        relleno = Espacio.m,
        activa = permiso.estado == EstadoPermiso.CONCEDIDO,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.m)) {
            Icon(
                imageVector = tipo.icono(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Medidas.iconoCabecera),
            )
            Column(Modifier.weight(1f)) {
                Text(titulo, style = Cuerpo, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                val explicacion = if (permiso.estado == EstadoPermiso.DENEGADO) tipo.denegado else tipo.para
                Text(stringResource(explicacion), style = Ayuda, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AccionPermiso(permiso, titulo)
        }
    }
}

@Composable
private fun AccionPermiso(
    permiso: PermisoBienvenida,
    titulo: String,
) {
    val etiqueta = ETIQUETA_PERMISO_BOTON_PREFIJO + permiso.tipo.clave
    when (permiso.estado) {
        EstadoPermiso.CONCEDIDO -> {
            Icon(
                imageVector = Iconos.Hecho,
                contentDescription = stringResource(R.string.tutorial_permiso_concedido) + ": " + titulo,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Medidas.iconoCabecera).testTag(etiqueta),
            )
        }

        EstadoPermiso.PENDIENTE -> {
            Button(
                onClick = permiso.onPedir,
                modifier = Modifier.heightIn(min = Medidas.zonaTactil).testTag(etiqueta),
            ) { Text(stringResource(R.string.tutorial_permiso_conceder), style = MaterialTheme.typography.labelLarge) }
        }

        EstadoPermiso.DENEGADO -> {
            OutlinedButton(
                onClick = permiso.onAbrirAjustes,
                modifier = Modifier.heightIn(min = Medidas.zonaTactil).testTag(etiqueta),
            ) { Text(stringResource(R.string.tutorial_permiso_ajustes), style = MaterialTheme.typography.labelLarge) }
        }
    }
}

private fun TipoPermiso.icono(): ImageVector =
    when (this) {
        TipoPermiso.UBICACION -> Iconos.Lugar
        TipoPermiso.MICROFONO -> Iconos.Microfono
        TipoPermiso.NOTIFICACIONES -> IconoCampana
    }
