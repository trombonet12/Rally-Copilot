@file:Suppress("TooManyFunctions") // panel, chip, esqueleto, barra de curvas y formatos de una sola pieza

package app.trombito.rallycopilot.ui.navegacion

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.ui.comun.EsqueletoWrc
import app.trombito.rallycopilot.ui.comun.FormatoEs
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.LADO_TACTIL
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.comun.VarianteTarjeta
import app.trombito.rallycopilot.ui.maniobras.formatearDistanciaManiobra
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Tiempo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val ETIQUETA_VISTA_PREVIA = "nav-vista-previa"
const val ETIQUETA_RUTA_RAPIDA = "nav-ruta-rapida"
const val ETIQUETA_RUTA_EQUILIBRADA = "nav-ruta-equilibrada"
const val ETIQUETA_RUTA_SINUOSA = "nav-ruta-sinuosa"
const val ETIQUETA_SIN_POSICION = "nav-sin-posicion"
const val ETIQUETA_SIN_RUTA = "nav-sin-ruta"
const val ETIQUETA_REINTENTAR_RUTA = "nav-reintentar-ruta"

/** Prefijo de la barra de curvas de cada ruta (+ el perfil en minúsculas) y de su relleno ámbar (+ `-relleno`). */
const val ETIQUETA_BARRA_CURVAS = "nav-barra-curvas-"

/** Prefijo de la hora de llegada de cada ruta (+ el perfil en minúsculas). */
const val ETIQUETA_HORA_LLEGADA = "nav-hora-llegada-"

private const val OPACIDAD_NO_DISPONIBLE = 0.6f
private const val OPACIDAD_ICONO_TENUE = 0.6f
private const val MIN_CURVAS_M = 100.0
private val ALTO_OPCION = 64.dp
private val ANCHO_MAXIMO_PANEL = 520.dp
private val GROSOR_SELECCION = 2.dp
private val ALTO_BARRA_CURVAS = 6.dp
private val ALTO_ESQUELETO_TIEMPO = 26.dp
private val ALTO_ESQUELETO_DATO = 12.dp
private const val FRACCION_ESQUELETO_TIEMPO = 0.7f
private const val FRACCION_ESQUELETO_DATO = 0.5f
private const val METROS_POR_KM = 1000.0
private const val OPACIDAD_PISTA_BARRA = 0.2f
private const val MS_POR_S = 1000.0
private val FORMATO_HORA_LLEGADA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val CIFRA_TIEMPO_RUTA = Tiempo.copy(fontSize = 26.sp, lineHeight = 28.sp)
private val UNIDAD_TIEMPO_RUTA = SpanStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Normal)

/** La hora (24 h, «14:32») a la que se llega si se sale en [ahoraMs] y se tarda [tiempoS] segundos. */
internal fun formatearHoraLlegada(
    ahoraMs: Long,
    tiempoS: Double,
    zona: ZoneId = ZoneId.systemDefault(),
): String =
    Instant
        .ofEpochMilli(ahoraMs + (tiempoS.coerceAtLeast(0.0) * MS_POR_S).toLong())
        .atZone(zona)
        .format(FORMATO_HORA_LLEGADA)

/** «23 min» o «1 h 15 min»: cifras grandes (tabulares) y unidades pequeñas, para que quepan tres en una fila. */
internal fun duracionConUnidades(
    texto: String,
    unidad: SpanStyle = UNIDAD_TIEMPO_RUTA,
): AnnotatedString =
    buildAnnotatedString {
        texto.forEach { caracter ->
            if (caracter.isDigit()) append(caracter) else withStyle(unidad) { append(caracter) }
        }
    }

/**
 * Panel inferior de la vista previa, en carbón (también en tema claro): los chips de ruta (rayo = rápida, carretera
 * suave = equilibrada, carretera con curvas = curvas) con tiempo, hora de llegada y barra de curvas, cancelar y
 * empezar. Un chip cuya ruta es la misma (por geometría) que la de uno anterior no se muestra: queda uno solo si todas
 * coinciden. En apaisado todo va en una sola fila para ocupar poca altura. [reloj] da la hora de salida con la que se
 * calcula la de llegada de cada ruta.
 */
@Composable
fun PanelVistaPrevia(
    vista: VistaPrevia,
    acciones: AccionesNavegacion,
    horizontal: Boolean,
    modifier: Modifier = Modifier,
    reloj: () -> Long = System::currentTimeMillis,
) {
    val descripcion = stringResource(R.string.navegacion_ui_rutas)
    val ahoraMs = remember(vista) { reloj() }
    TarjetaWrc(
        variante = VarianteTarjeta.MAPA,
        relleno = Espacio.s,
        modifier =
            modifier
                .widthIn(max = ANCHO_MAXIMO_PANEL)
                .fillMaxWidth()
                .testTag(ETIQUETA_VISTA_PREVIA)
                .semantics { contentDescription = descripcion },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
            ParadasDeLaVistaPrevia(vista, acciones, horizontal)
            if (horizontal) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Espacio.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BotonCancelarRuta(acciones.onCancelar)
                    Opciones(vista, acciones, ahoraMs, Modifier.weight(1f))
                    BotonEmpezar(acciones.onEmpezar, habilitado = vista.puedeEmpezar)
                }
            } else {
                Opciones(vista, acciones, ahoraMs, Modifier.fillMaxWidth())
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
    ahoraMs: Long,
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
        vista.perfilesVisibles.forEach { perfil ->
            OpcionRutaTarjeta(
                chip =
                    ChipRuta(
                        perfil = perfil,
                        opcion = vista.opciones[perfil] ?: OpcionRuta.Cargando,
                        seleccionada = vista.perfil == perfil,
                        ahoraMs = ahoraMs,
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

/** Lo que pinta un chip: su perfil, el resultado y si es el elegido. */
private data class ChipRuta(
    val perfil: PerfilRuta,
    val opcion: OpcionRuta,
    val seleccionada: Boolean,
    val ahoraMs: Long,
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
    val colores = MaterialTheme.colorScheme
    val nombre = stringResource(nombreRuta(perfil))
    val resumen = resumenOpcion(opcion, chip.ahoraMs)
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
            ContenidoOpcion(chip)
        }
    }
}

@Composable
private fun ContenidoOpcion(chip: ChipRuta) {
    when (val opcion = chip.opcion) {
        OpcionRuta.Cargando -> {
            EsqueletoOpcion()
        }

        is OpcionRuta.Lista -> {
            ContenidoLista(chip.perfil, opcion, chip.ahoraMs)
        }

        OpcionRuta.SinDatos -> {
            TextoNoDisponible(R.string.navegacion_ui_ruta_sin_datos)
        }

        is OpcionRuta.Error -> {
            TextoNoDisponible(R.string.navegacion_ui_ruta_error)
        }
    }
}

/** Mientras calcula: dos barras grises que laten con la forma del tiempo y de la distancia. */
@Composable
private fun EsqueletoOpcion() {
    val forma = RoundedCornerShape(ALTO_BARRA_CURVAS)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Espacio.xs),
    ) {
        EsqueletoWrc(Modifier.fillMaxWidth(FRACCION_ESQUELETO_TIEMPO), forma, ALTO_ESQUELETO_TIEMPO)
        EsqueletoWrc(Modifier.fillMaxWidth(FRACCION_ESQUELETO_DATO), forma, ALTO_ESQUELETO_DATO)
    }
}

/**
 * Una ruta calculada: tiempo en cifra WRC y la hora de llegada debajo; después la barra de proporción de curvas
 * («2,0 de 18,4 km») o, si no hay curvas medidas o casi no hay, la distancia a secas.
 */
@Composable
private fun ContenidoLista(
    perfil: PerfilRuta,
    opcion: OpcionRuta.Lista,
    ahoraMs: Long,
) {
    val colores = MaterialTheme.colorScheme
    val curvasM = opcion.curvasM
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = duracionConUnidades(formatearDuracionRuta(opcion.tiempoS)),
            style = CIFRA_TIEMPO_RUTA,
            color = colores.onSurface,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text =
                stringResource(
                    R.string.navegacion_ui_ruta_llegada,
                    formatearHoraLlegada(ahoraMs, opcion.tiempoS),
                ),
            style = MaterialTheme.typography.bodySmall,
            color = colores.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.testTag(ETIQUETA_HORA_LLEGADA + perfil.name.lowercase()),
        )
        if (curvasM != null && curvasM >= MIN_CURVAS_M) {
            BarraCurvas(perfil, curvasM, opcion.distanciaM)
        } else {
            Text(
                text = formatearDistanciaManiobra(opcion.distanciaM),
                style = MaterialTheme.typography.bodySmall,
                color = colores.onSurfaceVariant,
                maxLines = 1,
            )
            curvasM?.let { TextoCurvas(it) }
        }
    }
}

/** Tramo ámbar sobre gris con los km de curvas respecto al total: se ve cuál es la revirada sin leer cifras. */
@Composable
private fun BarraCurvas(
    perfil: PerfilRuta,
    curvasM: Double,
    distanciaM: Double,
) {
    val colores = MaterialTheme.colorScheme
    val fraccion = if (distanciaM > 0.0) (curvasM / distanciaM).toFloat().coerceIn(0f, 1f) else 0f
    val etiqueta = ETIQUETA_BARRA_CURVAS + perfil.name.lowercase()
    Column(
        modifier = Modifier.padding(top = Espacio.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Espacio.xs),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(ALTO_BARRA_CURVAS)
                .clip(RoundedCornerShape(ALTO_BARRA_CURVAS / 2))
                .background(colores.onSurface.copy(alpha = OPACIDAD_PISTA_BARRA))
                .testTag(etiqueta),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraccion)
                    .background(colores.primary)
                    .testTag("$etiqueta-relleno"),
            )
        }
        Text(
            text =
                stringResource(
                    R.string.navegacion_ui_ruta_curvas_de,
                    FormatoEs.decimal(curvasM / METROS_POR_KM),
                    FormatoEs.decimal(distanciaM / METROS_POR_KM),
                ),
            style = MaterialTheme.typography.bodySmall,
            color = colores.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** «Sin curvas» si la ruta no llega a 100 m de curvas. */
@Composable
private fun TextoCurvas(curvasM: Double) {
    Text(
        text = textoCurvas(curvasM),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // En dos líneas si no cabe: en una se cortaba («10,9 km de…») en móviles de 412 dp con los tres chips.
        maxLines = 2,
        textAlign = TextAlign.Center,
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
private fun resumenOpcion(
    opcion: OpcionRuta,
    ahoraMs: Long,
): String =
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
            val conCurvas = opcion.curvasM?.let { "$base, ${textoCurvas(it)}" } ?: base
            val llegada =
                stringResource(
                    R.string.navegacion_ui_ruta_llegada,
                    formatearHoraLlegada(ahoraMs, opcion.tiempoS),
                )
            "$conCurvas, $llegada"
        }

        OpcionRuta.SinDatos -> {
            stringResource(R.string.navegacion_ui_ruta_sin_datos)
        }

        is OpcionRuta.Error -> {
            stringResource(R.string.navegacion_ui_ruta_error)
        }
    }
