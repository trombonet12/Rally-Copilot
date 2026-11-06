package app.trombito.rallycopilot.ui.tutorial

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.comun.VarianteTarjeta
import app.trombito.rallycopilot.ui.theme.AmbarSobreCarbon
import app.trombito.rallycopilot.ui.theme.CarbonOpaco
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Medidas
import app.trombito.rallycopilot.ui.theme.Radios

/** Etiquetas de prueba del globo y de su botón. */
const val ETIQUETA_PISTA = "pista-contexto"
const val ETIQUETA_PISTA_ENTENDIDO = "pista-contexto-entendido"

private val ANCHO_MAXIMO_GLOBO = 280.dp
internal val MARGEN_VENTANA = 12.dp
internal val LARGO_FLECHA = 10.dp
private val ANCHO_FLECHA = 20.dp
private const val ESCALA_ENTRADA = 0.85f

/**
 * Globo de pista en contexto: texto corto, una flecha ámbar que señala un elemento y «Entendido». Sobre carbón con
 * borde ámbar (el rojo no se usa). [lado] es dónde está el globo respecto al elemento señalado y [flecha] el punto
 * (px desde el borde inicial del globo) al que apunta; ver [posicionarPista].
 */
@Composable
fun PistaContexto(
    texto: String,
    lado: LadoPista,
    flecha: Int,
    onEntendido: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titulo = stringResource(R.string.pista_descripcion)
    val entrada = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(visibleState = entrada, enter = fadeIn() + scaleIn(initialScale = ESCALA_ENTRADA)) {
        Box(
            modifier
                .width(anchoGlobo())
                .conFlecha(lado, flecha)
                .semantics {
                    paneTitle = titulo
                    liveRegion = LiveRegionMode.Polite
                }.testTag(ETIQUETA_PISTA),
        ) {
            TarjetaWrc(
                modifier = Modifier.background(CarbonOpaco, Radios.formaTarjeta),
                variante = VarianteTarjeta.MAPA,
                activa = true,
                relleno = Espacio.m,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                    Text(text = texto, style = MaterialTheme.typography.bodyMedium)
                    TextButton(
                        onClick = onEntendido,
                        modifier =
                            Modifier
                                .align(Alignment.End)
                                .heightIn(min = Medidas.zonaTactil)
                                .testTag(ETIQUETA_PISTA_ENTENDIDO),
                    ) {
                        Text(stringResource(R.string.pista_entendido), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** El globo mide hasta 280 dp (menos que la ventana con sus márgenes). */
@Composable
private fun anchoGlobo(): Dp {
    val densidad = LocalDensity.current
    val ventana =
        with(densidad) {
            LocalWindowInfo.current.containerSize.width
                .toDp()
        }
    return minOf(ANCHO_MAXIMO_GLOBO, ventana - MARGEN_VENTANA * 2)
}

/** Reserva el hueco de la flecha en el lado del elemento y la dibuja apuntando al punto [flecha] del borde. */
private fun Modifier.conFlecha(
    lado: LadoPista,
    flecha: Int,
): Modifier =
    this
        // La flecha se dibuja antes del relleno: así ocupa todo el globo y no solo el interior.
        .drawBehind {
            val punto = flecha.toFloat()
            val largo = LARGO_FLECHA.toPx()
            val medio = ANCHO_FLECHA.toPx() / 2
            val camino = Path()
            // El lado es dónde está el globo respecto al elemento: la punta mira hacia el elemento.
            when (lado) {
                LadoPista.ABAJO -> {
                    camino.triangulo(Offset(punto - medio, largo), Offset(punto + medio, largo), Offset(punto, 0f))
                }

                LadoPista.ARRIBA -> {
                    val base = size.height - largo
                    camino.triangulo(
                        Offset(punto - medio, base),
                        Offset(punto + medio, base),
                        Offset(punto, size.height),
                    )
                }

                LadoPista.DERECHA -> {
                    camino.triangulo(Offset(largo, punto - medio), Offset(largo, punto + medio), Offset(0f, punto))
                }

                LadoPista.IZQUIERDA -> {
                    val base = size.width - largo
                    camino.triangulo(
                        Offset(base, punto - medio),
                        Offset(base, punto + medio),
                        Offset(size.width, punto),
                    )
                }
            }
            drawPath(camino, AmbarSobreCarbon)
        }.padding(
            top = if (lado == LadoPista.ABAJO) LARGO_FLECHA else 0.dp,
            bottom = if (lado == LadoPista.ARRIBA) LARGO_FLECHA else 0.dp,
            start = if (lado == LadoPista.DERECHA) LARGO_FLECHA else 0.dp,
            end = if (lado == LadoPista.IZQUIERDA) LARGO_FLECHA else 0.dp,
        )

private fun Path.triangulo(
    a: Offset,
    b: Offset,
    c: Offset,
) {
    moveTo(a.x, a.y)
    lineTo(b.x, b.y)
    lineTo(c.x, c.y)
    close()
}

/**
 * El globo de [PistaContexto] en una ventana aparte, anclado al [ancla] (coordenadas de la ventana). No cambia el
 * diseño ni tapa los toques de alrededor. El hueco de la flecha y la posición salen de [posicionarPista].
 */
@Composable
internal fun PopupDePista(
    ancla: IntRect,
    texto: String,
    lado: LadoPista,
    onEntendido: () -> Unit,
) {
    val densidad = LocalDensity.current
    val ventana = LocalWindowInfo.current.containerSize
    val margen = with(densidad) { MARGEN_VENTANA.roundToPx() }
    val hueco = with(densidad) { LARGO_FLECHA.roundToPx() }
    var medida by remember { mutableStateOf(IntSize.Zero) }
    val posicion = posicionarPista(ancla, ventana, medida, lado, hueco, margen)
    val proveedor =
        remember(posicion) {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset = posicion.origen
            }
        }
    Popup(popupPositionProvider = proveedor, properties = PopupProperties(focusable = false)) {
        PistaContexto(
            texto = texto,
            lado = posicion.lado,
            flecha = posicion.flecha,
            onEntendido = onEntendido,
            modifier = Modifier.onSizeChanged { medida = it },
        )
    }
}
