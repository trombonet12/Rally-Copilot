package app.trombito.rallycopilot.ui.comun

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/** Mantiene la pantalla encendida (FLAG_KEEP_SCREEN_ON) mientras este composable está en la composición. */
@Composable
fun MantenerPantallaEncendida() {
    val vista = LocalView.current
    DisposableEffect(vista) {
        vista.keepScreenOn = true
        onDispose { vista.keepScreenOn = false }
    }
}
