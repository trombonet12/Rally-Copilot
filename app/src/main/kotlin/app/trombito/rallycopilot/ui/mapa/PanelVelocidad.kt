package app.trombito.rallycopilot.ui.mapa

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.ui.alertas.AlertasPanel
import app.trombito.rallycopilot.ui.alertas.MediaTramoUi
import app.trombito.rallycopilot.ui.comun.PlacaCarretera
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.LocalColoresRally

/** Etiquetas de prueba del panel. */
const val ETIQUETA_VELOCIDAD = "panel-velocidad"
const val ETIQUETA_LIMITE = "panel-limite"
const val ETIQUETA_VIA = "panel-via"

/** Nodo de accesibilidad de «por encima del límite» sobre la velocidad (existe solo mientras va en rojo). */
const val ETIQUETA_EXCESO = "panel-exceso"

/** Media del radar de tramo junto a la señal del límite. */
const val ETIQUETA_MEDIA = "panel-media"

private val FondoSenal = Color.White
private val TextoSenal = Color.Black

/**
 * Velocidad actual (siempre visible; «—» sin dato), señal de límite (solo si [limiteKmh] no es null) y la placa de la
 * vía. En vertical va en una fila bajo el mapa, con [botonesIzquierda] y [botonesDerecha] (columnas de botones de
 * icono) a los lados de la velocidad; en horizontal ([enColumna]) en una columna al lado, con todos los botones en
 * una fila al pie. [alertas] pone la velocidad en rojo si pasa del límite (si pasa del límite más un 10 %, la cifra va
 * en blanco sobre una pastilla roja que late) y la media del radar de tramo junto a la señal del límite.
 *
 * Con [compacto] (parado planificando una ruta) el panel ocupa lo mínimo para dejar más mapa: la velocidad pequeña, el
 * límite en una señal chica y los botones en una sola fila, sin la placa de la vía ni la media del radar; el cambio de
 * tamaño es animado. [pie] es una línea opcional bajo la placa de la vía (el cronómetro pequeño de la conducción);
 * el panel compacto no la lleva.
 */
@Composable
@Suppress("LongParameterList", "LongMethod") // datos de conducción, grupos de botones, alertas y las dos variantes
fun PanelVelocidad(
    velocidadKmh: Int?,
    limiteKmh: Int?,
    via: ViaActual?,
    enColumna: Boolean,
    modifier: Modifier = Modifier,
    botonesIzquierda: List<@Composable () -> Unit> = emptyList(),
    botonesDerecha: List<@Composable () -> Unit> = emptyList(),
    alertas: AlertasPanel = AlertasPanel(),
    compacto: Boolean = false,
    pie: (@Composable () -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier.testTag("panel"),
    ) {
        Box(Modifier.animateContentSize()) {
            if (compacto) {
                ContenidoCompacto(
                    DatosCompactos(velocidadKmh, limiteKmh, alertas, botonesIzquierda, botonesDerecha),
                    enColumna,
                )
            } else {
                val contenido: @Composable () -> Unit = {
                    Velocidad(velocidadKmh, alertas)
                    BloqueLimite(limiteKmh, alertas.tramo, enFila = enColumna)
                }
                val rotulo: @Composable () -> Unit = {
                    RotuloVia(refsDePlaca(via?.ref), via?.nombre?.trim()?.takeIf { it.isNotEmpty() }, enColumna)
                }
                if (enColumna) {
                    Column(
                        modifier = Modifier.padding(horizontal = Espacio.s, vertical = Espacio.m),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Espacio.s, Alignment.CenterVertically),
                    ) {
                        contenido()
                        rotulo()
                        pie?.invoke()
                        // Hasta cuatro botones de 48 dp en una sola fila: no se apila otra fila bajo la velocidad.
                        Row(horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                            (botonesIzquierda + botonesDerecha).forEach { it() }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.padding(horizontal = Espacio.m, vertical = Espacio.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ColumnaBotones(botonesIzquierda, Alignment.Start)
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Espacio.s, Alignment.CenterVertically),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Espacio.m, Alignment.CenterHorizontally),
                            ) { contenido() }
                            rotulo()
                            pie?.invoke()
                        }
                        ColumnaBotones(botonesDerecha, Alignment.End)
                    }
                }
            }
        }
    }
}

/** Columna de botones de ancho fijo (cabe la bandera de 56 dp): la velocidad queda centrada con o sin botones. */
@Composable
private fun ColumnaBotones(
    botones: List<@Composable () -> Unit>,
    alineacion: Alignment.Horizontal,
) {
    Column(
        modifier = Modifier.width(ANCHO_COLUMNA_BOTONES),
        horizontalAlignment = alineacion,
        verticalArrangement = Arrangement.spacedBy(Espacio.s, Alignment.CenterVertically),
    ) { botones.forEach { it() } }
}

private val ANCHO_COLUMNA_BOTONES = 56.dp

/** Los `ref` de OSM separados por «;», sin vacíos ni repetidos y como mucho [MAX_PLACAS]. */
internal fun refsDePlaca(ref: String?): List<String> =
    ref
        .orEmpty()
        .split(';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(MAX_PLACAS)

/**
 * Bajo la velocidad: una placa por cada `ref` y, si hay, el nombre en texto pequeño. En vertical el nombre va junto a
 * las placas y se recorta si no cabe; en horizontal (columna estrecha) va debajo. Sin `ref`, solo el nombre.
 */
@Composable
private fun RotuloVia(
    refs: List<String>,
    nombre: String?,
    enColumna: Boolean,
) {
    if (refs.isEmpty() && nombre == null) return
    val textoNombre: @Composable (Modifier, Int) -> Unit = { modificador, lineas ->
        Text(
            text = nombre.orEmpty(),
            style =
                if (refs.isEmpty()) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = lineas,
            overflow = TextOverflow.Ellipsis,
            modifier = modificador.testTag(ETIQUETA_VIA),
        )
    }
    val placas: @Composable () -> Unit = {
        if (enColumna) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Espacio.xs),
            ) { refs.forEach { PlacaCarretera(it) } }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) { refs.forEach { PlacaCarretera(it) } }
        }
    }
    when {
        refs.isEmpty() -> {
            textoNombre(Modifier, 2)
        }

        nombre == null -> {
            placas()
        }

        enColumna -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Espacio.xs),
            ) {
                placas()
                textoNombre(Modifier, 2)
            }
        }

        else -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Espacio.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                placas()
                textoNombre(Modifier.weight(1f, fill = false), 1)
            }
        }
    }
}

private const val MAX_PLACAS = 2

/** Color de la cifra de velocidad: el rojo de aviso si pasa del límite, el normal si no. */
internal fun colorVelocidad(
    rojo: Boolean,
    normal: Color,
    aviso: Color,
): Color = if (rojo) aviso else normal

@Composable
internal fun Velocidad(
    velocidadKmh: Int?,
    alertas: AlertasPanel,
    compacto: Boolean = false,
) {
    val colores = LocalColoresRally.current
    val rojo = alertas.rojo && velocidadKmh != null
    val exceso = rojo && alertas.exceso
    val descripcion =
        if (velocidadKmh == null) {
            stringResource(R.string.panel_velocidad_sin_dato)
        } else {
            stringResource(R.string.panel_velocidad_descripcion, velocidadKmh)
        }
    // Sin exceso la cifra solo se tiñe de rojo; con exceso va en blanco sobre una pastilla roja sólida que late.
    val cifra =
        if (exceso) {
            TextoExceso
        } else {
            colorVelocidad(
                rojo,
                MaterialTheme.colorScheme.onSurface,
                colores.deltaPeor,
            )
        }
    val unidad =
        if (exceso) TextoExceso else colorVelocidad(rojo, MaterialTheme.colorScheme.onSurfaceVariant, colores.deltaPeor)
    Box(if (exceso) Modifier.pastillaLatiente(FondoExceso) else Modifier) {
        val modificador =
            Modifier
                .padding(horizontal = Espacio.xs, vertical = Espacio.xs)
                .testTag(ETIQUETA_VELOCIDAD)
                .semantics(mergeDescendants = true) { contentDescription = descripcion }
        val texto = velocidadKmh?.toString() ?: stringResource(R.string.panel_velocidad_vacia)
        TextoVelocidad(texto, cifra, unidad, compacto, modificador)
        // Nodo propio sobre la velocidad: su región viva se anuncia al aparecer el rojo, no con cada cifra.
        if (rojo) {
            val aviso = stringResource(R.string.panel_exceso_descripcion)
            Box(
                Modifier
                    .matchParentSize()
                    .testTag(ETIQUETA_EXCESO)
                    .semantics {
                        contentDescription = aviso
                        liveRegion = LiveRegionMode.Polite
                    },
            )
        }
    }
}

/** Pastilla redondeada de color sólido cuya opacidad late suave (el texto encima no cambia). */
@Composable
private fun Modifier.pastillaLatiente(color: Color): Modifier {
    val alfa by
        rememberInfiniteTransition(label = "exceso").animateFloat(
            initialValue = 1f,
            targetValue = ALFA_MINIMO_LATIDO,
            animationSpec = infiniteRepeatable(tween(MS_LATIDO, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "alfa",
        )
    return drawBehind {
        val radio = RADIO_PASTILLA_EXCESO.toPx()
        drawRoundRect(color = color, cornerRadius = CornerRadius(radio, radio), alpha = alfa)
    }
}

/** Rojo sólido de la pastilla de exceso (fijo en ambos temas: el blanco encima da más de 5:1). */
private val FondoExceso = Color(0xFFC8171E)
private val TextoExceso = Color.White
private const val ALFA_MINIMO_LATIDO = 0.7f
private const val MS_LATIDO = 700
private val RADIO_PASTILLA_EXCESO = 16.dp
private val LADO_SENAL = 92.dp
internal val LADO_SENAL_CON_MEDIA = 76.dp

/** Señal del límite y, si hay un radar de tramo en curso, su media justo debajo (o al lado en la columna lateral). */
@Composable
private fun BloqueLimite(
    limiteKmh: Int?,
    tramo: MediaTramoUi?,
    enFila: Boolean,
) {
    if (limiteKmh == null && tramo == null) return
    val lado = if (tramo != null) LADO_SENAL_CON_MEDIA else LADO_SENAL
    val contenido: @Composable () -> Unit = {
        if (limiteKmh != null) SenalLimite(limiteKmh, lado)
        if (tramo != null) MediaTramo(tramo)
    }
    if (enFila) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Espacio.s),
            verticalAlignment = Alignment.CenterVertically,
        ) { contenido() }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Espacio.xs),
        ) { contenido() }
    }
}

/**
 * «MEDIA 78»: velocidad media del radar de tramo, en rojo si supera el límite; con el tramo ya terminado la etiqueta
 * es «FINAL» (se ve unos segundos). Sin media todavía (menos de 100 m recorridos) lleva una raya.
 */
@Composable
private fun MediaTramo(tramo: MediaTramoUi) {
    val colores = LocalColoresRally.current
    val color = if (tramo.supera) colores.deltaPeor else colores.aviso
    val forma = RoundedCornerShape(MEDIA_RADIO)
    val media = tramo.mediaKmh
    val base =
        when {
            media == null -> stringResource(R.string.panel_media_sin_dato)
            tramo.terminado -> stringResource(R.string.panel_media_final_descripcion, media)
            else -> stringResource(R.string.panel_media_descripcion, media)
        }
    val descripcion = if (tramo.supera) stringResource(R.string.panel_media_supera, base) else base
    val etiqueta = if (tramo.terminado) R.string.panel_media_final_etiqueta else R.string.panel_media_etiqueta
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .width(LADO_SENAL_CON_MEDIA)
                .testTag(ETIQUETA_MEDIA)
                .background(FondoMedia, forma)
                .border(MEDIA_BORDE, color, forma)
                .padding(vertical = Espacio.xs)
                .semantics(mergeDescendants = true) { contentDescription = descripcion },
    ) {
        Text(
            text = stringResource(etiqueta),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            color = color,
        )
        Text(
            text = media?.toString() ?: stringResource(R.string.panel_media_vacia),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = if (tramo.supera) colores.deltaPeor else TextoMedia,
        )
    }
}

private val FondoMedia = Color(0xFF0E0F12)
private val TextoMedia = Color(0xFFECEDEF)
private val MEDIA_RADIO = 10.dp
private val MEDIA_BORDE = 3.dp

/** Señal circular de límite: aro rojo, fondo blanco y cifra negra (colores de la señal real, no del tema). */
@Composable
internal fun SenalLimite(
    limiteKmh: Int,
    lado: Dp,
) {
    val descripcion = stringResource(R.string.panel_limite_descripcion, limiteKmh)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .testTag(ETIQUETA_LIMITE)
                .size(lado)
                .semantics { contentDescription = descripcion }
                .background(FondoSenal, CircleShape)
                .border(lado / RELACION_ARO_SENAL, LocalColoresRally.current.franja, CircleShape),
    ) {
        Text(
            text = limiteKmh.toString(),
            style =
                if (lado <
                    LADO_SENAL_CON_MEDIA
                ) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.headlineLarge
                },
            fontWeight = FontWeight.Black,
            color = TextoSenal,
            textAlign = TextAlign.Center,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

private const val RELACION_ARO_SENAL = 11.5f
