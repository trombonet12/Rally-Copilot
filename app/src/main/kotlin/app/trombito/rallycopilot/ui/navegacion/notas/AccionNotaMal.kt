package app.trombito.rallycopilot.ui.navegacion.notas

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.escucha.ResultadoNotaMal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * «Esta nota estaba mal» desde el banner de la nota actual (pulsación larga): [anotar] marca la última nota dicha y
 * dice cómo fue, para la confirmación visual breve.
 */
@Stable
class AccionNotaMal(
    val anotar: suspend () -> ResultadoNotaMal,
)

/** La acción que el carrete ofrece con la pulsación larga; sin ella (previsualizaciones, pruebas) no hace nada. */
val LocalAccionNotaMal = staticCompositionLocalOf<AccionNotaMal?> { null }

private const val CONFIRMACION_MS = 1_500L

/**
 * Estado de la pulsación larga sobre la nota actual: [anotar] da un háptico, marca la nota y deja en [mensaje] la
 * confirmación (un recurso de texto) durante [CONFIRMACION_MS]; sin acción disponible no hace nada.
 */
@Stable
class EstadoNotaMal internal constructor(
    private val accion: AccionNotaMal?,
    private val alcance: CoroutineScope,
    private val haptico: HapticFeedback,
) {
    private var enCurso = false

    /** La confirmación que se enseña en lugar de la nota (null = nada que enseñar). */
    var mensaje: Int? by mutableStateOf(null)
        private set

    val disponible: Boolean get() = accion != null

    /** Anota la nota; devuelve false si no hay acción (para la acción personalizada de accesibilidad). */
    fun anotar(): Boolean {
        val pedida = accion
        if (pedida != null && !enCurso) {
            enCurso = true
            haptico.performHapticFeedback(HapticFeedbackType.LongPress)
            alcance.launch {
                val resultado = pedida.anotar()
                mensaje =
                    if (resultado == ResultadoNotaMal.ANOTADA) {
                        R.string.notas_mal_anotada
                    } else {
                        R.string.notas_mal_sin_nota
                    }
                delay(CONFIRMACION_MS)
                mensaje = null
                enCurso = false
            }
        }
        return pedida != null
    }
}

@Composable
internal fun rememberNotaMal(): EstadoNotaMal {
    val accion = LocalAccionNotaMal.current
    val alcance = rememberCoroutineScope()
    val haptico = LocalHapticFeedback.current
    return remember(accion, alcance, haptico) { EstadoNotaMal(accion, alcance, haptico) }
}

/** La pulsación larga anota la nota (si hay acción disponible). */
internal fun Modifier.anotarNotaMalConPulsacionLarga(estado: EstadoNotaMal): Modifier =
    if (estado.disponible) pointerInput(estado) { detectTapGestures(onLongPress = { estado.anotar() }) } else this
