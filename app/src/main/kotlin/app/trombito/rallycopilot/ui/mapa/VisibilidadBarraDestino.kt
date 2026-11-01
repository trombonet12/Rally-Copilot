package app.trombito.rallycopilot.ui.mapa

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Segundos sin tocar el mapa tras los que la barra «¿A dónde?» se vuelve a esconder si sigue habiendo notas. */
internal const val RETARDO_OCULTAR_BARRA_MS = 8_000L

/**
 * La barra «¿A dónde?» no puede esconderse mientras el usuario la usa: el campo tiene el foco, hay texto escrito,
 * se ven resultados de la búsqueda o la vista previa de rutas.
 */
internal fun barraDestinoRetenida(
    enfocada: Boolean,
    hayTexto: Boolean,
    hayBusqueda: Boolean,
    hayVistaPrevia: Boolean,
): Boolean = enfocada || hayTexto || hayBusqueda || hayVistaPrevia

/**
 * Visibilidad de la barra en modo libre: sin notas en la franja se ve como siempre; con notas se esconde (la franja
 * ocupa su sitio) salvo que el usuario la haya [reclamada] tocando el mapa o esté [retenida] por su uso.
 */
internal fun barraDestinoVisible(
    hayNotas: Boolean,
    reclamada: Boolean,
    retenida: Boolean,
): Boolean = !hayNotas || reclamada || retenida

/** Si hay que contar los [RETARDO_OCULTAR_BARRA_MS] para volver a esconder la barra. */
internal fun barraDestinoCuentaAtras(
    hayNotas: Boolean,
    reclamada: Boolean,
    retenida: Boolean,
): Boolean = hayNotas && reclamada && !retenida

/** Estado de la barra «¿A dónde?» en modo libre: si el usuario la ha pedido con un toque, el foco y los toques. */
@Stable
internal class EstadoBarraDestino {
    var reclamada by mutableStateOf(false)
        private set
    var enfocada by mutableStateOf(false)

    /** Cuenta de toques; reinicia la cuenta atrás de [RETARDO_OCULTAR_BARRA_MS]. */
    var toques by mutableIntStateOf(0)
        private set

    fun tocar() {
        reclamada = true
        toques++
    }

    fun ocultar() {
        reclamada = false
    }
}

/** Esconde la barra tras [RETARDO_OCULTAR_BARRA_MS] sin toques mientras [cuentaAtras] sea cierta. */
@Composable
internal fun OcultarBarraTrasInactividad(
    estado: EstadoBarraDestino,
    cuentaAtras: Boolean,
) {
    LaunchedEffect(cuentaAtras, estado.toques) {
        if (cuentaAtras) {
            // En el hilo principal «de verdad» (no el despachador de Compose): así lo gobierna el reloj de los tests.
            withContext(Dispatchers.Main) { delay(RETARDO_OCULTAR_BARRA_MS) }
            estado.ocultar()
        }
    }
}

/** Avisa de cada dedo que baja sobre este contenido (incluido el mapa) sin consumir el gesto. */
internal fun Modifier.alTocar(
    clave: Any,
    onToque: () -> Unit,
): Modifier =
    pointerInput(clave) {
        awaitPointerEventScope {
            while (true) {
                val evento = awaitPointerEvent(PointerEventPass.Initial)
                if (evento.changes.any { it.changedToDown() }) onToque()
            }
        }
    }
