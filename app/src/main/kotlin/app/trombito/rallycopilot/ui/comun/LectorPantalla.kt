package app.trombito.rallycopilot.ui.comun

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Si hay un lector de pantalla (TalkBack) explorando al tacto. Con él activo las regiones vivas de avisos que el
 * copiloto ya dice por voz (GPS, exceso de velocidad) se callan para que no hablen dos voces a la vez.
 */
@Composable
fun rememberLectorPantallaActivo(): Boolean {
    val contexto = LocalContext.current
    val gestor =
        remember(contexto) { contexto.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager }
    var activo by remember(gestor) { mutableStateOf(gestor?.isTouchExplorationEnabled == true) }
    DisposableEffect(gestor) {
        val oyente = AccessibilityManager.TouchExplorationStateChangeListener { activo = it }
        gestor?.addTouchExplorationStateChangeListener(oyente)
        onDispose { gestor?.removeTouchExplorationStateChangeListener(oyente) }
    }
    return activo
}
