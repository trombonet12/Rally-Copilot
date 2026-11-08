package app.trombito.rallycopilot.ui.navegacion

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.ui.comun.FormatoEs
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.LADO_TACTIL
import app.trombito.rallycopilot.ui.maniobras.formatearDistanciaManiobra
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_VISTA_PREVIA = "nav-vista-previa"
const val ETIQUETA_RUTA_RAPIDA = "nav-ruta-rapida"
const val ETIQUETA_RUTA_EQUILIBRADA = "nav-ruta-equilibrada"
const val ETIQUETA_RUTA_SINUOSA = "nav-ruta-sinuosa"
const val ETIQUETA_RUTA_IGUAL = "nav-ruta-igual"
const val ETIQUETA_SIN_POSICION = "nav-sin-posicion"
const val ETIQUETA_SIN_RUTA = "nav-sin-ruta"
const val ETIQUETA_REINTENTAR_RUTA = "nav-reintentar-ruta"

private const val OPACIDAD_SOBRE_MAPA = 0.94f
private const val OPACIDAD_NO_DISPONIBLE = 0.6f
private const val OPACIDAD_ICONO_TENUE = 0.6f
private const val MIN_CURVAS_M = 100.0
private val ALTO_OPCION = 64.dp
private val ANCHO_MAXIMO_PANEL = 520.dp
private val GROSOR_SELECCION = 2.dp

/**
 * Panel inferior de la vista previa: los tres chips de ruta (rayo = rápida, carretera suave = equilibrada, carretera
 * con curvas = curvas) con tiempo, distancia y km de curvas, cancelar y empezar. En apaisado todo va en una sola
 * fila para ocupar poca altura.
 */
@Composable
fun PanelVistaPrevia(
    vista: VistaPrevia,
    acciones: AccionesNavegacion,
    horizontal: Boolean,
    modifier: Modifier = Modifier,
) {
    val descripcion = stringResource(R.string.navegacion_ui_rutas)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(OPACIDAD_SOBRE_MAPA),
        modifier =
            modifier
                .widthIn(max = ANCHO_MAXIMO_PANEL)
                .fillMaxWidth()
                .testTag(ETIQUETA_VISTA_PREVIA)
                .semantics { contentDescription = descripcion },
    ) {
        if (horizontal) {
            Column(Modifier.padding(Espacio.s), verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
                ParadasDeLaVistaPrevia(vista, acciones, horizontal = true)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Espacio.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BotonCancelarRuta(acciones.onCancelar)
                    Opciones(vista, acciones, Modifier.weight(1f))
                    BotonEmpezar(acciones.onEmpezar, habilitado = vista.puedeEmpezar)
                }
            }
        } else {
            Column(Modifier.padding(Espacio.s), verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
                ParadasDeLaVistaPrevia(vista, acciones, horizontal = false)
                Opciones(vista, acciones, Modifier.fillMaxWidth())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BotonCancelarRuta(acciones.onCancelar)
                    BotonEmpezar(acciones.onEmpezar, habilitado = vista.puedeEmpezar)
                }
            }
        }
    }
}

/** Las paradas de la ruta, numeradas y con el final (nada si no hay ninguna). */
@Composable
private fun ParadasDeLaVistaPrevia(
    vista: VistaPrevia,
    acciones: AccionesNavegacion,
    horizontal: Boolean,
) {
    if (vista.paradas.isEmpty()) return
    ListaParadas(vista.paradas, vista.destino, acciones, horizontal = horizontal, sobreMapa = false)
}

@Composable
private fun Opciones(
    vista: VistaPrevia,
    acciones: AccionesNavegacion,
    modifier: Modifier = Modifier,
) {
    if (vista.sinPosicion) {
        Text(
            text = stringResource(R.string.navegacion_ui_sin_posicion),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = modifier.heightIn(min = ALTO_OPCION).padding(Espacio.s).testTag(ETIQUETA_SIN_POSICION),
        )
        return
    }
    if (vista.opciones.values.none { it is OpcionRuta.Lista || it is OpcionRuta.Cargando }) {
        SinRutaPosible(acciones.onReintentar, modifier)
        return
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
        PerfilRuta.entries.forEach { perfil ->
            OpcionRutaTarjeta(
                chip =
                    ChipRuta(
                        perfil = perfil,
                        opcion = vista.opciones[perfil] ?: OpcionRuta.Cargando,
                        seleccionada = vista.perfil == perfil,
                        igualA = vista.igualA(perfil),
                    ),
                onClick = { acciones.onPerfil(perfil) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Ninguna ruta se pudo calcular (sin datos o con error): icono, mensaje y «Probar otra vez». */
@Composable
private fun SinRutaPosible(
    onReintentar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = MaterialTheme.colorScheme
    Row(
        modifier = modifier.heightIn(min = ALTO_OPCION).testTag(ETIQUETA_SIN_RUTA),
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Iconos.Direcciones, contentDescription = null, tint = colores.primary.copy(alpha = OPACIDAD_ICONO_TENUE))
        Text(
            text = stringResource(R.string.navegacion_ui_sin_ruta),
            style = MaterialTheme.typography.bodyMedium,
            color = colores.onSurface,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
        )
        TextButton(
            onClick = onReintentar,
            modifier = Modifier.heightIn(min = LADO_TACTIL).testTag(ETIQUETA_REINTENTAR_RUTA),
        ) {
            Text(text = stringResource(R.string.navegacion_ui_probar_otra_vez), maxLines = 1, softWrap = false)
        }
    }
}

/** Lo que pinta un chip: su perfil, el resultado, si es el elegido y, si repite la ruta de otro, de cuál. */
private data class ChipRuta(
    val perfil: PerfilRuta,
    val opcion: OpcionRuta,
    val seleccionada: Boolean,
    val igualA: PerfilRuta?,
)

@Composable
private fun OpcionRutaTarjeta(
    chip: ChipRuta,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val perfil = chip.perfil
    val opcion = chip.opcion
    val seleccionada = chip.seleccionada
    val igualA = chip.igualA
    val colores = MaterialTheme.colorScheme
    val nombre = stringResource(nombreRuta(perfil))
    val igual =
        igualA?.let { stringResource(R.string.navegacion_ui_ruta_igual_descripcion, stringResource(tituloRuta(it))) }
    val resumen = listOfNotNull(resumenOpcion(opcion), igual).joinToString(", ")
    val descripcion = stringResource(R.string.navegacion_ui_ruta_descripcion, nombre, resumen)
    val estado = if (seleccionada) stringResource(R.string.navegacion_ui_ruta_seleccionada) else null
    val disponible = opcion is OpcionRuta.Lista || opcion is OpcionRuta.Cargando
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (seleccionada) colores.primaryContainer else colores.surfaceContainerHighest,
        border = if (seleccionada) BorderStroke(GROSOR_SELECCION, colores.primary) else null,
        modifier =
            modifier
                .heightIn(min = ALTO_OPCION)
                .alpha(if (disponible) 1f else OPACIDAD_NO_DISPONIBLE)
                .testTag(etiquetaRuta(perfil))
                .selectable(selected = seleccionada, enabled = opcion is OpcionRuta.Lista, role = Role.RadioButton) {
                    onClick()
                }.semantics(mergeDescendants = true) {
                    contentDescription = descripcion
                    estado?.let { stateDescription = it }
                },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Espacio.m, vertical = Espacio.s),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Espacio.xs),
        ) {
            val colorIcono = if (seleccionada) colores.primary else colores.onSurface
            Icon(iconoRuta(perfil), contentDescription = null, tint = colorIcono)
            Text(
                text = stringResource(tituloRuta(perfil)),
                style = MaterialTheme.typography.labelLarge,
                color = if (seleccionada) colores.primary else colores.onSurface,
                maxLines = 1,
            )
            ContenidoOpcion(opcion, igualA != null)
        }
    }
}

@Composable
private fun ContenidoOpcion(
    opcion: OpcionRuta,
    mismaRuta: Boolean,
) {
    when (opcion) {
        OpcionRuta.Cargando -> {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }

        is OpcionRuta.Lista -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatearDuracionRuta(opcion.tiempoS),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = formatearDistanciaManiobra(opcion.distanciaM),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (mismaRuta) {
                    Text(
                        text = stringResource(R.string.navegacion_ui_ruta_igual),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        modifier = Modifier.testTag(ETIQUETA_RUTA_IGUAL),
                    )
                } else {
                    opcion.curvasM?.let { TextoCurvas(it) }
                }
            }
        }

        OpcionRuta.SinDatos -> {
            TextoNoDisponible(R.string.navegacion_ui_ruta_sin_datos)
        }

        is OpcionRuta.Error -> {
            TextoNoDisponible(R.string.navegacion_ui_ruta_error)
        }
    }
}

/** «6,2 km de curvas», o «Sin curvas» si la ruta no llega a 100 m de curvas. */
@Composable
private fun TextoCurvas(curvasM: Double) {
    Text(
        text = textoCurvas(curvasM),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

@Composable
private fun textoCurvas(curvasM: Double): String =
    if (curvasM < MIN_CURVAS_M) {
        stringResource(R.string.navegacion_ui_ruta_sin_curvas)
    } else {
        stringResource(R.string.navegacion_ui_ruta_curvas, FormatoEs.kilometrosDesdeMetros(curvasM))
    }

@Composable
private fun TextoNoDisponible(
    @StringRes texto: Int,
) {
    Text(
        text = stringResource(texto),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** Lo que lee TalkBack tras el nombre de la ruta: «23 min, 18,4 km», «calculando», etc. */
@Composable
private fun resumenOpcion(opcion: OpcionRuta): String =
    when (opcion) {
        OpcionRuta.Cargando -> {
            stringResource(R.string.navegacion_ui_ruta_calculando)
        }

        is OpcionRuta.Lista -> {
            val base =
                stringResource(
                    R.string.navegacion_ui_ruta_datos,
                    formatearDuracionRuta(opcion.tiempoS),
                    formatearDistanciaManiobra(opcion.distanciaM),
                )
            opcion.curvasM?.let { "$base, ${textoCurvas(it)}" } ?: base
        }

        OpcionRuta.SinDatos -> {
            stringResource(R.string.navegacion_ui_ruta_sin_datos)
        }

        is OpcionRuta.Error -> {
            stringResource(R.string.navegacion_ui_ruta_error)
        }
    }
