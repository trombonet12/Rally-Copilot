package app.trombito.rallycopilot.ui.navegacion.notas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.NotaVisible
import app.trombito.rallycopilot.motor.notas.TipoAviso
import app.trombito.rallycopilot.ui.maniobras.ParalelogramoShape
import app.trombito.rallycopilot.ui.navegacion.guiado.ColoresGuiado
import app.trombito.rallycopilot.ui.theme.RallyOscuro

internal const val OPACIDAD_FONDO = 0.94f
private const val REDUCCION_FUENTE = 0.92f
private const val FRACCION_TEXTO_MINIMO = 0.7f
private const val FRACCION_TEXTO_MINIMO_CON_DISTANCIA = 0.65f
private const val FRACCION_TEXTO_DISTANCIA = 0.6f
internal val GRIS_BORDE = Color(0xFF80858D)
internal val FONDO_ACTIVA = Color(0xFF2A2008)
private val SESGO = 10.dp
private val SESGO_COMPACTO = 6.dp
private val FORMA_PEQUENA = RoundedCornerShape(10.dp)

/**
 * Medidas de una fila: la primera nota va grande y las siguientes en pequeño. [lineas] es el máximo de líneas del
 * texto, que antes de recortarse reduce la fuente hasta el 70 % (65 % con distancia, que le quita sitio); las
 * pequeñas [abreviar] (IZQ, DER, HORQ). Solo la grande y la compacta (la primera nota) escriben la [distancia].
 */
@Suppress("LongParameterList") // todas las medidas de una fila, fijas por diseño
internal class Medidas(
    val icono: Dp,
    val texto: TextUnit,
    val avisoUnido: Dp,
    val forma: Shape,
    val relleno: Dp,
    val lineas: Int,
    val abreviar: Boolean,
    val distancia: Boolean = false,
) {
    val textoMinimo: TextUnit
        get() = texto * if (distancia) FRACCION_TEXTO_MINIMO_CON_DISTANCIA else FRACCION_TEXTO_MINIMO
}

internal val MEDIDAS_GRANDE = Medidas(48.dp, 24.sp, 28.dp, ParalelogramoShape(SESGO), SESGO + 8.dp, 2, false, true)
internal val MEDIDAS_COMPACTA =
    Medidas(32.dp, 20.sp, 22.dp, ParalelogramoShape(SESGO_COMPACTO), SESGO_COMPACTO + 8.dp, 1, false, true)
internal val FORMA_ENORME = RoundedCornerShape(24.dp)
internal val MEDIDAS_ENORME = Medidas(200.dp, 44.sp, 64.dp, FORMA_ENORME, 24.dp, 2, false, true)
internal val MEDIDAS_ENORME_APAISADA = Medidas(116.dp, 36.sp, 40.dp, FORMA_ENORME, 16.dp, 2, false, true)
internal val MEDIDAS_ROADBOOK_LADO =
    Medidas(36.dp, 20.sp, 24.dp, ParalelogramoShape(SESGO_COMPACTO), SESGO_COMPACTO + 8.dp, 1, true, true)
internal val MEDIDAS_PEQUENA = Medidas(28.dp, 15.sp, 20.dp, FORMA_PEQUENA, 8.dp, 2, true)

@Composable
internal fun FilaNota(
    visible: NotaVisible,
    indice: Int,
    simplificadas: Boolean,
    medidas: Medidas,
    modifier: Modifier,
) {
    val recursos = LocalContext.current.resources
    val elemento = visible.elemento
    val cuidado = esCuidado(elemento)
    val color = colorNota(cuidado, visible.activa)
    val borde = if (visible.activa) ColoresGuiado.Laton else GRIS_BORDE
    val fondo = if (visible.activa) FONDO_ACTIVA else ColoresGuiado.Carbon
    val descripcion = descripcionNota(recursos, visible, simplificadas)
    val ahora = stringResource(R.string.notas_ahora)
    Row(
        modifier =
            modifier
                .testTag(ETIQUETA_NOTA_PREFIJO + indice)
                .background(fondo.copy(OPACIDAD_FONDO), medidas.forma)
                .border(2.dp, borde, medidas.forma)
                .padding(horizontal = medidas.relleno, vertical = 4.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = descripcion
                    if (visible.activa) stateDescription = ahora
                },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconosDeLaNota(elemento, indice, simplificadas, color, medidas)
        TextoAjustado(
            texto = textoNota(recursos, elemento, simplificadas, medidas.abreviar),
            color = color,
            medidas = medidas,
            modifier = Modifier.weight(1f),
        )
        if (medidas.distancia) {
            DistanciaNota(
                distanciaM = visible.distanciaM,
                color = color,
                medidas = medidas,
                modifier = Modifier.testTag(ETIQUETA_NOTA_DISTANCIA_PREFIJO + indice),
            )
        }
        if (cuidado && elemento is ElementoHoja.DeNota) {
            Icono(
                recurso = R.drawable.ic_nota_cuidado,
                color = RallyOscuro.aviso,
                lado = medidas.avisoUnido,
                modifier = Modifier.testTag(ETIQUETA_NOTA_CUIDADO_PREFIJO + indice),
            )
        }
    }
}

/** Avisos unidos, icono de la curva y puente de la curva: lo que no va en el texto se ve solo como icono. */
@Composable
internal fun RowScope.IconosDeLaNota(
    elemento: ElementoHoja,
    indice: Int,
    simplificadas: Boolean,
    color: Color,
    medidas: Medidas,
) {
    if (elemento is ElementoHoja.DeNota) {
        elemento.avisosUnidos
            .filterNot { it.tipo == TipoAviso.CUIDADO }
            .forEach {
                Icono(
                    recurso = iconoAviso(it.tipo),
                    color = colorAviso(it.tipo, color),
                    lado = medidas.avisoUnido,
                    modifier = Modifier.testTag("$ETIQUETA_NOTA_AVISO_PREFIJO$indice-${it.tipo.name}"),
                )
            }
    }
    Icono(iconoElemento(elemento, simplificadas), color, medidas.icono)
    if (elemento is ElementoHoja.DeNota && elemento.sobrePuente != null) {
        Icono(
            recurso = iconoAviso(TipoAviso.PUENTE),
            color = color,
            lado = medidas.avisoUnido,
            modifier = Modifier.testTag("$ETIQUETA_NOTA_AVISO_PREFIJO$indice-${TipoAviso.PUENTE.name}"),
        )
    }
}

/** Texto de la nota: reduce la fuente (hasta el mínimo de [medidas]) antes de recortar con «…». */
@Composable
internal fun TextoAjustado(
    texto: String,
    color: Color,
    medidas: Medidas,
    modifier: Modifier = Modifier,
    alineacion: TextAlign = TextAlign.Unspecified,
) {
    var tamano by remember(texto, medidas) { mutableStateOf(medidas.texto) }
    Text(
        text = texto,
        color = color,
        style =
            MaterialTheme.typography.titleLarge.copy(
                fontSize = tamano,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
            ),
        maxLines = medidas.lineas,
        textAlign = alineacion,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = {
            if (it.hasVisualOverflow && tamano.value > medidas.textoMinimo.value) {
                tamano = (tamano.value * REDUCCION_FUENTE).coerceAtLeast(medidas.textoMinimo.value).sp
            }
        },
        modifier = modifier,
    )
}

/** «120 m»: a cuánto está la nota, en una línea y más pequeña que su texto. TalkBack ya la lee en la descripción. */
@Composable
private fun DistanciaNota(
    distanciaM: Double,
    color: Color,
    medidas: Medidas,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(R.string.notas_distancia_nota, distanciaRedondeadaM(distanciaM)),
        color = color,
        style =
            MaterialTheme.typography.titleMedium.copy(
                fontSize = medidas.texto * FRACCION_TEXTO_DISTANCIA,
                fontWeight = FontWeight.Bold,
            ),
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
    )
}

@Composable
internal fun Icono(
    recurso: Int,
    color: Color,
    lado: Dp,
    modifier: Modifier = Modifier,
) {
    Icon(painterResource(recurso), contentDescription = null, tint = color, modifier = modifier.size(lado))
}

/** Pide precaución: la nota lleva la marca o alguno de sus avisos es de «cuidado» (resalto, badén, cuidado). */
internal fun esCuidado(elemento: ElementoHoja): Boolean =
    when (elemento) {
        is ElementoHoja.DeNota -> elemento.nota.cuidado || elemento.avisosUnidos.any { it.tipo.esCuidado }
        is ElementoHoja.DeAviso -> elemento.aviso.tipo.esCuidado
    }

private fun colorAviso(
    tipo: TipoAviso,
    normal: Color,
): Color = if (tipo.esCuidado) RallyOscuro.aviso else normal

/** Ámbar si pide precaución, latón si toca cantarla ya, y blanco roto en los demás casos. */
internal fun colorNota(
    cuidado: Boolean,
    activa: Boolean,
): Color =
    when {
        cuidado -> RallyOscuro.aviso
        activa -> ColoresGuiado.Laton
        else -> ColoresGuiado.Texto
    }
