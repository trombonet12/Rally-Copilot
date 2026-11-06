package app.trombito.rallycopilot.ui.comun

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Medidas
import app.trombito.rallycopilot.ui.theme.Movimiento
import app.trombito.rallycopilot.ui.theme.Radios

/** Etiqueta de prueba del indicador de carga. */
const val ETIQUETA_CARGANDO = "cargando-wrc"

/** Etiqueta de prueba de la lista de esqueletos. */
const val ETIQUETA_ESQUELETO = "esqueleto-wrc"

private const val OPACIDAD_ESQUELETO_MIN = 0.35f
private const val OPACIDAD_ESQUELETO_MAX = 0.8f
private val ALTO_FILA_ESQUELETO = 72.dp
private val GROSOR_INDICADOR = 3.dp

/** Carga del sistema WRC: indicador circular ámbar de 32 dp con su [texto] debajo. */
@Composable
fun CargandoWrc(
    texto: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(Espacio.xl)
                .testTag(ETIQUETA_CARGANDO)
                .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(Espacio.m, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(Medidas.indicadorCarga),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = GROSOR_INDICADOR,
        )
        Text(
            text = texto,
            style = Ayuda,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Bloque gris que late suavemente: la silueta de lo que está por cargar. */
@Composable
fun EsqueletoWrc(
    modifier: Modifier = Modifier,
    forma: Shape = Radios.formaTarjeta,
    alto: Dp = ALTO_FILA_ESQUELETO,
) {
    val transicion = rememberInfiniteTransition(label = "esqueleto")
    val opacidad by transicion.animateFloat(
        initialValue = OPACIDAD_ESQUELETO_MIN,
        targetValue = OPACIDAD_ESQUELETO_MAX,
        animationSpec = infiniteRepeatable(tween(Movimiento.ESQUELETO), RepeatMode.Reverse),
        label = "opacidad-esqueleto",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(alto)
            .alpha(opacidad)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, forma)
            .clearAndSetSemantics {},
    )
}

/** Lista de [filas] esqueletos, separados 12 dp, para pantallas de listas mientras carga. */
@Composable
fun ListaEsqueletoWrc(
    modifier: Modifier = Modifier,
    filas: Int = 4,
) {
    val descripcion = stringResource(R.string.wrc_cargando_esqueleto)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(Espacio.l)
                .testTag(ETIQUETA_ESQUELETO)
                .semantics { contentDescription = descripcion },
        verticalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        repeat(filas) { EsqueletoWrc() }
    }
}
