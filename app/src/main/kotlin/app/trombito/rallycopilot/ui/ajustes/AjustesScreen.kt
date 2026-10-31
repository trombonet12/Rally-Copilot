package app.trombito.rallycopilot.ui.ajustes

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.ajustes.EstiloManiobras
import app.trombito.rallycopilot.domain.ajustes.IconoPosicion
import app.trombito.rallycopilot.domain.ajustes.Orientacion
import app.trombito.rallycopilot.motor.notas.NivelAnticipacion
import app.trombito.rallycopilot.motor.ruta.TipoManiobra
import app.trombito.rallycopilot.ui.comun.BarraSuperior
import app.trombito.rallycopilot.ui.comun.BotonIcono
import app.trombito.rallycopilot.ui.comun.EstadoBotonIcono
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.nombre
import app.trombito.rallycopilot.ui.comun.recurso
import app.trombito.rallycopilot.ui.maniobras.IconoManiobra
import app.trombito.rallycopilot.ui.maniobras.nombre
import app.trombito.rallycopilot.ui.maniobras.variante
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_AJUSTES_MAPAS = "ajustes-mapas-offline"
const val ETIQUETA_AJUSTES_ACERCA_DE = "ajustes-acerca-de"
const val ETIQUETA_ZOOM_PREFIJO = "ajustes-zoom-"
const val ETIQUETA_ICONO_PREFIJO = "ajustes-icono-"
const val ETIQUETA_MANIOBRAS_PREFIJO = "ajustes-maniobras-"
const val ETIQUETA_ORIENTACION_RUMBO = "ajustes-orientacion-rumbo"
const val ETIQUETA_ORIENTACION_NORTE = "ajustes-orientacion-norte"

private val LADO_MINIATURA = 64.dp
private val DIBUJO_MINIATURA = 44.dp

/** Tamaño del icono de cada nivel de zoom: crece de «más lejos» a «más cerca». */
private val TAMANOS_ZOOM = listOf(16.dp, 20.dp, 24.dp, 28.dp, 32.dp)

/** Callbacks de Ajustes. */
data class AccionesAjustes(
    val onVolver: () -> Unit = {},
    val onMapasOffline: () -> Unit = {},
    val onAcercaDe: () -> Unit = {},
    val onNivelZoom: (Int) -> Unit = {},
    val onIcono: (IconoPosicion) -> Unit = {},
    val onOrientacion: (Orientacion) -> Unit = {},
    val onEstiloManiobras: (EstiloManiobras) -> Unit = {},
    val onAnticipacion: (NivelAnticipacion) -> Unit = {},
    val onNotasSimplificadas: (Boolean) -> Unit = {},
)

@StringRes
private fun textoNivelZoom(nivel: Int): Int =
    when (nivel) {
        AjustesConduccion.NIVEL_MIN -> R.string.ajustes_zoom_muy_lejos
        -1 -> R.string.ajustes_zoom_lejos
        0 -> R.string.ajustes_zoom_normal
        1 -> R.string.ajustes_zoom_cerca
        else -> R.string.ajustes_zoom_muy_cerca
    }

/**
 * Ajustes: Mapas offline, Acerca de y las preferencias de conducción (zoom, icono de posición, estilo de maniobras,
 * orientación, anticipación de las notas y notas simplificadas).
 */
@Composable
fun AjustesScreen(
    ajustes: AjustesConduccion,
    acciones: AccionesAjustes,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().safeDrawingPadding()) {
        BarraSuperior(stringResource(R.string.ajustes_titulo), acciones.onVolver)
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Espacio.l),
            verticalArrangement = Arrangement.spacedBy(Espacio.l),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
                FilaAjuste(
                    stringResource(R.string.ajustes_mapas_offline),
                    ETIQUETA_AJUSTES_MAPAS,
                    acciones.onMapasOffline,
                )
                FilaAjuste(stringResource(R.string.ajustes_acerca_de), ETIQUETA_AJUSTES_ACERCA_DE, acciones.onAcercaDe)
            }
            SeccionZoom(ajustes.nivelZoom, acciones.onNivelZoom)
            SeccionIcono(ajustes.icono, acciones.onIcono)
            SeccionManiobras(ajustes.estiloManiobras, acciones.onEstiloManiobras)
            SeccionOrientacion(ajustes.orientacion, acciones.onOrientacion)
            SeccionNotas(ajustes, acciones)
        }
    }
}

@Composable
internal fun Seccion(
    @StringRes titulo: Int,
    contenido: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
        Text(
            text = stringResource(titulo),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        contenido()
    }
}

@Composable
private fun SeccionZoom(
    nivel: Int,
    onNivel: (Int) -> Unit,
) {
    Seccion(R.string.ajustes_zoom_titulo) {
        Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s), verticalAlignment = Alignment.CenterVertically) {
            (AjustesConduccion.NIVEL_MIN..AjustesConduccion.NIVEL_MAX).forEachIndexed { indice, valor ->
                BotonIcono(
                    descripcion = stringResource(textoNivelZoom(valor)),
                    onClick = { onNivel(valor) },
                    modifier = Modifier.testTag(ETIQUETA_ZOOM_PREFIJO + valor),
                    estado = estadoSeleccion(valor == nivel),
                ) { Icon(Iconos.Lupa, contentDescription = null, modifier = Modifier.size(TAMANOS_ZOOM[indice])) }
            }
        }
    }
}

@Composable
private fun SeccionIcono(
    icono: IconoPosicion,
    onIcono: (IconoPosicion) -> Unit,
) {
    Seccion(R.string.ajustes_icono_titulo) {
        Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s), verticalAlignment = Alignment.CenterVertically) {
            IconoPosicion.entries.forEach { opcion ->
                BotonIcono(
                    descripcion = stringResource(opcion.nombre()),
                    onClick = { onIcono(opcion) },
                    modifier = Modifier.size(LADO_MINIATURA).testTag(ETIQUETA_ICONO_PREFIJO + opcion.name.lowercase()),
                    estado = estadoSeleccion(opcion == icono),
                ) {
                    Image(painterResource(opcion.recurso()), contentDescription = null, Modifier.size(DIBUJO_MINIATURA))
                }
            }
        }
    }
}

@Composable
private fun SeccionManiobras(
    estilo: EstiloManiobras,
    onEstilo: (EstiloManiobras) -> Unit,
) {
    Seccion(R.string.ajustes_maniobras_titulo) {
        Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s), verticalAlignment = Alignment.CenterVertically) {
            EstiloManiobras.entries.forEach { opcion ->
                BotonIcono(
                    descripcion = stringResource(opcion.nombre()),
                    onClick = { onEstilo(opcion) },
                    modifier =
                        Modifier.size(LADO_MINIATURA).testTag(ETIQUETA_MANIOBRAS_PREFIJO + opcion.name.lowercase()),
                    estado = estadoSeleccion(opcion == estilo),
                ) {
                    IconoManiobra(
                        tipo = TipoManiobra.DERECHA,
                        variante = opcion.variante(),
                        tamano = DIBUJO_MINIATURA,
                        decorativo = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun SeccionOrientacion(
    orientacion: Orientacion,
    onOrientacion: (Orientacion) -> Unit,
) {
    Seccion(R.string.ajustes_orientacion_titulo) {
        Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
            BotonIcono(
                descripcion = stringResource(R.string.ajustes_orientacion_rumbo),
                onClick = { onOrientacion(Orientacion.RUMBO) },
                modifier = Modifier.testTag(ETIQUETA_ORIENTACION_RUMBO),
                estado = estadoSeleccion(orientacion == Orientacion.RUMBO),
            ) { Icon(Iconos.Rumbo, contentDescription = null) }
            BotonIcono(
                descripcion = stringResource(R.string.ajustes_orientacion_norte),
                onClick = { onOrientacion(Orientacion.NORTE) },
                modifier = Modifier.testTag(ETIQUETA_ORIENTACION_NORTE),
                estado = estadoSeleccion(orientacion == Orientacion.NORTE),
            ) { Icon(Iconos.Brujula, contentDescription = null) }
        }
    }
}

@Composable
internal fun estadoSeleccion(seleccionado: Boolean) =
    EstadoBotonIcono(
        seleccionado = seleccionado,
        descripcionEstado = if (seleccionado) stringResource(R.string.ajustes_seleccionado) else null,
        rol = Role.RadioButton,
    )

@Composable
private fun FilaAjuste(
    titulo: String,
    etiqueta: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().testTag(etiqueta),
    ) {
        Column(
            modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = Espacio.l, vertical = Espacio.m),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
