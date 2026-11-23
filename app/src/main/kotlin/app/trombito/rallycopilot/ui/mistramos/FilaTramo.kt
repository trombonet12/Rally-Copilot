package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.tramos.ResumenTramo
import app.trombito.rallycopilot.domain.tramos.TipoTramo
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Cuerpo
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Radios
import app.trombito.rallycopilot.ui.theme.Rotulo
import app.trombito.rallycopilot.ui.tramos.formatearTiempo
import app.trombito.rallycopilot.ui.tutorial.ConPista
import app.trombito.rallycopilot.ui.tutorial.LadoPista
import app.trombito.rallycopilot.ui.tutorial.LocalPistas
import app.trombito.rallycopilot.ui.tutorial.Pista

const val ETIQUETA_FILA_PREFIJO = "tramo-fila-"
const val ETIQUETA_FAVORITO_PREFIJO = "tramo-favorito-"
const val ETIQUETA_TIEMPO_PREFIJO = "tramo-tiempo-"

private val LADO_TACTIL = 48.dp
private val ANCHO_BARRA_FAVORITO = 3.dp
private val LADO_MINIATURA = 56.dp
private const val LINEAS_NOMBRE = 2

/**
 * Fila que se borra deslizando hacia la izquierda; también por la acción de accesibilidad «Borrar». Con
 * [conCarretera] el tramo de vía se titula con su nombre completo («Ma-15 · km 20,1→32,4 · hacia Manacor»); sin ella
 * (bajo la cabecera de su carretera) solo con sus kilómetros.
 */
@Suppress("LongParameterList") // API de componente: la traza y la pista son opcionales
@Composable
fun FilaTramoDeslizable(
    tramo: ResumenTramo,
    acciones: AccionesMisTramos,
    modifier: Modifier = Modifier,
    traza: List<PuntoGeo>? = null,
    conPista: Boolean = false,
    conCarretera: Boolean = false,
) {
    val estado =
        rememberSwipeToDismissBoxState(
            confirmValueChange = {
                val borrar = it == SwipeToDismissBoxValue.EndToStart
                if (borrar) acciones.onBorrar(tramo)
                borrar
            },
        )
    SwipeToDismissBox(
        state = estado,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = { FondoBorrar() },
    ) {
        FilaTramo(tramo, traza, acciones, conPista, conCarretera)
    }
}

@Composable
private fun FondoBorrar() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.errorContainer, Radios.formaTarjeta)
                .padding(horizontal = Espacio.l),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(Iconos.Borrar, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun FilaTramo(
    tramo: ResumenTramo,
    traza: List<PuntoGeo>?,
    acciones: AccionesMisTramos,
    conPista: Boolean,
    conCarretera: Boolean,
) {
    val borrarTexto = stringResource(R.string.mistramos_borrar)
    val renombrarTexto = stringResource(R.string.mistramos_renombrar)
    val pistas = LocalPistas.current
    val colorBarra = MaterialTheme.colorScheme.primary
    val barraPx = with(LocalDensity.current) { ANCHO_BARRA_FAVORITO.toPx() }
    TarjetaWrc(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(Radios.formaTarjeta)
                .drawWithContent {
                    drawContent()
                    if (tramo.favorito) drawRect(colorBarra, Offset.Zero, Size(barraPx, size.height))
                }.testTag(ETIQUETA_FILA_PREFIJO + tramo.id)
                .semantics {
                    customActions =
                        listOf(
                            CustomAccessibilityAction(borrarTexto) {
                                acciones.onBorrar(tramo)
                                true
                            },
                            CustomAccessibilityAction(renombrarTexto) {
                                acciones.onPedirRenombrar(tramo)
                                true
                            },
                        )
                },
        relleno = Espacio.m,
        onClick = { acciones.onAbrir(tramo) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.m)) {
            Miniatura(traza)
            DatosTramo(tramo, conCarretera, Modifier.weight(1f))
            TiempoTramo(tramo)
            ConPista(Pista.MIS_TRAMOS, stringResource(R.string.pista_mis_tramos), activa = conPista) {
                BotonFavorito(
                    favorito = tramo.favorito,
                    onClick = {
                        // Usar la estrella ya es haber entendido la ayuda.
                        pistas.entendida(Pista.MIS_TRAMOS)
                        acciones.onFavorito(tramo)
                    },
                    modifier = Modifier.testTag(ETIQUETA_FAVORITO_PREFIJO + tramo.id),
                )
            }
        }
    }
}

/** Silueta del tramo; mientras se lee (o si no se puede), un hueco con la bandera. */
@Composable
private fun Miniatura(traza: List<PuntoGeo>?) {
    if (traza != null) {
        MiniaturaTraza(traza, LADO_MINIATURA)
    } else {
        Box(
            Modifier
                .size(LADO_MINIATURA)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest, Radios.formaTarjeta),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Iconos.Bandera, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

/** Nombre y, debajo, «12,3 km · 68 km/h»: lo que se ve de un vistazo; el resto está en el detalle. */
@Composable
private fun DatosTramo(
    tramo: ResumenTramo,
    conCarretera: Boolean,
    modifier: Modifier = Modifier,
) {
    // Bajo la cabecera de su carretera, un tramo de vía sin nombre propio se titula por sus kilómetros.
    val rango = if (tramo.tipo == TipoTramo.VIA && !conCarretera) textoRangoVia(tramo) else null
    val titulo = if (rango != null && tramo.nombrePropio == null) rango else tramo.nombre
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
        Text(
            text = titulo,
            style = Cuerpo.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = LINEAS_NOMBRE,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = textoMedidas(tramo),
            style = Ayuda,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** «12,3 km · 68 km/h»; sin ninguna pasada aún no hay velocidad y solo sale la distancia. */
@Composable
private fun textoMedidas(tramo: ResumenTramo): String {
    val distancia = formatearDistanciaTramo(tramo.longitudM)
    val velocidad = tramo.velocidadMediaKmh()?.let { formatearVelocidadKmh(it) } ?: return distancia
    return stringResource(R.string.mistramos_medidas, distancia, velocidad)
}

/** «km 20,1→32,4» de un tramo de vía con hitos; sin ellos, null (el título es el nombre). */
@Composable
private fun textoRangoVia(tramo: ResumenTramo): String? {
    val desde = tramo.via?.pkDesde
    val hasta = tramo.via?.pkHasta
    return if (desde != null && hasta != null) {
        stringResource(R.string.mistramos_via_rango, formatearPk(desde), formatearPk(hasta))
    } else {
        null
    }
}

/**
 * El tiempo, en segundo plano. Con una sola pasada es un dato suelto que no compara con nada: va apagado y pequeño.
 * Desde dos, el mejor tiempo sí cuenta y va en ámbar y más grande.
 */
@Composable
private fun TiempoTramo(tramo: ResumenTramo) {
    val mejorMs = tramo.mejorTotalMs
    val tiempo = mejorMs?.let { formatearTiempo(it) } ?: stringResource(R.string.mistramos_sin_tiempo)
    val destacado = mejorMs != null && tramo.pasadas >= 2
    Text(
        text = tiempo,
        style =
            if (destacado) {
                Rotulo.copy(fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = 0.sp)
            } else {
                Ayuda
            },
        color = if (destacado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier.testTag(ETIQUETA_TIEMPO_PREFIJO + tramo.id),
    )
}

/** Estrella de favorito: llena en latón si lo es, con borde si no. */
@Composable
fun BotonFavorito(
    favorito: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color: Color =
        if (favorito) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    IconButton(onClick = onClick, modifier = modifier.size(LADO_TACTIL)) {
        Icon(
            imageVector = if (favorito) Iconos.Estrella else Iconos.EstrellaVacia,
            contentDescription =
                stringResource(
                    if (favorito) R.string.mistramos_quitar_favorito else R.string.mistramos_marcar_favorito,
                ),
            tint = color,
        )
    }
}
