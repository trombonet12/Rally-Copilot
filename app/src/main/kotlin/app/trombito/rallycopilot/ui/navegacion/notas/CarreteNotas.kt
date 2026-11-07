package app.trombito.rallycopilot.ui.navegacion.notas

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.notas.NotaVisible
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.comun.VarianteTarjeta
import app.trombito.rallycopilot.ui.theme.AmbarSobreCarbon
import app.trombito.rallycopilot.ui.theme.CarbonOpaco
import app.trombito.rallycopilot.ui.theme.Movimiento
import app.trombito.rallycopilot.ui.theme.Radios
import app.trombito.rallycopilot.ui.theme.Rotulo

/** Contenedor del carrete (ocupa todo el ancho; no existe sin notas). */
const val ETIQUETA_FRANJA_NOTAS = "notas-franja"
const val ETIQUETA_NOTA_PREFIJO = "notas-fila-"
const val ETIQUETA_NOTA_CUIDADO_PREFIJO = "notas-cuidado-"

/** Distancia hasta la nota («120 m»); solo la lleva la nota actual: `notas-distancia-0`. */
const val ETIQUETA_NOTA_DISTANCIA_PREFIJO = "notas-distancia-"

/** Icono de un aviso unido (o del puente de la curva) de la nota: `notas-aviso-<fila>-<TIPO>`. */
const val ETIQUETA_NOTA_AVISO_PREFIJO = "notas-aviso-"

private const val FRACCION_TEXTO_MINIMO = 0.55f
private const val HOLGURA = 0.97f
private const val FRACCION_DISTANCIA = 0.6f
private const val FRACCION_INSIGNIA = 0.62f

private val SEPARACION_SIGUIENTE = 8.dp
private val ANCHO_SIGUIENTE = 136.dp
private const val TEXTO_MINIMO_SIGUIENTE_SP = 12f
private val LADO_INSIGNIA = 24.dp
private val LADO_INSIGNIA_COMPACTA = 20.dp

private val MEDIDAS_ACTUAL = Medidas(40.dp, 28.sp, 24.dp, Radios.formaTarjeta, 16.dp, 2, false, true)
private val MEDIDAS_ACTUAL_COMPACTA = Medidas(32.dp, 22.sp, 20.dp, Radios.formaTarjeta, 8.dp, 2, false, true)
private val MEDIDAS_SIGUIENTE = Medidas(28.dp, 16.sp, 20.dp, Radios.formaTarjeta, 8.dp, 2, true)
private val MEDIDAS_SIGUIENTE_COMPACTA = Medidas(22.dp, 14.sp, 16.dp, Radios.formaTarjeta, 6.dp, 2, true)

/**
 * Carrete de notas bajo el banner de maniobra (o arriba del todo en modo libre): la nota actual en una tarjeta de
 * carbón con su icono de roadbook, el texto en cursiva Black (de 28 sp, que se reduce antes de partirse), la distancia
 * hasta ella a la derecha («120 m») y una insignia ámbar «!» si pide cuidado; la que ya toca cantar
 * ([NotaVisible.activa]) lleva el borde ámbar. A su derecha va, más pequeña (carbón opaco) y entera, dentro de los
 * márgenes, la siguiente aún no cantada (texto de 12 sp como mínimo, hasta dos líneas). Al cantarse la actual, todo
 * el carrete se desliza hacia la izquierda (la siguiente pasa a ser la actual) como un carrete de
 * fotos. El texto es solo la curva: los avisos unidos y el
 * puente van como iconos; un aviso suelto (cruce, rasante…) es una nota más. Con [compacta] (pantalla apaisada) las
 * tarjetas son más bajas. No pinta nada si no hay notas.
 */
@Composable
fun CarreteNotas(
    estado: NotasUiState,
    modifier: Modifier = Modifier,
    compacta: Boolean = false,
) {
    if (estado.visibles.isEmpty()) return
    AnimatedContent(
        targetState = estado,
        modifier = modifier.fillMaxWidth().clipToBounds().testTag(ETIQUETA_FRANJA_NOTAS),
        // Solo desliza cuando cambia la nota actual; el resto de cambios (distancia, activa) actualizan sin animar.
        contentKey = { it.visibles.firstOrNull()?.elemento },
        transitionSpec = {
            slideInHorizontally(tween(Movimiento.MEDIA)) { ancho -> ancho } togetherWith
                slideOutHorizontally(tween(Movimiento.MEDIA)) { ancho -> -ancho }
        },
        label = "carrete-notas",
    ) { visto ->
        Carrete(visto, compacta)
    }
}

@Composable
private fun Carrete(
    estado: NotasUiState,
    compacta: Boolean,
) {
    val actual = estado.visibles.first()
    val siguiente = estado.visibles.getOrNull(1)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // La siguiente cabe entera a la derecha: la actual cede su ancho (y reduce el texto antes de partirse).
        val ancho = if (siguiente != null) maxWidth - ANCHO_SIGUIENTE - SEPARACION_SIGUIENTE else maxWidth
        TarjetaActual(
            visible = actual,
            simplificadas = estado.simplificadas,
            compacta = compacta,
            modifier = Modifier.width(ancho),
        )
        if (siguiente != null) {
            TarjetaSiguiente(
                visible = siguiente,
                simplificadas = estado.simplificadas,
                compacta = compacta,
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = ancho + SEPARACION_SIGUIENTE)
                        .requiredWidth(ANCHO_SIGUIENTE),
            )
        }
    }
}

@Composable
private fun TarjetaActual(
    visible: NotaVisible,
    simplificadas: Boolean,
    compacta: Boolean,
    modifier: Modifier,
) {
    val medidas = if (compacta) MEDIDAS_ACTUAL_COMPACTA else MEDIDAS_ACTUAL
    val recursos = LocalContext.current.resources
    val elemento = visible.elemento
    val cuidado = esCuidado(elemento)
    val color = colorNota(cuidado, visible.activa)
    val descripcion = descripcionNota(recursos, visible, simplificadas)
    val ahora = stringResource(R.string.notas_ahora)
    TarjetaWrc(
        modifier =
            modifier
                .testTag(ETIQUETA_NOTA_PREFIJO + 0)
                .semantics(mergeDescendants = true) {
                    contentDescription = descripcion
                    if (visible.activa) stateDescription = ahora
                },
        variante = VarianteTarjeta.MAPA,
        activa = visible.activa,
        relleno = medidas.relleno,
        opaca = true,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconosDeLaNota(elemento, 0, simplificadas, color, medidas)
            TextoRotulo(
                texto = textoNota(recursos, elemento, simplificadas, color, medidas.abreviar),
                color = color,
                medidas = medidas,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.notas_distancia_nota, distanciaRedondeadaM(visible.distanciaM)),
                color = color,
                style = Rotulo.copy(fontSize = medidas.texto * FRACCION_DISTANCIA, lineHeight = TextUnit.Unspecified),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.testTag(ETIQUETA_NOTA_DISTANCIA_PREFIJO + 0),
            )
            if (cuidado) {
                InsigniaCuidado(
                    lado = if (compacta) LADO_INSIGNIA_COMPACTA else LADO_INSIGNIA,
                    modifier = Modifier.testTag(ETIQUETA_NOTA_CUIDADO_PREFIJO + 0),
                )
            }
        }
    }
}

@Composable
private fun TarjetaSiguiente(
    visible: NotaVisible,
    simplificadas: Boolean,
    compacta: Boolean,
    modifier: Modifier,
) {
    val medidas = if (compacta) MEDIDAS_SIGUIENTE_COMPACTA else MEDIDAS_SIGUIENTE
    val recursos = LocalContext.current.resources
    val elemento = visible.elemento
    val color = colorNota(esCuidado(elemento), false)
    val descripcion = descripcionNota(recursos, visible, simplificadas)
    TarjetaWrc(
        modifier =
            modifier.testTag(ETIQUETA_NOTA_PREFIJO + 1).semantics(mergeDescendants = true) {
                contentDescription = descripcion
            },
        variante = VarianteTarjeta.MAPA,
        relleno = medidas.relleno,
        opaca = true,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconosDeLaNota(elemento, 1, simplificadas, color, medidas)
            TextoRotulo(
                texto = textoNota(recursos, elemento, simplificadas, color, medidas.abreviar),
                color = color,
                medidas = medidas,
                modifier = Modifier.weight(1f),
                minimoSp = TEXTO_MINIMO_SIGUIENTE_SP,
            )
        }
    }
}

/**
 * Texto de la nota en el estilo [Rotulo], ajustado de una sola vez (se mide antes de pintar, sin parpadeo): si no cabe
 * en una línea se reduce la fuente hasta el 55 %; ya en el mínimo admite [Medidas.lineas] líneas y, si aun así no cabe,
 * se recorta con «…».
 */
@Composable
private fun TextoRotulo(
    texto: AnnotatedString,
    color: Color,
    medidas: Medidas,
    modifier: Modifier = Modifier,
    minimoSp: Float = 0f,
) {
    val medidor = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val disponible = constraints.maxWidth
        val completo = medidas.texto.value
        val minimo = maxOf(completo * FRACCION_TEXTO_MINIMO, minimoSp)
        val tamano =
            remember(texto, medidas, disponible) {
                val ancho =
                    medidor
                        .measure(texto, estiloRotulo(completo), softWrap = false, maxLines = 1)
                        .size.width
                if (ancho <= disponible) completo else (completo * disponible / ancho * HOLGURA).coerceAtLeast(minimo)
            }
        Text(
            text = texto,
            color = color,
            style = estiloRotulo(tamano),
            maxLines = if (tamano <= minimo) medidas.lineas else 1,
            textAlign = TextAlign.Start,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun estiloRotulo(tamano: Float): TextStyle = Rotulo.copy(fontSize = tamano.sp, lineHeight = (tamano * 1.1f).sp)

/** Círculo ámbar con «!»: la nota pide precaución (resalto, badén, cuidado). */
@Composable
private fun InsigniaCuidado(
    lado: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(lado).background(AmbarSobreCarbon, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.notas_insignia_cuidado),
            color = CarbonOpaco,
            style = estiloRotulo(lado.value * FRACCION_INSIGNIA).copy(fontWeight = FontWeight.Black),
            maxLines = 1,
        )
    }
}
