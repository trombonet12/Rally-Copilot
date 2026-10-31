package app.trombito.rallycopilot.ui.navegacion

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.LADO_TACTIL
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_SEGUIR = "nav-seguir"
const val ETIQUETA_SEGUIR_BOTON = "nav-seguir-boton"
const val ETIQUETA_SEGUIR_ESTADO = "nav-seguir-estado"
const val ETIQUETA_SEGUIR_DESCARTAR = "nav-seguir-descartar"

private const val OPACIDAD_SOBRE_MAPA = 0.92f
private val RADIO = 28.dp

/**
 * «Seguir a <destino>» bajo la barra «¿A dónde?»: el guiado que quedó a medias al morir el proceso. Un toque recalcula
 * la ruta desde donde estés y empieza; la X lo descarta. Mientras calcula (o espera la ubicación) no admite otro toque.
 */
@Composable
fun TarjetaSeguir(
    oferta: OfertaSeguir,
    onSeguir: () -> Unit,
    onDescartar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = MaterialTheme.colorScheme
    val ocupada = oferta.fase == FaseSeguir.CALCULANDO || oferta.fase == FaseSeguir.SIN_POSICION
    Surface(
        shape = RoundedCornerShape(RADIO),
        color = colores.surfaceContainerHigh.copy(OPACIDAD_SOBRE_MAPA),
        modifier = modifier.fillMaxWidth().testTag(ETIQUETA_SEGUIR),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = LADO_TACTIL + Espacio.s)
                        .testTag(ETIQUETA_SEGUIR_BOTON)
                        .clickable(enabled = !ocupada, role = Role.Button, onClick = onSeguir)
                        .padding(start = Espacio.m, top = Espacio.xs, bottom = Espacio.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Espacio.m),
            ) {
                Icon(Iconos.Empezar, contentDescription = null, tint = colores.primary, modifier = Modifier.size(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.seguir_a, oferta.destino.nombre),
                        style =
                            MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                fontStyle = FontStyle.Italic,
                            ),
                        color = colores.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    textoEstado(oferta.fase)?.let { (texto, error) ->
                        Text(
                            text = texto,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (error) colores.error else colores.onSurfaceVariant,
                            modifier =
                                Modifier.testTag(ETIQUETA_SEGUIR_ESTADO).semantics {
                                    liveRegion = LiveRegionMode.Polite
                                },
                        )
                    }
                }
            }
            IconButton(
                onClick = onDescartar,
                modifier = Modifier.size(LADO_TACTIL).testTag(ETIQUETA_SEGUIR_DESCARTAR),
            ) {
                Icon(
                    imageVector = Iconos.Cerrar,
                    contentDescription = stringResource(R.string.seguir_descartar),
                    tint = colores.onSurface,
                )
            }
        }
    }
}

/** Línea de estado bajo el destino y si es un error; null en reposo. */
@Composable
private fun textoEstado(fase: FaseSeguir): Pair<String, Boolean>? =
    when (fase) {
        FaseSeguir.LISTA -> null
        FaseSeguir.CALCULANDO -> stringResource(R.string.seguir_calculando) to false
        FaseSeguir.SIN_POSICION -> stringResource(R.string.seguir_sin_posicion) to false
        FaseSeguir.ERROR -> stringResource(R.string.seguir_error) to true
    }
