package app.trombito.rallycopilot.ui.ajustes

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.BotonIcono
import app.trombito.rallycopilot.ui.comun.EstadoBotonIcono
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Cuerpo
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.EtiquetaIcono
import app.trombito.rallycopilot.ui.theme.Medidas
import app.trombito.rallycopilot.ui.theme.Radios

/** Prefijo de la etiqueta de prueba del botón ⓘ (le sigue el nombre del recurso del título). */
const val ETIQUETA_INFO_PREFIJO = "ajustes-info-"

private val ICONO_INFO = 20.dp
private val ALTO_FILA = 56.dp

/** Título de una opción dentro de una subpantalla: 16 sp SemiBold. */
private val TituloOpcion = Cuerpo.copy(fontWeight = FontWeight.SemiBold)

@Composable
internal fun estadoSeleccion(seleccionado: Boolean) =
    EstadoBotonIcono(
        seleccionado = seleccionado,
        descripcionEstado = if (seleccionado) stringResource(R.string.ajustes_seleccionado) else null,
        rol = Role.RadioButton,
    )

/** El ⓘ: abre o cierra la explicación larga de una opción (objetivo de 48 dp, icono de 20). */
@Composable
internal fun BotonInfo(
    abierto: Boolean,
    onClick: () -> Unit,
    nombre: String,
    modifier: Modifier = Modifier,
) {
    val descripcion = stringResource(R.string.ajustes_info_boton, nombre)
    IconButton(
        onClick = onClick,
        modifier =
            modifier.size(Medidas.zonaTactil).semantics {
                contentDescription = descripcion
                selected = abierto
            },
    ) {
        Icon(
            Iconos.Informacion,
            contentDescription = null,
            tint = if (abierto) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(ICONO_INFO),
        )
    }
}

/**
 * Un ajuste de una subpantalla: su nombre con el ⓘ (el [detalle], de una frase, solo se ve al pulsarlo) y los
 * controles debajo.
 */
@Composable
internal fun Opcion(
    @StringRes titulo: Int,
    @StringRes detalle: Int?,
    contenido: @Composable () -> Unit,
) {
    var verInfo by rememberSaveable { mutableStateOf(false) }
    val nombre = stringResource(titulo)
    val entrada = LocalResources.current.getResourceEntryName(titulo)
    Column(verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = nombre,
                style = TituloOpcion,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (detalle != null) {
                BotonInfo(verInfo, { verInfo = !verInfo }, nombre, Modifier.testTag(ETIQUETA_INFO_PREFIJO + entrada))
            }
        }
        if (detalle != null) {
            AnimatedVisibility(verInfo) {
                Text(
                    text = stringResource(detalle),
                    style = Ayuda,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(ETIQUETA_INFO_PREFIJO + entrada + "-texto"),
                )
            }
        }
        contenido()
    }
}

/** Un icono-botón con su etiqueta de 12 sp debajo (si el icono no se explica solo); sin [etiqueta], solo el icono. */
@Composable
@Suppress("LongParameterList") // etiqueta, descripción, estado, clic, etiqueta de pruebas y el dibujo
internal fun BotonIconoEtiquetado(
    etiqueta: String?,
    descripcion: String,
    seleccionado: Boolean,
    onClick: () -> Unit,
    etiquetaPrueba: String,
    modifier: Modifier = Modifier,
    contenido: @Composable () -> Unit,
) {
    val colores = MaterialTheme.colorScheme
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BotonIcono(
            descripcion = descripcion,
            onClick = onClick,
            modifier = Modifier.testTag(etiquetaPrueba),
            estado = estadoSeleccion(seleccionado),
            contenido = contenido,
        )
        if (etiqueta != null) {
            Text(
                text = etiqueta,
                style = EtiquetaIcono,
                color = if (seleccionado) colores.primary else colores.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Espacio.xs),
            )
        }
    }
}

/** Una fila de opciones iguales repartidas a lo ancho. */
@Composable
internal fun FilaOpciones(
    modifier: Modifier = Modifier,
    contenido: @Composable RowScope.() -> Unit,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, content = contenido)
}

/** Una fila que lleva a otra pantalla o lanza una acción; [icono] a la izquierda y flecha si [navega]. */
@Composable
@Suppress("LongParameterList") // título, detalle, etiqueta de pruebas, clic, icono y estado de la fila
internal fun FilaAccion(
    titulo: String,
    detalle: String?,
    etiqueta: String,
    onClick: () -> Unit,
    icono: ImageVector,
    navega: Boolean = false,
    habilitada: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = habilitada,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = Radios.formaTarjeta,
        modifier = Modifier.fillMaxWidth().testTag(etiqueta),
    ) {
        Row(
            modifier = Modifier.heightIn(min = ALTO_FILA).padding(horizontal = Espacio.l, vertical = Espacio.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacio.m),
        ) {
            Icon(icono, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(titulo, style = TituloOpcion, color = MaterialTheme.colorScheme.onSurface)
                if (detalle != null) {
                    Text(detalle, style = Ayuda, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
            if (navega) {
                Icon(Iconos.Siguiente, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Interruptor con título, una línea corta de [detalle] y, si hay [info], un ⓘ que despliega la explicación larga.
 * Toda la fila (menos el ⓘ) conmuta.
 */
@Composable
@Suppress("LongParameterList") // título, estado, cambio, etiqueta de pruebas y los dos textos opcionales
internal fun FilaInterruptor(
    titulo: String,
    marcado: Boolean,
    onCambio: (Boolean) -> Unit,
    etiqueta: String,
    detalle: String? = null,
    info: String? = null,
) {
    var verInfo by rememberSaveable { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = Radios.formaTarjeta,
        modifier = Modifier.fillMaxWidth().testTag(etiqueta),
    ) {
        Column(Modifier.padding(horizontal = Espacio.l, vertical = Espacio.s)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Espacio.s),
            ) {
                Row(
                    modifier =
                        Modifier
                            .weight(1f)
                            .toggleable(value = marcado, role = Role.Switch, onValueChange = onCambio)
                            .heightIn(min = ALTO_FILA),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Espacio.m),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(titulo, style = TituloOpcion, color = MaterialTheme.colorScheme.onSurface)
                        if (detalle != null) {
                            Text(
                                detalle,
                                style = Ayuda,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                    }
                    Switch(checked = marcado, onCheckedChange = null)
                }
                if (info != null) {
                    BotonInfo(verInfo, { verInfo = !verInfo }, titulo, Modifier.testTag("$etiqueta-info"))
                }
            }
            if (info != null) {
                AnimatedVisibility(verInfo) {
                    Text(
                        text = info,
                        style = Ayuda,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Espacio.s),
                    )
                }
            }
        }
    }
}
