package app.trombito.rallycopilot.ui.navegacion.notas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.NotaVisible
import app.trombito.rallycopilot.ui.navegacion.guiado.ColoresGuiado
import app.trombito.rallycopilot.ui.theme.RallyOscuro

const val ETIQUETA_NOTA_ENORME = "notas-enorme"
const val ETIQUETA_NOTA_ENORME_DISTANCIA = "notas-enorme-distancia"

private val GROSOR_BORDE = 3.dp
private val RELLENO = 16.dp

/**
 * La nota actual del modo de notas grandes (roadbook): el icono de la curva enorme con sus avisos unidos, el texto
 * («DERECHA 4 LARGA · 50», con el grado y los modificadores) y la distancia en cifras gigantes. Latón si toca cantarla
 * ya, ámbar si pide precaución. Con [horizontal] el icono y la distancia van en una fila bajo el texto.
 */
@Composable
fun NotaEnorme(
    visible: NotaVisible,
    simplificadas: Boolean,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false,
) {
    val recursos = LocalContext.current.resources
    val elemento = visible.elemento
    val color = colorNota(esCuidado(elemento), visible.activa)
    val borde = if (visible.activa) ColoresGuiado.Laton else GRIS_BORDE
    val fondo = if (visible.activa) FONDO_ACTIVA else ColoresGuiado.Carbon
    val descripcion = descripcionNota(recursos, visible, simplificadas)
    val ahora = stringResource(R.string.notas_ahora)
    val medidas = if (horizontal) MEDIDAS_ENORME_APAISADA else MEDIDAS_ENORME
    val texto = textoNota(recursos, elemento, simplificadas)
    Column(
        modifier =
            modifier
                .testTag(ETIQUETA_NOTA_ENORME)
                .background(fondo, medidas.forma)
                .border(GROSOR_BORDE, borde, medidas.forma)
                .padding(RELLENO)
                .semantics(mergeDescendants = true) {
                    contentDescription = descripcion
                    if (visible.activa) stateDescription = ahora
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        val icono = @Composable { IconoEnorme(elemento, simplificadas, color, medidas) }
        val distancia = @Composable { DistanciaEnorme(visible.distanciaM, color, horizontal, esCuidado(elemento)) }
        TextoAjustado(texto, color, medidas, Modifier.fillMaxWidth(), TextAlign.Center)
        if (horizontal) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RELLENO)) {
                icono()
                distancia()
            }
        } else {
            icono()
            distancia()
        }
    }
}

@Composable
private fun IconoEnorme(
    elemento: ElementoHoja,
    simplificadas: Boolean,
    color: Color,
    medidas: Medidas,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconosDeLaNota(elemento, 0, simplificadas, color, medidas)
    }
}

/** «120 m» en cifras gigantes y, si pide precaución, el aviso de cuidado al lado. */
@Composable
private fun DistanciaEnorme(
    distanciaM: Double,
    color: Color,
    horizontal: Boolean,
    cuidado: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.notas_distancia_nota, distanciaRedondeadaM(distanciaM)),
            color = color,
            style =
                MaterialTheme.typography.displayLarge.copy(
                    fontSize = if (horizontal) 52.sp else 104.sp,
                    fontWeight = FontWeight.Black,
                ),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag(ETIQUETA_NOTA_ENORME_DISTANCIA),
        )
        if (cuidado) Icono(R.drawable.ic_nota_cuidado, RallyOscuro.aviso, if (horizontal) 44.dp else 64.dp)
    }
}
