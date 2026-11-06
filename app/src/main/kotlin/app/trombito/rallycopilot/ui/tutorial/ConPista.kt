package app.trombito.rallycopilot.ui.tutorial

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.roundToIntRect

/** Cómo se enseña el globo: por defecto en una ventana aparte ([PopupDePista]); las pruebas lo dibujan en línea. */
internal val LocalPresentadorPista =
    staticCompositionLocalOf<@Composable (IntRect, String, LadoPista, () -> Unit) -> Unit> {
        { ancla, texto, lado, onEntendido -> PopupDePista(ancla, texto, lado, onEntendido) }
    }

/** Milisegundos que la pantalla espera antes de enseñar su pista (que se vea primero lo que señala). */
const val RETARDO_PISTA_MS = 700L

/**
 * Envuelve el elemento que señala la pista. Si la [pista] aún no se ha entendido y [activa], tras un instante sale un
 * [PistaContexto] (en el [Pista.lado] que le toca) anclado al elemento, en una ventana aparte: no cambia el diseño
 * ni tapa los toques de alrededor.
 * «Entendido» la marca como vista para siempre; cada pantalla enseña la suya una sola vez. Sin elemento (la lista
 * vacía, la fila que no existe) no hay pista y sigue pendiente.
 */
@Composable
fun ConPista(
    pista: Pista,
    texto: String,
    modifier: Modifier = Modifier,
    activa: Boolean = true,
    contenido: @Composable () -> Unit,
) {
    val estado = LocalPistas.current
    val pendiente = activa && estado.pendiente(pista)
    var listo by remember { mutableStateOf(false) }
    var ancla by remember { mutableStateOf<IntRect?>(null) }
    LaunchedEffect(pendiente) {
        listo = false
        if (pendiente) {
            esperarFotogramas(RETARDO_PISTA_MS)
            listo = true
        }
    }
    val medir =
        if (pendiente) {
            Modifier.onGloballyPositioned { ancla = it.boundsInWindow().roundToIntRect() }
        } else {
            Modifier
        }
    Box(modifier.then(medir)) {
        contenido()
        val sitio = ancla
        if (pendiente && listo && sitio != null) {
            LocalPresentadorPista.current(sitio, texto, pista.lado) { estado.entendida(pista) }
        }
    }
}

/** Espera [ms] contando fotogramas, no con el reloj de las corrutinas: vale igual en el móvil y en las pruebas. */
private suspend fun esperarFotogramas(ms: Long) {
    val inicio = withFrameMillis { it }
    while (withFrameMillis { it } - inicio < ms) Unit
}
