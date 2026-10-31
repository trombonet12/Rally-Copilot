package app.trombito.rallycopilot.ui.navegacion.notas

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.FrasesNota
import app.trombito.rallycopilot.motor.notas.NotaVisible
import app.trombito.rallycopilot.motor.notas.TipoAviso
import app.trombito.rallycopilot.ui.maniobras.ParalelogramoShape
import app.trombito.rallycopilot.ui.navegacion.guiado.ColoresGuiado
import app.trombito.rallycopilot.ui.theme.RallyOscuro
import kotlin.math.roundToInt

const val ETIQUETA_FRANJA_NOTAS = "notas-franja"
const val ETIQUETA_NOTA_PREFIJO = "notas-fila-"
const val ETIQUETA_NOTA_CUIDADO_PREFIJO = "notas-cuidado-"

private const val OPACIDAD_FONDO = 0.94f
private const val PASO_DISTANCIA_M = 10
private val GRIS_BORDE = Color(0xFF80858D)
private val FONDO_ACTIVA = Color(0xFF2A2008)
private val SESGO = 10.dp
private val FORMA_PEQUENA = RoundedCornerShape(10.dp)

/** Medidas de una fila: la primera nota va grande y las siguientes en pequeño. */
private class Medidas(
    val icono: Dp,
    val texto: TextUnit,
    val avisoUnido: Dp,
    val forma: Shape,
    val relleno: Dp,
)

private val MEDIDAS_GRANDE = Medidas(48.dp, 24.sp, 28.dp, ParalelogramoShape(SESGO), SESGO + 8.dp)
private val MEDIDAS_PEQUENA = Medidas(28.dp, 15.sp, 20.dp, FORMA_PEQUENA, 8.dp)

/**
 * Franja de notas bajo el banner de maniobra (o arriba del todo en modo libre): la próxima nota grande, con su icono de
 * roadbook, el texto en negrita cursiva y la distancia a la siguiente («DERECHA 4 LARGA · 50»), y las dos siguientes en
 * pequeño. La que ya toca cantar ([NotaVisible.activa]) va resaltada en latón y las notas de «cuidado» en ámbar. No
 * pinta nada si no hay notas.
 */
@Composable
fun FranjaNotas(
    estado: NotasUiState,
    modifier: Modifier = Modifier,
) {
    val primera = estado.visibles.firstOrNull() ?: return
    val siguientes = estado.visibles.drop(1)
    Column(
        modifier = modifier.fillMaxWidth().testTag(ETIQUETA_FRANJA_NOTAS),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FilaNota(primera, 0, estado.simplificadas, MEDIDAS_GRANDE, Modifier.fillMaxWidth())
        if (siguientes.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                siguientes.forEachIndexed { i, visible ->
                    FilaNota(visible, i + 1, estado.simplificadas, MEDIDAS_PEQUENA, Modifier.weight(1f))
                }
                // Con una sola nota pequeña no se estira a todo el ancho: ocupa la misma mitad que si hubiera dos.
                if (siguientes.size < 2) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FilaNota(
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
        if (elemento is ElementoHoja.DeNota) {
            elemento.avisosUnidos
                .filterNot { it.tipo == TipoAviso.CUIDADO && elemento.nota.cuidado }
                .forEach { Icono(iconoAviso(it.tipo), colorAviso(it.tipo, color), medidas.avisoUnido) }
        }
        Icono(iconoElemento(elemento, simplificadas), color, medidas.icono)
        Text(
            text = textoNota(recursos, elemento, simplificadas),
            color = color,
            style =
                MaterialTheme.typography.titleLarge.copy(
                    fontSize = medidas.texto,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
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

@Composable
private fun Icono(
    recurso: Int,
    color: Color,
    lado: Dp,
    modifier: Modifier = Modifier,
) {
    Icon(painterResource(recurso), contentDescription = null, tint = color, modifier = modifier.size(lado))
}

private fun esCuidado(elemento: ElementoHoja): Boolean =
    when (elemento) {
        is ElementoHoja.DeNota -> elemento.nota.cuidado
        is ElementoHoja.DeAviso -> elemento.aviso.tipo == TipoAviso.CUIDADO
    }

private fun colorAviso(
    tipo: TipoAviso,
    normal: Color,
): Color = if (tipo == TipoAviso.CUIDADO) RallyOscuro.aviso else normal

/** Ámbar si pide precaución, latón si toca cantarla ya, y blanco roto en los demás casos. */
private fun colorNota(
    cuidado: Boolean,
    activa: Boolean,
): Color =
    when {
        cuidado -> RallyOscuro.aviso
        activa -> ColoresGuiado.Laton
        else -> ColoresGuiado.Texto
    }

/** Texto visible en mayúsculas: «DERECHA 4 LARGA · 50», «HORQUILLA IZQUIERDA», «CERRADA» (simplificada) o «PUENTE». */
internal fun textoNota(
    recursos: Resources,
    elemento: ElementoHoja,
    simplificada: Boolean,
): String =
    when (elemento) {
        is ElementoHoja.DeAviso -> recursos.getString(nombreAviso(elemento.aviso.tipo))
        is ElementoHoja.DeNota -> textoCurva(recursos, elemento, simplificada)
    }

private fun textoCurva(
    recursos: Resources,
    elemento: ElementoHoja.DeNota,
    simplificada: Boolean,
): String {
    val nota = elemento.nota
    val izquierda = nota.direccion == Direccion.IZQUIERDA
    val base =
        if (nota.grado == null) {
            recursos.getString(if (izquierda) R.string.notas_horquilla_izquierda else R.string.notas_horquilla_derecha)
        } else {
            val direccion = recursos.getString(if (izquierda) R.string.notas_izquierda else R.string.notas_derecha)
            val grado =
                if (simplificada) recursos.getString(nombreSimplificado(nota.simplificada())) else "${nota.grado}"
            recursos.getString(R.string.notas_giro, direccion, grado)
        }
    val modificadores = nota.modificadores.sorted().joinToString("") { " " + recursos.getString(nombreModificador(it)) }
    val siguiente = nota.distanciaSiguienteM?.let { " " + recursos.getString(R.string.notas_distancia_siguiente, it) }
    return base + modificadores + siguiente.orEmpty()
}

/** Lo que lee TalkBack: la misma frase que canta la voz y a cuántos metros queda (redondeado a 10). */
internal fun descripcionNota(
    recursos: Resources,
    visible: NotaVisible,
    simplificada: Boolean,
): String {
    val hablada =
        when (val elemento = visible.elemento) {
            is ElementoHoja.DeAviso -> {
                elemento.aviso.tipo.frase
            }

            is ElementoHoja.DeNota -> {
                val previos = elemento.avisosUnidos.filterNot { it.tipo == TipoAviso.CUIDADO && elemento.nota.cuidado }
                (previos.map { it.tipo.frase } + FrasesNota.frase(elemento.nota, simplificada)).joinToString(", ")
            }
        }
    val metros = (visible.distanciaM.coerceAtLeast(0.0) / PASO_DISTANCIA_M).roundToInt() * PASO_DISTANCIA_M
    return recursos.getString(R.string.notas_descripcion, hablada, metros)
}
