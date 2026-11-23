package app.trombito.rallycopilot.ui.comun

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.trombito.rallycopilot.ui.theme.Movimiento

/** Fracción del ancho que se desliza: poco recorrido, para que se lea como un cambio de página y no como un barrido. */
private const val FRACCION_DESLIZ = 4

/**
 * Transición única de navegación del sistema WRC: la pantalla nueva entra deslizando desde la derecha y fundiéndose
 * (300 ms) y la anterior sale un poco hacia la izquierda; al volver, al revés. Son funciones sueltas para enchufarlas
 * a `NavHost` (`enterTransition`, `exitTransition`, `popEnterTransition`, `popExitTransition`) cuando cada pantalla
 * migre. Con [reducirMovimiento] no hay desplazamiento ni fundido: el cambio es inmediato.
 */
object TransicionesWrc {
    /** Entra la pantalla nueva (ir adelante). */
    fun entrada(reducirMovimiento: Boolean): EnterTransition =
        if (reducirMovimiento) {
            EnterTransition.None
        } else {
            slideInHorizontally(tween(Movimiento.MEDIA)) { it / FRACCION_DESLIZ } + fadeIn(tween(Movimiento.MEDIA))
        }

    /** Sale la pantalla anterior al ir adelante. */
    fun salida(reducirMovimiento: Boolean): ExitTransition =
        if (reducirMovimiento) {
            ExitTransition.None
        } else {
            slideOutHorizontally(tween(Movimiento.MEDIA)) { -it / FRACCION_DESLIZ } + fadeOut(tween(Movimiento.MEDIA))
        }

    /** Vuelve la pantalla anterior (ir atrás). */
    fun entradaAtras(reducirMovimiento: Boolean): EnterTransition =
        if (reducirMovimiento) {
            EnterTransition.None
        } else {
            slideInHorizontally(tween(Movimiento.MEDIA)) { -it / FRACCION_DESLIZ } + fadeIn(tween(Movimiento.MEDIA))
        }

    /** Sale la pantalla actual al ir atrás. */
    fun salidaAtras(reducirMovimiento: Boolean): ExitTransition =
        if (reducirMovimiento) {
            ExitTransition.None
        } else {
            slideOutHorizontally(tween(Movimiento.MEDIA)) { it / FRACCION_DESLIZ } + fadeOut(tween(Movimiento.MEDIA))
        }
}

/**
 * Si el usuario ha apagado las animaciones del sistema (escala de animación a 0 en las opciones de desarrollo o
 * «Quitar animaciones» en accesibilidad). Se lee una vez por composición: cambiarlo exige volver a la app.
 */
fun reducirMovimiento(contexto: Context): Boolean =
    Settings.Global.getFloat(contexto.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** [reducirMovimiento] para composables. */
@Composable
fun rememberReducirMovimiento(): Boolean {
    val contexto = LocalContext.current
    return remember(contexto) { reducirMovimiento(contexto) }
}
