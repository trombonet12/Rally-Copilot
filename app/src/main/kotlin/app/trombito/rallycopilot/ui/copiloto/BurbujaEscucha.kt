package app.trombito.rallycopilot.ui.copiloto

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.escucha.DialogoVoz
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.comun.VarianteTarjeta
import app.trombito.rallycopilot.ui.theme.AmbarSobreCarbon
import app.trombito.rallycopilot.ui.theme.Cuerpo
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.EtiquetaIcono
import app.trombito.rallycopilot.ui.theme.Movimiento
import app.trombito.rallycopilot.ui.theme.TextoSecundarioSobreCarbon
import app.trombito.rallycopilot.ui.theme.TextoSobreCarbon
import app.trombito.rallycopilot.ui.tramos.esperarEnPrincipal

const val ETIQUETA_BURBUJA_ESCUCHA = "copiloto-burbuja-escucha"
const val ETIQUETA_BURBUJA_TEXTO = "copiloto-burbuja-texto"
const val ETIQUETA_BURBUJA_RESPUESTA = "copiloto-burbuja-respuesta"

/** Cuánto sigue a la vista la burbuja tras acabar la escucha. */
const val RETARDO_OCULTAR_BURBUJA_MS = 3_000L

private val LADO_MICRO = 40.dp
private val ICONO_MICRO = 22.dp
private const val ONDAS_MS = 1_100

/**
 * La burbuja de escucha sobre la hoja inferior del mapa: aparece al oír «Copiloto» o al tocar el micrófono
 * ([escuchando]) con el micrófono latiendo y «Escuchando…»; al resolverse la orden enseña lo que se oyó y la
 * respuesta del copiloto ([dialogo]) y se esconde sola [RETARDO_OCULTAR_BURBUJA_MS] después de acabar.
 */
@Composable
fun BurbujaEscucha(
    escuchando: Boolean,
    dialogo: DialogoVoz?,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(escuchando, dialogo?.id) {
        if (escuchando || dialogo != null) visible = true
        if (!escuchando) {
            esperarEnPrincipal(RETARDO_OCULTAR_BURBUJA_MS)
            visible = false
        }
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(Movimiento.CORTA)) + slideInVertically(tween(Movimiento.MEDIA)) { it / 2 },
        exit = fadeOut(tween(Movimiento.CORTA)) + slideOutVertically(tween(Movimiento.CORTA)) { it / 2 },
    ) {
        ContenidoBurbuja(escuchando, dialogo)
    }
}

@Composable
private fun ContenidoBurbuja(
    escuchando: Boolean,
    dialogo: DialogoVoz?,
) {
    val texto = dialogo?.texto?.takeIf { it.isNotBlank() }
    val respuesta = dialogo?.respuesta?.takeIf { it.isNotBlank() }
    TarjetaWrc(
        modifier = Modifier.testTag(ETIQUETA_BURBUJA_ESCUCHA).semantics { liveRegion = LiveRegionMode.Polite },
        variante = VarianteTarjeta.MAPA,
        relleno = Espacio.m,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.m)) {
            MicrofonoConPulso(pulsando = escuchando)
            Column(verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                if (escuchando && texto == null) {
                    Text(stringResource(R.string.burbuja_escuchando), style = EtiquetaIcono, color = AmbarSobreCarbon)
                }
                if (texto != null) {
                    LineaBurbuja(
                        stringResource(R.string.burbuja_oido, texto),
                        TextoSecundarioSobreCarbon,
                        ETIQUETA_BURBUJA_TEXTO,
                    )
                }
                if (respuesta != null) {
                    LineaBurbuja(
                        respuesta.replaceFirstChar { it.uppercase() },
                        TextoSobreCarbon,
                        ETIQUETA_BURBUJA_RESPUESTA,
                    )
                }
                if (!escuchando && texto == null && respuesta == null) {
                    Text(stringResource(R.string.burbuja_sin_oir), style = Cuerpo, color = TextoSecundarioSobreCarbon)
                }
            }
        }
    }
}

@Composable
private fun LineaBurbuja(
    texto: String,
    color: androidx.compose.ui.graphics.Color,
    etiqueta: String,
) {
    Text(
        texto,
        style = Cuerpo,
        color = color,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag(etiqueta),
    )
}

/** El micrófono sobre carbón con una onda ámbar que se expande y se desvanece mientras [pulsando]. */
@Composable
private fun MicrofonoConPulso(pulsando: Boolean) {
    val fase =
        if (pulsando) {
            rememberInfiniteTransition(label = "burbuja-micro")
                .animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(ONDAS_MS), RepeatMode.Restart),
                    label = "burbuja-onda",
                ).value
        } else {
            0f
        }
    Box(Modifier.size(LADO_MICRO), contentAlignment = Alignment.Center) {
        if (pulsando) {
            Canvas(Modifier.size(LADO_MICRO)) {
                drawCircle(
                    AmbarSobreCarbon.copy(alpha = 0.7f * (1f - fase)),
                    size.minDimension / 2 * (0.5f + 0.5f * fase),
                    Offset(size.width / 2, size.height / 2),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
        Icon(
            Iconos.Microfono,
            contentDescription =
                stringResource(
                    if (pulsando) R.string.microfono_escuchando else R.string.microfono_boton,
                ),
            tint = AmbarSobreCarbon,
            modifier = Modifier.size(ICONO_MICRO),
        )
    }
}
